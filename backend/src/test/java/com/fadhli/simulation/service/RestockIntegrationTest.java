package com.fadhli.simulation.service;

import com.fadhli.simulation.dto.SimulationStatusDto;
import com.fadhli.simulation.manager.SemaphoreManager;
import com.fadhli.simulation.model.TicketEvent;
import com.fadhli.simulation.repository.TicketEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Menjaga penambahan stok di tengah simulasi.
 *
 * <p>Yang paling mudah salah di sini adalah kapasitas yang dilaporkan. Kalau stok bertambah tetapi
 * total tetap, dasbor akan melaporkan terjual melebihi seratus persen; kalau total bertambah
 * tetapi kuota cepat di Redis tidak, tiket tambahannya tidak akan pernah bisa dibeli karena
 * penjaga di depan sudah menolak lebih dulu.
 */
@SpringBootTest
class RestockIntegrationTest {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private SemaphoreManager semaphoreManager;

    @Autowired
    private TicketEventRepository ticketEventRepository;

    @Test
    @DisplayName("Restock should raise the Redis quota, the database, and the reported capacity together")
    void testRestockRaisesEveryCounter() {
        int initialStock = 3;
        TicketEvent event = ticketService.initSimulation("Restock Test", initialStock, 2);
        String simId = ticketService.getCurrentSimId();
        assertNotNull(simId);

        // Habiskan seluruh kuota cepat di Redis.
        for (int i = 0; i < initialStock; i++) {
            assertTrue(semaphoreManager.tryReserveStock(simId));
        }
        assertFalse(semaphoreManager.tryReserveStock(simId), "Stok awal harus sudah habis");

        assertEquals(10, ticketService.restock(simId, 10));

        assertTrue(semaphoreManager.tryReserveStock(simId),
                "Tiket tambahan harus bisa dipesan lewat penjaga di Redis");

        TicketEvent refreshed = ticketEventRepository.findById(event.getId()).orElseThrow();
        assertEquals(initialStock + 10, refreshed.getTotalTickets(),
                "Kapasitas di basis data harus ikut naik");
        assertEquals(initialStock + 10, refreshed.getAvailableTickets(),
                "Stok tersedia di basis data harus ikut naik");

        SimulationStatusDto status = ticketService.getCurrentStatus();
        assertEquals(initialStock + 10, status.getTotalTickets(),
                "Kapasitas yang dilaporkan harus memperhitungkan stok tambahan");
        assertTrue(status.getAvailableTickets() <= status.getTotalTickets(),
                "Tersedia tidak boleh melebihi kapasitas yang dilaporkan");
    }

    @Test
    @DisplayName("Restock should ignore non-positive amounts and unknown simulations")
    void testRestockRejectsNonsense() {
        ticketService.initSimulation("Restock Guard", 5, 2);
        String simId = ticketService.getCurrentSimId();

        assertEquals(0, ticketService.restock(simId, 0));
        assertEquals(0, ticketService.restock(simId, -10));
        assertEquals(0, ticketService.restock("simulasi-yang-tidak-ada", 10));
    }
}
