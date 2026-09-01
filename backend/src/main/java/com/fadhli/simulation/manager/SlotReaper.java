package com.fadhli.simulation.manager;

import org.springframework.beans.factory.ObjectProvider;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Mengembalikan slot yang pemegangnya tidak pernah melepas.
 *
 * <p>Ini jawaban atas pertanyaan "apa yang terjadi kalau instance yang sedang memegang slot mati".
 * Pada semaphore biasa jawabannya adalah permit itu hilang selamanya dan kapasitas simulasi
 * menyusut diam-diam sampai seluruh sistem berhenti melayani. Dengan lease, slot punya tenggat:
 * lewat tenggat, slot ditarik kembali ke antrean dan bisa dipakai orang lain.
 *
 * <p>Hanya satu instance yang boleh menyapu pada satu waktu. Itu bukan demi kebenaran — skrip
 * perebutan slot sudah atomik dan aman dijalankan bersamaan — melainkan supaya jumlah pekerjaan
 * dan jumlah baris log tidak berlipat sebanyak jumlah instance yang berjalan.
 */
@Component
public class SlotReaper {

    private static final Logger log = LoggerFactory.getLogger(SlotReaper.class);

    private static final String REAPER_LOCK = "sim:reaper:lock";

    /**
     * Batas waktu kunci dipegang. Kalau instance yang sedang menyapu mati, kunci terlepas sendiri
     * setelah tenggat ini sehingga penyapuan tidak berhenti selamanya.
     */
    private static final long LOCK_LEASE_SECONDS = 5;

    private final RedissonClient redisson;
    private final SemaphoreManager semaphoreManager;
    private final SimulationStateStore store;
    private final ObjectProvider<SessionJanitor> janitor;

    public SlotReaper(RedissonClient redisson, SemaphoreManager semaphoreManager,
                      SimulationStateStore store, ObjectProvider<SessionJanitor> janitor) {
        this.redisson = redisson;
        this.semaphoreManager = semaphoreManager;
        this.store = store;
        this.janitor = janitor;
    }

    /**
     * Pembereskan sesi milik slot yang direbut. Diambil lewat penyedia, bukan disuntik langsung,
     * karena pembereskannya hidup di lapisan layanan yang justru bergantung pada paket ini.
     */
    public interface SessionJanitor {
        void abandon(String simId, String userId);
    }

    @Scheduled(fixedRate = 1000)
    public void reapCurrentSimulation() {
        String simId = store.currentSimId();
        if (simId == null) {
            return;
        }

        RLock lock = redisson.getLock(REAPER_LOCK);
        boolean locked = false;
        try {
            // Tidak menunggu sama sekali: kalau instance lain sedang menyapu, penyapuan berikutnya
            // toh datang satu detik lagi.
            locked = lock.tryLock(0, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (locked) {
                reap(simId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * Menyapu satu simulasi dan mengembalikan jumlah slot yang berhasil direbut.
     *
     * <p>Tenggat dibaca sekali di awal supaya slot yang lease-nya baru saja kedaluwarsa di tengah
     * penyapuan tidak ikut terambil dengan waktu yang berbeda-beda antar slot.
     */
    public int reap(String simId) {
        long now = System.currentTimeMillis();
        List<String> expired = semaphoreManager.expiredSlots(simId, now);
        if (expired.isEmpty()) {
            return 0;
        }

        // Pemilik tiap slot dibaca sebelum direbut, karena setelah direbut catatannya hilang dan
        // tidak ada lagi yang bisa memberi tahu sesi siapa yang harus dibereskan.
        Map<String, SlotOwner> owners = semaphoreManager.getSlotOwners(simId);

        int reclaimed = 0;
        for (String slot : expired) {
            if (!semaphoreManager.reclaim(simId, slot, now)) {
                continue;
            }
            reclaimed++;
            log.warn("Reclaimed {} of simulation {}: its lease expired without a release",
                    slot, simId);

            SlotOwner owner = owners.get(slot);
            if (owner != null) {
                janitor.ifAvailable(j -> j.abandon(simId, owner.userId()));
            }
        }
        return reclaimed;
    }
}
