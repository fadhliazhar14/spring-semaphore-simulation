package com.fadhli.simulation.service;

import com.fadhli.simulation.manager.PurchaseSession;
import com.fadhli.simulation.manager.ApplicationInstance;
import com.fadhli.simulation.manager.SimulationConfig;
import com.fadhli.simulation.manager.SimulationStateStore;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Menembakkan gelombang pembeli tiruan.
 *
 * <p>Perilaku yang ditiru adalah orang yang menekan tombol beli, ditolak karena semua slot
 * terpakai, lalu menekan lagi. Bukan orang yang diparkir di ruang tunggu. Bedanya bukan soal rasa:
 * yang ditolak tidak menahan apa pun di sisi server, sedangkan yang menunggu menahan satu jalur
 * eksekusi selama ia menunggu. Perebutan yang sebenarnya terjadi di antara percobaan-percobaan
 * itu, bukan di dalam sebuah barisan.
 *
 * <p>Setelah dapat slot, pembeli menjalani sesi berlangkah: memilih tiket, membayar, lalu
 * menerima tiketnya. Di antara langkah ada waktu berpikir, dan waktu berpikir itulah yang membuat
 * simulasi bisa diamati.
 *
 * <p>Baik jeda antar percobaan maupun waktu berpikir antar langkah tidak boleh dijalankan dengan
 * menidurkan thread pekerja. Seribu pembeli yang sedang berjeda akan menghabiskan seribu thread
 * padahal tidak sedang mengerjakan apa pun — persis kesalahan yang membuat sebuah batas
 * konkurensi kehilangan artinya. Semua jeda dijadwalkan, dan thread pekerja hanya dipakai saat
 * sebuah langkah benar-benar dikerjakan.
 */
@Service
public class WarTrafficGenerator {

    private static final Logger log = LoggerFactory.getLogger(WarTrafficGenerator.class);

    /** Sebaran jeda supaya percobaan ulang tidak berdenyut serentak seperti satu gelombang. */
    private static final double JITTER = 0.4;

    private final SessionService sessionService;
    private final SimulationStateStore store;
    private final SseService sseService;
    private final ApplicationInstance instance;

    /** Menjalankan percobaan. Ukurannya membatasi berapa pembelian yang benar-benar bersamaan. */
    private final ExecutorService attemptPool = Executors.newFixedThreadPool(50);

    /** Hanya menghitung mundur jeda; tidak pernah menjalankan pembelian. */
    private final ScheduledExecutorService retryScheduler = Executors.newScheduledThreadPool(4);

    public WarTrafficGenerator(SessionService sessionService, SimulationStateStore store,
                               SseService sseService, ApplicationInstance instance) {
        this.sessionService = sessionService;
        this.store = store;
        this.sseService = sseService;
        this.instance = instance;
    }

    /**
     * Melepas satu gelombang pembeli.
     *
     * @param maxAttempts    berapa kali seorang pembeli mau mencoba sebelum menyerah
     * @param retryDelayMs   jeda rata-rata sebelum mencoba lagi
     */
    public void releaseWave(String simId, int buyers, int maxAttempts, int retryDelayMs) {
        String waveId = Long.toString(System.currentTimeMillis(), 36);
        for (int i = 1; i <= buyers; i++) {
            // userId memuat identitas instance supaya dua instance yang menembak bersamaan tidak
            // memakai nama pengguna yang sama, dan supaya papan menunjukkan asal request.
            String userId = instance.id() + "-" + waveId + "-" + String.format("%05d", i);
            submitAttempt(simId, userId, 1, Math.max(1, maxAttempts), Math.max(1, retryDelayMs));
        }
        sseService.activity(buyers + " pembeli masuk dari " + instance.id()
                + " (maksimal " + maxAttempts + " percobaan)");
    }

    /** Satu percobaan merebut slot. Kalau ditolak, pembeli menjadwalkan percobaan berikutnya. */
    private void submitAttempt(String simId, String userId, int attempt, int maxAttempts,
                               int retryDelayMs) {
        attemptPool.submit(() -> {
            PurchaseSession session;
            try {
                session = sessionService.start(simId, userId);
            } catch (Exception e) {
                log.warn("Attempt {} for {} failed: {}", attempt, userId, e.getMessage());
                return;
            }

            if (session != null) {
                scheduleNextStep(simId, userId);
                return;
            }
            if (attempt < maxAttempts) {
                retryScheduler.schedule(
                        () -> submitAttempt(simId, userId, attempt + 1, maxAttempts, retryDelayMs),
                        jitter(retryDelayMs), TimeUnit.MILLISECONDS);
                return;
            }
            // Jatah percobaan habis tanpa pernah kebagian slot. Pembeli ini pulang.
            store.increment(simId, SimulationStateStore.METRIC_GAVE_UP);
        });
    }

    /**
     * Menjadwalkan langkah berikutnya setelah waktu berpikir. Selama menunggu, tidak ada thread
     * yang tertahan — hanya slot di Redis, dan itu memang yang sedang diamati.
     */
    private void scheduleNextStep(String simId, String userId) {
        int thinkTimeMs = thinkTimeOf(simId);
        retryScheduler.schedule(() -> attemptPool.submit(() -> {
            PurchaseSession moved;
            try {
                moved = sessionService.advance(simId, userId);
            } catch (Exception e) {
                log.warn("Step for {} failed: {}", userId, e.getMessage());
                return;
            }
            // Sesi yang sudah berakhir, atau yang slotnya keburu direbut reaper, berhenti di sini.
            if (moved != null && moved.state().holdingSlot()) {
                scheduleNextStep(simId, userId);
            }
        }), jitter(thinkTimeMs), TimeUnit.MILLISECONDS);
    }

    private int thinkTimeOf(String simId) {
        SimulationConfig cfg = store.config(simId);
        return (cfg == null) ? 300 : Math.max(1, cfg.thinkTimeMs());
    }

    private static long jitter(int baseMs) {
        double spread = baseMs * JITTER;
        return Math.max(1, Math.round(baseMs + ThreadLocalRandom.current().nextDouble(-spread, spread)));
    }

    @PreDestroy
    void shutdown() {
        retryScheduler.shutdownNow();
        attemptPool.shutdownNow();
    }
}
