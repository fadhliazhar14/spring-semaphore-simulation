package com.fadhli.simulation.service;

import com.fadhli.simulation.manager.PurchaseSession;
import com.fadhli.simulation.manager.SemaphoreManager;
import com.fadhli.simulation.manager.SessionState;
import com.fadhli.simulation.manager.SimulationConfig;
import com.fadhli.simulation.manager.SimulationStateStore;
import com.fadhli.simulation.manager.SlotLease;
import com.fadhli.simulation.manager.SlotReaper;
import com.fadhli.simulation.model.TicketEvent;
import com.fadhli.simulation.repository.TicketEventRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mesin keadaan satu sesi pembelian.
 *
 * <p>Pembelian dipecah menjadi beberapa langkah, dan tiap langkah dipicu panggilan tersendiri.
 * Ini bukan sekadar kosmetik. Kalau seluruh langkah dijalankan dalam satu panggilan, jeda antar
 * langkah hanya bisa ditiru dengan menidurkan thread, dan thread yang tidur tetap memakan sumber
 * daya yang langka. Dengan langkah terpisah, tidak ada apa pun yang tertahan di antara dua
 * langkah selain catatan di Redis.
 *
 * <p>Slot dipegang sepanjang sesi, dari langkah pertama sampai terakhir. Itu pilihan sadar, dan
 * kelemahannya memang dimaksudkan untuk terlihat: begitu waktu berpikir pengguna diperpanjang,
 * slot tertahan lama, penolakan melonjak, padahal yang di dalam sedang tidak mengerjakan apa pun.
 * Persoalan itulah yang di dunia nyata mendorong orang melepas permit sebelum menunggu manusia.
 *
 * <p>Sesi yang ditinggalkan tidak menahan slot selamanya karena tiap sesi punya tenggat, dan
 * {@link com.fadhli.simulation.manager.SlotReaper} membereskan yang lewat tenggat.
 */
@Service
public class SessionService implements SlotReaper.SessionJanitor {

    /** Berapa kali sebuah sesi berpindah langkah sampai selesai. Dipakai menghitung tenggat. */
    private static final int STEPS = 3;

    /**
     * Kelonggaran tenggat sesi terhadap waktu berpikir yang diperkirakan. Reaper yang merebut slot
     * dari sesi yang sebenarnya masih hidup jauh lebih merugikan daripada slot yang tertahan
     * beberapa detik lebih lama, jadi kelonggarannya sengaja besar.
     */
    private static final int LEASE_SAFETY_FACTOR = 4;

    private static final Duration MIN_SESSION_LEASE = Duration.ofSeconds(15);

    /** Umur catatan sesi setelah berakhir, supaya hasil akhirnya sempat terbaca papan observasi. */
    private static final Duration FINISHED_SESSION_TTL = Duration.ofMinutes(5);

    private final TicketEventRepository ticketEventRepository;
    private final SemaphoreManager semaphoreManager;
    private final SimulationStateStore store;
    private final SseService sseService;
    private final SessionService self;

    public SessionService(TicketEventRepository ticketEventRepository,
                          SemaphoreManager semaphoreManager,
                          SimulationStateStore store,
                          SseService sseService,
                          @Lazy SessionService self) {
        this.ticketEventRepository = ticketEventRepository;
        this.semaphoreManager = semaphoreManager;
        this.store = store;
        this.sseService = sseService;
        this.self = (self != null) ? self : this;
    }

    /** Tenggat satu sesi, diturunkan dari waktu berpikir supaya tidak perlu disetel terpisah. */
    public static Duration sessionLease(SimulationConfig cfg) {
        long estimated = (long) cfg.thinkTimeMs() * STEPS * LEASE_SAFETY_FACTOR;
        return Duration.ofMillis(Math.max(MIN_SESSION_LEASE.toMillis(), estimated));
    }

    /**
     * Memulai sesi: merebut satu slot, atau ditolak seketika kalau semuanya terpakai.
     *
     * @return sesi yang baru dibuat, atau {@code null} bila tidak kebagian slot
     */
    public PurchaseSession start(String simId, String userId) {
        SimulationConfig cfg = store.config(simId);
        if (cfg == null) {
            return null;
        }
        store.increment(simId, SimulationStateStore.METRIC_TOTAL);

        SlotLease lease = semaphoreManager.tryAcquire(simId, userId, sessionLease(cfg));
        if (lease == null) {
            store.increment(simId, SimulationStateStore.METRIC_REJECTED);
            return null;
        }

        long now = System.currentTimeMillis();
        PurchaseSession session = new PurchaseSession(
                userId, SessionState.SELECTING, lease.slot(), lease.fencingToken(), now, now);
        semaphoreManager.saveSession(simId, session, sessionLease(cfg));
        return session;
    }

    /**
     * Memajukan sesi satu langkah.
     *
     * @return keadaan sesi setelah langkah ini, atau {@code null} bila sesinya sudah tidak ada
     *         (biasanya karena tenggatnya lewat dan sudah dibereskan reaper)
     */
    public PurchaseSession advance(String simId, String userId) {
        SimulationConfig cfg = store.config(simId);
        PurchaseSession session = semaphoreManager.getSession(simId, userId);
        if (cfg == null || session == null || !session.state().holdingSlot()) {
            return session;
        }

        return switch (session.state()) {
            case SELECTING -> selectTicket(cfg, session);
            case PAYING -> payForTicket(cfg, session);
            case CONFIRMING -> issueTicket(cfg, session);
            default -> session;
        };
    }

    /** Langkah nyata: memesan satu tiket dari kuota di Redis. */
    private PurchaseSession selectTicket(SimulationConfig cfg, PurchaseSession session) {
        if (!semaphoreManager.tryReserveStock(cfg.simId())) {
            store.increment(cfg.simId(), SimulationStateStore.METRIC_OUT_OF_STOCK);
            return finish(cfg, session, SessionState.OUT_OF_STOCK,
                    "User " + session.userId() + " kalah cepat, stok habis");
        }
        return moveTo(cfg, session, SessionState.PAYING);
    }

    /**
     * Satu-satunya langkah yang benar-benar tiruan: tidak ada gerbang pembayaran sungguhan, jadi
     * hasilnya diundi. Akibatnya nyata — pembayaran yang ditolak mengembalikan tiket yang tadi
     * sudah ditahan, sehingga stok tidak bocor.
     */
    private PurchaseSession payForTicket(SimulationConfig cfg, PurchaseSession session) {
        boolean paid = ThreadLocalRandom.current().nextInt(100) < cfg.paymentSuccessPercent();
        if (!paid) {
            semaphoreManager.addStock(cfg.simId(), 1);
            store.increment(cfg.simId(), SimulationStateStore.METRIC_PAYMENT_FAILED);
            return finish(cfg, session, SessionState.PAYMENT_FAILED,
                    "Pembayaran " + session.userId() + " ditolak, tiket dikembalikan");
        }
        return moveTo(cfg, session, SessionState.CONFIRMING);
    }

    /** Langkah nyata: mencatat penjualan di basis data. */
    private PurchaseSession issueTicket(SimulationConfig cfg, PurchaseSession session) {
        if (!self.commitSale(cfg.eventId())) {
            // Kuota Redis sempat meloloskan sesi ini padahal basis data sudah kosong. Kuota itu
            // sudah terpakai, jadi tidak ada yang perlu dikembalikan.
            store.increment(cfg.simId(), SimulationStateStore.METRIC_OUT_OF_STOCK);
            return finish(cfg, session, SessionState.OUT_OF_STOCK,
                    "User " + session.userId() + " gagal, stok basis data habis");
        }
        store.increment(cfg.simId(), SimulationStateStore.METRIC_SUCCESS);
        return finish(cfg, session, SessionState.DONE,
                "User " + session.userId() + " berhasil mendapatkan tiket");
    }

    @Transactional
    public boolean commitSale(Long eventId) {
        return ticketEventRepository.decrementTicketStock(eventId) > 0;
    }

    private PurchaseSession moveTo(SimulationConfig cfg, PurchaseSession session,
                                   SessionState next) {
        PurchaseSession moved = new PurchaseSession(session.userId(), next, session.slot(),
                session.fencingToken(), session.startedAt(), System.currentTimeMillis());
        semaphoreManager.saveSession(cfg.simId(), moved, sessionLease(cfg));
        return moved;
    }

    /** Mengakhiri sesi: slot dilepas lebih dulu, baru keadaan akhirnya dicatat. */
    private PurchaseSession finish(SimulationConfig cfg, PurchaseSession session,
                                   SessionState finalState, String message) {
        SlotLease lease = session.lease(cfg.simId());
        if (lease != null) {
            semaphoreManager.release(lease);
        }
        PurchaseSession finished = new PurchaseSession(session.userId(), finalState, null, null,
                session.startedAt(), System.currentTimeMillis());
        semaphoreManager.saveSession(cfg.simId(), finished, FINISHED_SESSION_TTL);
        sseService.activity(message);
        return finished;
    }

    /**
     * Membereskan sesi yang slotnya sudah direbut reaper. Stok yang sempat ditahan dikembalikan,
     * kalau tidak stok akan bocor sedikit demi sedikit setiap ada pembeli yang kabur di tengah
     * jalan.
     */
    @Override
    public void abandon(String simId, String userId) {
        SimulationConfig cfg = store.config(simId);
        PurchaseSession session = semaphoreManager.getSession(simId, userId);
        if (cfg == null || session == null || !session.state().holdingSlot()) {
            return;
        }
        if (session.state().holdingStock()) {
            semaphoreManager.addStock(simId, 1);
        }
        store.increment(simId, SimulationStateStore.METRIC_ABANDONED);

        PurchaseSession abandoned = new PurchaseSession(userId, SessionState.ABANDONED, null, null,
                session.startedAt(), System.currentTimeMillis());
        semaphoreManager.saveSession(simId, abandoned, FINISHED_SESSION_TTL);
        sseService.activity("Sesi " + userId + " ditinggalkan, slot dan tiketnya ditarik kembali");
    }
}
