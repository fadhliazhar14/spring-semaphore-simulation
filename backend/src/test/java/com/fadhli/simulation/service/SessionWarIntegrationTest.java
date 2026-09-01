package com.fadhli.simulation.service;

import com.fadhli.simulation.dto.SimulationStatusDto;
import com.fadhli.simulation.manager.PurchaseSession;
import com.fadhli.simulation.manager.SemaphoreManager;
import com.fadhli.simulation.manager.SessionState;
import com.fadhli.simulation.manager.SlotReaper;
import com.fadhli.simulation.model.TicketEvent;
import com.fadhli.simulation.repository.TicketEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Perang sungguhan dengan sesi berlangkah.
 *
 * <p>Yang dijaga di sini adalah keutuhan hitungan setelah alur dipecah menjadi beberapa langkah.
 * Pemecahan itu membuka jalan bagi tiket bocor: sesi yang berhenti di tengah setelah stok ditahan
 * akan menelan satu tiket tanpa pernah menjualnya, dan kebocoran semacam itu tidak menimbulkan
 * error apa pun — angkanya saja yang perlahan tidak masuk akal.
 */
@SpringBootTest
class SessionWarIntegrationTest {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private SemaphoreManager semaphoreManager;

    @Autowired
    private SlotReaper slotReaper;

    @Autowired
    private TicketEventRepository ticketEventRepository;

    @Test
    @DisplayName("Every ticket must end up sold, returned, or still on the shelf — never lost")
    void testSteppedSessionsKeepTheBooksBalanced() throws InterruptedException {
        int stock = 30;
        int permits = 5;
        int buyers = 80;

        // Waktu berpikir nol: yang diuji keutuhan hitungan, bukan kemudahan pengamatan.
        TicketEvent event = ticketService.initSimulation("Sesi War", stock, permits, 0, 70);
        String simId = ticketService.getCurrentSimId();
        assertNotNull(simId);

        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(buyers);
        AtomicInteger finished = new AtomicInteger();
        var terminalStates = ConcurrentHashMap.<SessionState>newKeySet();

        for (int i = 1; i <= buyers; i++) {
            final String userId = "buyer-" + String.format("%03d", i);
            executor.submit(() -> {
                try {
                    start.await();

                    // Tidak ada antrean: yang ditolak mencoba lagi sampai kebagian atau menyerah.
                    PurchaseSession session = null;
                    long giveUpAt = System.currentTimeMillis() + 20_000;
                    while (session == null && System.currentTimeMillis() < giveUpAt) {
                        session = sessionService.start(simId, userId);
                        if (session == null) {
                            Thread.sleep(5);
                        }
                    }
                    if (session == null) {
                        return;
                    }

                    // Sesi dijalankan sampai keadaan akhirnya tercapai.
                    while (session != null && session.state().holdingSlot()) {
                        session = sessionService.advance(simId, userId);
                    }
                    if (session != null) {
                        terminalStates.add(session.state());
                        finished.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS), "Seluruh pembeli harus selesai");
        executor.shutdown();

        // Tidak boleh ada sesi yang menggantung setelah semua pembeli selesai.
        assertEquals(0, slotReaper.reap(simId), "Tidak boleh ada sesi terlantar");
        assertEquals(permits, semaphoreManager.getAvailablePermits(simId),
                "Semua slot harus kembali utuh");
        assertEquals(0, semaphoreManager.getActivePermits(simId));

        SimulationStatusDto status = ticketService.getCurrentStatus();
        TicketEvent finalEvent = ticketEventRepository.findById(event.getId()).orElseThrow();

        assertEquals(buyers, finished.get(), "Setiap pembeli harus mencapai keadaan akhir");
        assertTrue(terminalStates.contains(SessionState.DONE), "Sebagian harus dapat tiket");
        assertTrue(terminalStates.stream().noneMatch(SessionState::holdingSlot),
                "Keadaan akhir tidak boleh ada yang masih memegang slot");

        // Inti pengujiannya: tiap tiket berakhir terjual, dikembalikan, atau masih di rak.
        assertEquals(stock, finalEvent.getAvailableTickets() + status.getSuccessRequests(),
                "Tiket terjual ditambah sisa stok harus sama dengan stok awal");
        assertEquals(status.getSuccessRequests(), stock - finalEvent.getAvailableTickets(),
                "Metrik sukses harus cocok dengan penjualan di basis data");
        assertTrue(finalEvent.getAvailableTickets() >= 0, "Stok tidak boleh negatif");

        // Kuota cepat di Redis harus kembali sejalan dengan basis data setelah perang usai,
        // termasuk tiket-tiket yang sempat ditahan lalu dikembalikan karena pembayaran gagal.
        // Kalau penahanan tidak pernah dikembalikan, angka inilah yang lebih dulu melenceng.
        assertEquals(finalEvent.getAvailableTickets().longValue(),
                semaphoreManager.availableStock(simId),
                "Sisa kuota di Redis harus sama dengan sisa stok di basis data");

        assertTrue(status.getFailedPayment() > 0,
                "Dengan peluang bayar 70 persen, sebagian pembayaran seharusnya ditolak");
    }
}
