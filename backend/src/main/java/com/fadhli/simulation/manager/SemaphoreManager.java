package com.fadhli.simulation.manager;

import org.redisson.api.RAtomicLong;
import org.redisson.api.RBatch;
import org.redisson.api.RDeque;
import org.redisson.api.RMap;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Slot konkurensi, kuota stok, dan sesi pengguna — semuanya di Redis dan semuanya diberi cakupan
 * {@code simId}.
 *
 * <p>Sejak Tahap 2 permit tidak lagi berupa {@code RSemaphore} melainkan antrean token slot
 * bernomor. Secara semantik keduanya setara: antrean berisi N token, mengambil token adalah
 * acquire dan mengembalikannya adalah release, sehingga paling banyak N pemegang pada satu waktu.
 * Bedanya, token punya identitas. Itu memberi dua hal yang tidak bisa diberikan semaphore biasa:
 * papan observasi dapat menampilkan siapa menempati slot nomor berapa, dan setiap kepemilikan
 * punya tenggat waktu (lease) sehingga slot yang pemegangnya mati bisa direbut kembali oleh
 * {@link SlotReaper} alih-alih hilang selamanya.
 *
 * <p>Pengambilan slot tidak pernah memblokir. Siapa yang tidak kebagian ditolak saat itu juga,
 * bukan diparkir sampai ada yang bebas. Simulasi ini memang dimaksudkan sebagai perebutan: menahan
 * request selama beberapa detik sambil mencoba berulang kali adalah antrean juga, hanya antrean
 * yang tidak kelihatan, tidak adil, dan tanpa umpan balik. Ketekunan pengguna dimodelkan sebagai
 * percobaan ulang dari sisi pemanggil.
 *
 * <p>Pengembalian slot dijaga <em>fencing token</em>: pemilik lama yang slotnya sudah direbut
 * reaper akan ditolak saat mencoba melepas. Tanpa penjagaan itu satu slot bisa masuk kembali ke
 * antrean dua kali dan jumlah permit membengkak melebihi batas — persis penyakit yang diobati di
 * Tahap 1, hanya lewat pintu yang berbeda.
 */
@Component
public class SemaphoreManager {

    private static final Logger log = LoggerFactory.getLogger(SemaphoreManager.class);

    /**
     * Umur kepemilikan satu slot. Nilainya sengaja jauh lebih longgar daripada kemungkinan
     * terburuk satu proses bisnis, karena reaper yang merebut slot dari pemilik yang sebenarnya
     * masih hidup lebih merugikan daripada slot yang tertahan beberapa detik lebih lama.
     * Perpanjangan lease di tengah proses belum diperlukan; alur baru dipecah di Tahap 4.
     */
    public static final Duration SLOT_LEASE = Duration.ofSeconds(30);

    /**
     * Mengisi antrean slot, tetapi hanya kalau key-nya memang belum ada. Pemeriksaan dan pengisian
     * harus berada dalam satu skrip supaya dua instance yang menginisialisasi simulasi yang sama
     * tidak menghasilkan 2N token.
     */
    private static final String SCRIPT_INIT_SLOTS = """
            if redis.call('exists', KEYS[1]) == 1 then return 0 end
            for i = 1, tonumber(ARGV[1]) do
              redis.call('rpush', KEYS[1], 'slot-' .. i)
            end
            redis.call('pexpire', KEYS[1], ARGV[2])
            return 1
            """;

    /**
     * Melepas slot hanya bila fencing token-nya masih cocok. Mengembalikan 1 bila berhasil,
     * 0 bila kepemilikannya sudah tidak ada (biasanya sudah direbut reaper), dan -1 bila slotnya
     * sudah berpindah ke pemilik baru sehingga pelepasan ini basi.
     */
    private static final String SCRIPT_RELEASE_SLOT = """
            local owner = redis.call('hget', KEYS[2], ARGV[1])
            if owner == false then return 0 end
            local sep = string.find(owner, '|', 1, true)
            if sep == nil or string.sub(owner, 1, sep - 1) ~= ARGV[2] then return -1 end
            redis.call('hdel', KEYS[2], ARGV[1])
            redis.call('zrem', KEYS[3], ARGV[1])
            redis.call('rpush', KEYS[1], ARGV[1])
            redis.call('pexpire', KEYS[1], ARGV[3])
            return 1
            """;

    /**
     * Merebut kembali satu slot yang lease-nya lewat tenggat. Tenggatnya diperiksa ulang di dalam
     * skrip: kalau pemiliknya sempat melepas sendiri sepersekian detik sebelumnya, entri lease-nya
     * sudah hilang dan skrip ini tidak melakukan apa-apa, jadi slot tidak pernah kembali dua kali.
     */
    private static final String SCRIPT_REAP_SLOT = """
            local deadline = redis.call('zscore', KEYS[3], ARGV[1])
            if deadline == false then return 0 end
            if tonumber(deadline) > tonumber(ARGV[2]) then return 0 end
            redis.call('hdel', KEYS[2], ARGV[1])
            redis.call('zrem', KEYS[3], ARGV[1])
            redis.call('rpush', KEYS[1], ARGV[1])
            redis.call('pexpire', KEYS[1], ARGV[3])
            return 1
            """;

    /** Hasil percobaan melepas slot. */
    public enum ReleaseResult {
        /** Slot kembali ke antrean. */
        RELEASED,
        /** Kepemilikannya sudah tidak ada lagi, kemungkinan besar sudah direbut reaper. */
        ALREADY_REAPED,
        /** Slot sudah dipegang orang lain; pelepasan ditolak agar token tidak kembali dua kali. */
        STALE_TOKEN
    }

    private final RedissonClient redisson;
    private final ApplicationInstance instance;

    public SemaphoreManager(RedissonClient redisson, ApplicationInstance instance) {
        this.redisson = redisson;
        this.instance = instance;
    }

    public void initSemaphore(String simId, int permits) {
        if (permits <= 0) {
            throw new IllegalArgumentException("Permits must be greater than 0");
        }
        SimulationKeys keys = new SimulationKeys(simId);
        long created = script().eval(RScript.Mode.READ_WRITE, SCRIPT_INIT_SLOTS,
                RScript.ReturnType.INTEGER,
                List.of(keys.slots()),
                String.valueOf(permits), ttlMs());
        if (created == 1) {
            log.info("Simulation {} initialized with {} slots", simId, permits);
        } else {
            log.warn("Simulation {} already has a slot queue; init ignored", simId);
        }
    }

    public void initStock(String simId, int stock) {
        RAtomicLong counter = redisson.getAtomicLong(new SimulationKeys(simId).stock());
        counter.set(stock);
        counter.expire(SimulationStateStore.SIM_TTL);
    }

    public SlotLease tryAcquire(String simId, String userId) {
        return tryAcquire(simId, userId, SLOT_LEASE);
    }

    /**
     * Mengambil satu slot bila ada yang bebas saat ini juga, atau {@code null} bila tidak.
     *
     * <p>Tidak ada penantian sama sekali: satu perjalanan ke Redis, dapat atau tidak. Pemanggil
     * yang ditolak bebas mencoba lagi, dan justru percobaan ulang itulah bentuk perebutannya.
     *
     * <p>Pembukuan setelah token terambil sengaja tidak memakai skrip: token sudah keluar dari
     * antrean, jadi pemanggil ini satu-satunya pihak yang memegangnya dan tidak ada yang bisa
     * bersaing menulis entri yang sama.
     */
    public SlotLease tryAcquire(String simId, String userId, Duration lease) {
        SimulationKeys keys = new SimulationKeys(simId);
        String slot = redisson.<String>getDeque(keys.slots(), StringCodec.INSTANCE).poll();
        if (slot == null) {
            return null;
        }

        String fencingToken = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        SlotOwner owner = new SlotOwner(fencingToken, now, instance.id(), userId);

        RBatch batch = redisson.createBatch();
        batch.getMap(keys.slotOwner(), StringCodec.INSTANCE).putAsync(slot, owner.encode());
        batch.getMap(keys.slotOwner(), StringCodec.INSTANCE)
                .expireAsync(SimulationStateStore.SIM_TTL);
        batch.getScoredSortedSet(keys.slotLease(), StringCodec.INSTANCE)
                .addAsync(now + lease.toMillis(), slot);
        batch.getScoredSortedSet(keys.slotLease(), StringCodec.INSTANCE)
                .expireAsync(SimulationStateStore.SIM_TTL);
        batch.execute();

        return new SlotLease(simId, slot, fencingToken);
    }

    public ReleaseResult release(SlotLease lease) {
        if (lease == null) {
            return ReleaseResult.ALREADY_REAPED;
        }
        SimulationKeys keys = new SimulationKeys(lease.simId());
        long result = script().eval(RScript.Mode.READ_WRITE, SCRIPT_RELEASE_SLOT,
                RScript.ReturnType.INTEGER,
                List.of(keys.slots(), keys.slotOwner(), keys.slotLease()),
                lease.slot(), lease.fencingToken(), ttlMs());

        if (result == 1) {
            return ReleaseResult.RELEASED;
        }
        if (result == 0) {
            log.warn("Slot {} of simulation {} was already reclaimed before its owner released it",
                    lease.slot(), lease.simId());
            return ReleaseResult.ALREADY_REAPED;
        }
        log.warn("Rejected stale release of slot {} in simulation {}: it belongs to someone else now",
                lease.slot(), lease.simId());
        return ReleaseResult.STALE_TOKEN;
    }

    /** Merebut satu slot kedaluwarsa. Dipakai {@link SlotReaper}. */
    boolean reclaim(String simId, String slot, long nowEpochMs) {
        SimulationKeys keys = new SimulationKeys(simId);
        long result = script().eval(RScript.Mode.READ_WRITE, SCRIPT_REAP_SLOT,
                RScript.ReturnType.INTEGER,
                List.of(keys.slots(), keys.slotOwner(), keys.slotLease()),
                slot, String.valueOf(nowEpochMs), ttlMs());
        return result == 1;
    }

    /** Slot-slot yang lease-nya sudah lewat tenggat pada {@code nowEpochMs}. */
    List<String> expiredSlots(String simId, long nowEpochMs) {
        return List.copyOf(redisson
                .<String>getScoredSortedSet(new SimulationKeys(simId).slotLease(), StringCodec.INSTANCE)
                .valueRange(Double.NEGATIVE_INFINITY, true, nowEpochMs, true));
    }

    /** Menambah kuota stok di Redis, mengembalikan sisa stok setelah ditambah. */
    public long addStock(String simId, int amount) {
        RAtomicLong counter = redisson.getAtomicLong(new SimulationKeys(simId).stock());
        long remaining = counter.addAndGet(amount);
        counter.expire(SimulationStateStore.SIM_TTL);
        return remaining;
    }

    /** Sisa kuota stok di Redis. Dipakai untuk memastikan kuota tetap sejalan dengan basis data. */
    public long availableStock(String simId) {
        return redisson.getAtomicLong(new SimulationKeys(simId).stock()).get();
    }

    /** Mengurangi kuota stok secara atomik, mengembalikannya bila ternyata sudah habis. */
    public boolean tryReserveStock(String simId) {
        RAtomicLong counter = redisson.getAtomicLong(new SimulationKeys(simId).stock());
        if (counter.decrementAndGet() < 0) {
            counter.incrementAndGet();
            return false;
        }
        return true;
    }

    /**
     * Menulis keadaan sesi beserta tenggatnya.
     *
     * <p>Sesi berupa hash, bukan satu nilai tunggal seperti sebelumnya, karena yang perlu diingat
     * bukan hanya status melainkan juga slot mana yang dipegang dan dengan penanda kepemilikan
     * apa. Tanpa itu, sesi yang terlantar tidak bisa dibereskan siapa pun: tidak ada yang tahu
     * slot mana yang harus ditarik.
     */
    public void saveSession(String simId, PurchaseSession session, Duration ttl) {
        RMap<String, String> map = redisson.getMap(
                new SimulationKeys(simId).session(session.userId()), StringCodec.INSTANCE);
        map.putAll(session.toMap());
        map.expire(ttl);
    }

    public PurchaseSession getSession(String simId, String userId) {
        if (simId == null || userId == null) {
            return null;
        }
        return PurchaseSession.fromMap(redisson.<String, String>getMap(
                new SimulationKeys(simId).session(userId), StringCodec.INSTANCE).readAllMap());
    }

    /** Membaca banyak sesi sekaligus, dipakai papan observasi agar tidak N kali bolak-balik. */
    public Map<String, PurchaseSession> getSessions(String simId, Collection<String> userIds) {
        Map<String, PurchaseSession> sessions = new LinkedHashMap<>();
        if (userIds.isEmpty()) {
            return sessions;
        }
        SimulationKeys keys = new SimulationKeys(simId);
        RBatch batch = redisson.createBatch();
        List<String> ordered = List.copyOf(userIds);
        ordered.forEach(userId ->
                batch.getMap(keys.session(userId), StringCodec.INSTANCE).readAllMapAsync());

        List<?> responses = batch.execute().getResponses();
        for (int i = 0; i < ordered.size() && i < responses.size(); i++) {
            if (responses.get(i) instanceof Map<?, ?> raw) {
                @SuppressWarnings("unchecked")
                PurchaseSession session = PurchaseSession.fromMap((Map<String, String>) raw);
                if (session != null) {
                    sessions.put(ordered.get(i), session);
                }
            }
        }
        return sessions;
    }

    /** Jumlah slot yang sedang menganggur di antrean. */
    public int getAvailablePermits(String simId) {
        return redisson.getDeque(new SimulationKeys(simId).slots(), StringCodec.INSTANCE).size();
    }

    /**
     * Jumlah slot yang sedang dipegang, dihitung dari daftar lease dan bukan dari selisih terhadap
     * jumlah slot total. Selisih hanya benar kalau kedua sisinya konsisten; daftar lease adalah
     * catatan langsung tentang siapa yang benar-benar sedang memegang slot.
     */
    public int getActivePermits(String simId) {
        return redisson.getScoredSortedSet(new SimulationKeys(simId).slotLease(), StringCodec.INSTANCE)
                .size();
    }

    /**
     * Tenggat lease tiap slot yang sedang dipegang, dalam epoch milidetik. Papan observasi
     * memakainya untuk menampilkan sisa waktu sebelum reaper berhak merebut slot.
     */
    public Map<String, Long> getSlotLeaseDeadlines(String simId) {
        Map<String, Long> deadlines = new LinkedHashMap<>();
        redisson.<String>getScoredSortedSet(new SimulationKeys(simId).slotLease(), StringCodec.INSTANCE)
                .entryRange(0, -1)
                .forEach(entry -> deadlines.put(entry.getValue(), entry.getScore().longValue()));
        return deadlines;
    }

    /** Peta slot ke pemiliknya, dipakai papan observasi untuk menggambar isi tiap slot. */
    public Map<String, SlotOwner> getSlotOwners(String simId) {
        Map<String, String> raw = redisson.<String, String>getMap(
                new SimulationKeys(simId).slotOwner(), StringCodec.INSTANCE).readAllMap();
        Map<String, SlotOwner> owners = new LinkedHashMap<>();
        raw.forEach((slot, encoded) -> {
            SlotOwner owner = SlotOwner.decode(encoded);
            if (owner != null) {
                owners.put(slot, owner);
            }
        });
        return owners;
    }

    private RScript script() {
        return redisson.getScript(StringCodec.INSTANCE);
    }

    private static String ttlMs() {
        return String.valueOf(SimulationStateStore.SIM_TTL.toMillis());
    }
}
