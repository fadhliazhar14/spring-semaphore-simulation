package com.fadhli.simulation.service;

import com.fadhli.simulation.dto.SimulationStatusDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Mengirim keadaan simulasi ke papan observasi dengan irama tetap, bukan tiap kali ada peristiwa.
 *
 * <p>Sebelumnya snapshot disusun ulang di setiap titik peristiwa: masuk antrean, dapat slot,
 * berhasil, gagal, lepas slot. Tiga ratus request menghasilkan hampir seribu snapshot, dan tiap
 * snapshot berarti sekali baca ke Postgres dan beberapa kali ke Redis. Akibatnya papan yang
 * seharusnya menampilkan simulasi justru ikut membebani simulasi yang sedang ditampilkannya,
 * dan gambarnya tersendat persis pada saat paling menarik untuk diamati.
 *
 * <p>Sekarang biayanya rata: sepuluh snapshot per detik tanpa peduli seberapa deras trafiknya.
 * Sepuluh kali per detik cukup untuk mata — apalagi karena simulasi ini memang sengaja
 * diperlambat supaya bisa diamati — sementara catatan aktivitas tetap dikirim per peristiwa
 * lewat {@link SseService#activity(String)} karena isinya hanya sebaris teks.
 */
@Component
public class SimulationSnapshotPublisher {

    private static final Logger log = LoggerFactory.getLogger(SimulationSnapshotPublisher.class);

    private final TicketService ticketService;
    private final SseService sseService;

    public SimulationSnapshotPublisher(TicketService ticketService, SseService sseService) {
        this.ticketService = ticketService;
        this.sseService = sseService;
    }

    @Scheduled(fixedRate = 100)
    public void publish() {
        // Tanpa penonton, snapshot tidak perlu disusun sama sekali.
        if (sseService.getSubscriberCount() == 0) {
            return;
        }
        try {
            SimulationStatusDto snapshot = ticketService.currentSnapshot();
            if (snapshot != null) {
                sseService.broadcast(snapshot);
            }
        } catch (Exception e) {
            // Penyusunan snapshot tidak boleh mematikan penjadwal; kegagalan sesaat cukup dicatat.
            log.warn("Failed to publish simulation snapshot: {}", e.getMessage());
        }
    }
}
