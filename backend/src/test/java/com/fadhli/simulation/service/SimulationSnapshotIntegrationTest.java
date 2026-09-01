package com.fadhli.simulation.service;

import com.fadhli.simulation.dto.SimulationStatusDto;
import com.fadhli.simulation.dto.SlotStateDto;
import com.fadhli.simulation.manager.SemaphoreManager;
import com.fadhli.simulation.manager.PurchaseSession;
import com.fadhli.simulation.manager.SessionState;
import com.fadhli.simulation.manager.SlotLease;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Menjaga isi snapshot yang dibaca papan observasi.
 *
 * <p>Yang diuji di sini bukan kebenaran konkurensinya — itu urusan {@code SemaphoreManagerTest} —
 * melainkan apakah keadaan slot benar-benar sampai ke DTO. Sebuah slot bisa saja dikelola dengan
 * benar tetapi tidak pernah terlihat, dan bagi proyek ini yang tidak terlihat sama saja dengan
 * tidak ada.
 */
@SpringBootTest
class SimulationSnapshotIntegrationTest {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private SemaphoreManager semaphoreManager;

    @Test
    @DisplayName("Snapshot should expose every slot, including the idle ones")
    void testSnapshotCarriesSlotIdentity() {
        int permits = 4;
        ticketService.initSimulation("Snapshot Test", 10, permits);
        String simId = ticketService.getCurrentSimId();
        assertNotNull(simId);

        SimulationStatusDto empty = ticketService.getCurrentStatus();
        assertEquals(permits, empty.getSlots().size(),
                "Slot kosong tetap harus dikirim agar jumlah kotak di papan tidak berubah-ubah");
        assertTrue(empty.getSlots().stream().noneMatch(SlotStateDto::occupied));
        assertNotNull(empty.getReportedBy(), "Snapshot harus menyebut instance penyusunnya");

        SlotLease lease = semaphoreManager.tryAcquire(simId, "user-nonton");
        assertNotNull(lease);
        long now = System.currentTimeMillis();
        semaphoreManager.saveSession(simId, new PurchaseSession("user-nonton",
                        SessionState.SELECTING, lease.slot(), lease.fencingToken(), now, now),
                java.time.Duration.ofMinutes(1));
        try {
            List<SlotStateDto> slots = ticketService.getCurrentStatus().getSlots();
            assertEquals(permits, slots.size());

            SlotStateDto held = slots.stream()
                    .filter(SlotStateDto::occupied)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Slot yang dipegang harus terlihat di snapshot"));

            assertEquals(lease.slot(), held.slot());
            assertEquals(lease.slotNumber(), held.number());
            assertEquals("user-nonton", held.userId());
            assertEquals("SELECTING", held.phase(),
                    "Langkah pada papan harus datang dari sesi, bukan dari catatan pemilik slot");
            assertNotNull(held.instanceId(), "Papan harus bisa menunjukkan instance pemegang slot");
            assertTrue(held.heldForMs() >= 0);
            assertTrue(held.leaseRemainingMs() > 0
                            && held.leaseRemainingMs() <= SemaphoreManager.SLOT_LEASE.toMillis(),
                    "Sisa lease harus berada di dalam rentang lease yang berlaku");

            assertEquals(1, slots.stream().filter(SlotStateDto::occupied).count());
        } finally {
            semaphoreManager.release(lease);
        }

        assertTrue(ticketService.getCurrentStatus().getSlots().stream()
                .noneMatch(SlotStateDto::occupied), "Slot harus kosong lagi setelah dilepas");
    }
}
