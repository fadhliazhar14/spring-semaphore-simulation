package com.fadhli.simulation.manager;

import org.redisson.api.RAtomicLong;
import org.redisson.api.RBatch;
import org.redisson.api.RBucket;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Menyimpan identitas, konfigurasi, dan metrik simulasi di Redis, bukan di heap satu instance.
 *
 * <p>Sebelumnya seluruh metrik berupa {@code AtomicInteger} milik bean singleton, sehingga saat
 * aplikasi dijalankan lebih dari satu instance setiap instance melaporkan angkanya sendiri dan
 * dasbor menampilkan hasil yang berbeda-beda tergantung instance mana yang melayani permintaan.
 */
@Component
public class SimulationStateStore {

    private static final Logger log = LoggerFactory.getLogger(SimulationStateStore.class);

    /** Umur key simulasi. Simulasi lama dibersihkan Redis sendiri tanpa perlu penghapusan manual. */
    static final Duration SIM_TTL = Duration.ofHours(24);

    public static final String METRIC_TOTAL = "total";
    public static final String METRIC_SUCCESS = "success";
    public static final String METRIC_OUT_OF_STOCK = "outOfStock";
    /** Request yang ditolak karena tidak ada slot bebas saat itu juga. */
    public static final String METRIC_REJECTED = "rejected";
    /** Pembayaran ditolak; tiket yang sempat ditahan sudah dikembalikan. */
    public static final String METRIC_PAYMENT_FAILED = "paymentFailed";
    /** Sesi yang tenggatnya lewat sebelum sempat diselesaikan pemiliknya. */
    public static final String METRIC_ABANDONED = "abandoned";
    /**
     * Pembeli yang kehabisan jatah percobaan tanpa pernah sekali pun kebagian slot. Tanpa angka
     * ini, papan tidak bisa menjawab pertanyaan yang paling wajar ditanyakan: dari sekian pembeli
     * yang berangkat, ke mana perginya mereka yang tidak muncul di mana-mana.
     */
    public static final String METRIC_GAVE_UP = "gaveUp";
    /**
     * Jumlah tiket yang ditambahkan di tengah simulasi. Disimpan terpisah dan bukan dengan
     * mengubah {@code totalTickets} di konfigurasi, karena konfigurasi sengaja tidak pernah
     * berubah sepanjang umur sebuah simId sehingga aman di-cache tiap instance tanpa invalidasi.
     */
    public static final String METRIC_RESTOCKED = "restocked";

    private final RedissonClient redisson;

    /** Konfigurasi tetap sepanjang umur simId, jadi aman di-cache tanpa invalidasi. */
    private final Map<String, SimulationConfig> configCache = new ConcurrentHashMap<>();

    public SimulationStateStore(RedissonClient redisson) {
        this.redisson = redisson;
    }

    /**
     * Membuat simulasi baru beserta seluruh key-nya. Tidak ada key milik simulasi sebelumnya yang
     * disentuh, sehingga simulasi yang masih berjalan di instance lain tidak ikut rusak.
     */
    public SimulationConfig createSimulation(Long eventId, int totalTickets, int permits,
                                             int thinkTimeMs, int paymentSuccessPercent) {
        String simId = UUID.randomUUID().toString().substring(0, 8);
        SimulationConfig cfg = new SimulationConfig(
                simId, eventId, permits, totalTickets,
                Math.max(0, thinkTimeMs), Math.min(100, Math.max(0, paymentSuccessPercent)));

        SimulationKeys keys = new SimulationKeys(simId);
        RMap<String, String> map = redisson.getMap(keys.config(), StringCodec.INSTANCE);
        map.putAll(Map.of(
                "eventId", String.valueOf(eventId),
                "permits", String.valueOf(cfg.permits()),
                "totalTickets", String.valueOf(cfg.totalTickets()),
                "thinkTimeMs", String.valueOf(cfg.thinkTimeMs()),
                "paymentSuccessPercent", String.valueOf(cfg.paymentSuccessPercent())));
        map.expire(SIM_TTL);

        RBucket<String> current = redisson.getBucket(SimulationKeys.CURRENT, StringCodec.INSTANCE);
        current.set(simId, SIM_TTL);

        configCache.put(simId, cfg);
        log.info("Created simulation {} (event={}, permits={}, stock={}, thinkTime={}ms, pay={}%)",
                simId, eventId, permits, totalTickets,
                cfg.thinkTimeMs(), cfg.paymentSuccessPercent());
        return cfg;
    }

    /** simId yang sedang aktif, atau {@code null} bila belum ada simulasi yang diinisialisasi. */
    public String currentSimId() {
        return redisson.<String>getBucket(SimulationKeys.CURRENT, StringCodec.INSTANCE).get();
    }

    /**
     * Konfigurasi sebuah simulasi. Instance yang baru menyala di tengah simulasi berjalan akan
     * memuatnya dari Redis, bukan memakai nilai default bawaan kodenya sendiri.
     */
    public SimulationConfig config(String simId) {
        if (simId == null) {
            return null;
        }
        SimulationConfig cached = configCache.get(simId);
        if (cached != null) {
            return cached;
        }
        Map<String, String> raw = redisson.<String, String>getMap(
                new SimulationKeys(simId).config(), StringCodec.INSTANCE).readAllMap();
        if (raw.isEmpty()) {
            return null;
        }
        SimulationConfig cfg = new SimulationConfig(
                simId,
                Long.parseLong(raw.get("eventId")),
                Integer.parseInt(raw.get("permits")),
                Integer.parseInt(raw.get("totalTickets")),
                Integer.parseInt(raw.getOrDefault("thinkTimeMs", "300")),
                Integer.parseInt(raw.getOrDefault("paymentSuccessPercent", "90")));
        configCache.put(simId, cfg);
        return cfg;
    }

    /** Menaikkan satu metrik sejumlah tertentu sekaligus menyegarkan TTL-nya. */
    public void increaseBy(String simId, String metric, long delta) {
        if (simId == null) {
            return;
        }
        String key = new SimulationKeys(simId).metric(metric);
        RBatch batch = redisson.createBatch();
        batch.getAtomicLong(key).addAndGetAsync(delta);
        batch.getAtomicLong(key).expireAsync(SIM_TTL);
        batch.execute();
    }

    public long metric(String simId, String metric) {
        if (simId == null) {
            return 0;
        }
        return redisson.getAtomicLong(new SimulationKeys(simId).metric(metric)).get();
    }

    /** Menaikkan satu metrik sekaligus menyegarkan TTL-nya dalam satu perjalanan ke Redis. */
    public void increment(String simId, String metric) {
        if (simId == null) {
            return;
        }
        String key = new SimulationKeys(simId).metric(metric);
        RBatch batch = redisson.createBatch();
        batch.getAtomicLong(key).incrementAndGetAsync();
        batch.getAtomicLong(key).expireAsync(SIM_TTL);
        batch.execute();
    }

    /** Membaca keempat metrik dalam satu perjalanan ke Redis. */
    public Metrics metrics(String simId) {
        if (simId == null) {
            return new Metrics(0, 0, 0, 0, 0, 0, 0);
        }
        SimulationKeys keys = new SimulationKeys(simId);
        RBatch batch = redisson.createBatch();
        batch.getAtomicLong(keys.metric(METRIC_TOTAL)).getAsync();
        batch.getAtomicLong(keys.metric(METRIC_SUCCESS)).getAsync();
        batch.getAtomicLong(keys.metric(METRIC_OUT_OF_STOCK)).getAsync();
        batch.getAtomicLong(keys.metric(METRIC_REJECTED)).getAsync();
        batch.getAtomicLong(keys.metric(METRIC_PAYMENT_FAILED)).getAsync();
        batch.getAtomicLong(keys.metric(METRIC_ABANDONED)).getAsync();
        batch.getAtomicLong(keys.metric(METRIC_GAVE_UP)).getAsync();
        List<?> responses = batch.execute().getResponses();
        return new Metrics(
                intOf(responses.get(0)), intOf(responses.get(1)), intOf(responses.get(2)),
                intOf(responses.get(3)), intOf(responses.get(4)), intOf(responses.get(5)),
                intOf(responses.get(6)));
    }

    private static int intOf(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    public record Metrics(int total, int success, int outOfStock, int rejected,
                          int paymentFailed, int abandoned, int gaveUp) {
    }
}
