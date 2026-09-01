package com.fadhli.simulation.manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SemaphoreManagerTest {

    @Autowired
    private SemaphoreManager semaphoreManager;

    @Autowired
    private SlotReaper slotReaper;

    /** simId unik per test agar dua test tidak pernah memakai key Redis yang sama. */
    private String simId;

    @BeforeEach
    void setUp() {
        simId = newSimId();
        semaphoreManager.initSemaphore(simId, 3);
    }

    private static String newSimId() {
        return "test-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    @DisplayName("Should hand out and take back numbered slots")
    void testSlotAcquireAndRelease() throws InterruptedException {
        assertEquals(3, semaphoreManager.getAvailablePermits(simId));
        assertEquals(0, semaphoreManager.getActivePermits(simId));

        SlotLease first = semaphoreManager.tryAcquire(simId, "user-1");
        assertNotNull(first);
        assertEquals(2, semaphoreManager.getAvailablePermits(simId));
        assertEquals(1, semaphoreManager.getActivePermits(simId));

        // Slot punya identitas, dan papan observasi harus bisa membaca siapa yang menempatinya.
        Map<String, SlotOwner> owners = semaphoreManager.getSlotOwners(simId);
        assertEquals(Set.of(first.slot()), owners.keySet());
        assertEquals("user-1", owners.get(first.slot()).userId());
        assertTrue(first.slotNumber() >= 1 && first.slotNumber() <= 3);

        SlotLease second = semaphoreManager.tryAcquire(simId, "user-2");
        SlotLease third = semaphoreManager.tryAcquire(simId, "user-3");
        assertNotNull(second);
        assertNotNull(third);
        assertEquals(3, Set.of(first.slot(), second.slot(), third.slot()).size(),
                "Tiga pemegang bersamaan harus menempati tiga slot yang berbeda");
        assertEquals(0, semaphoreManager.getAvailablePermits(simId));
        assertEquals(3, semaphoreManager.getActivePermits(simId));

        assertEquals(SemaphoreManager.ReleaseResult.RELEASED, semaphoreManager.release(first));
        assertEquals(1, semaphoreManager.getAvailablePermits(simId));
        assertEquals(2, semaphoreManager.getActivePermits(simId));

        semaphoreManager.release(second);
        semaphoreManager.release(third);
        assertEquals(0, semaphoreManager.getActivePermits(simId));
        assertEquals(3, semaphoreManager.getAvailablePermits(simId));
    }

    @Test
    @DisplayName("A held slot must be refused immediately, never queued for")
    void testHeldSlotIsRefusedWithoutWaiting() {
        String soloSim = newSimId();
        semaphoreManager.initSemaphore(soloSim, 1);

        SlotLease held = semaphoreManager.tryAcquire(soloSim, "user-1");
        assertNotNull(held);

        long startedAt = System.nanoTime();
        SlotLease denied = semaphoreManager.tryAcquire(soloSim, "user-2");
        long waitedMs = (System.nanoTime() - startedAt) / 1_000_000L;

        assertNull(denied, "Slot yang sedang dipegang tidak boleh diberikan ke peminta kedua");
        assertEquals(1, semaphoreManager.getActivePermits(soloSim));
        // Inti Tahap 5: yang tidak kebagian ditolak saat itu juga, bukan diparkir menunggu.
        assertTrue(waitedMs < 100, "Penolakan harus seketika, tapi memakan " + waitedMs + " ms");

        semaphoreManager.release(held);
        assertNotNull(semaphoreManager.tryAcquire(soloSim, "user-2"));
    }

    @Test
    @DisplayName("Reaper should take back a slot whose lease has expired")
    void testReaperReclaimsExpiredLease() throws InterruptedException {
        SlotLease lease = semaphoreManager.tryAcquire(
                simId, "user-mati", Duration.ofMillis(150));
        assertNotNull(lease);
        assertEquals(2, semaphoreManager.getAvailablePermits(simId));

        // Selama lease masih berlaku, slot tidak boleh diganggu.
        assertEquals(0, slotReaper.reap(simId));
        assertEquals(1, semaphoreManager.getActivePermits(simId));

        Thread.sleep(250);

        assertEquals(1, slotReaper.reap(simId), "Slot kedaluwarsa harus direbut kembali");
        assertEquals(3, semaphoreManager.getAvailablePermits(simId));
        assertEquals(0, semaphoreManager.getActivePermits(simId));
        assertTrue(semaphoreManager.getSlotOwners(simId).isEmpty());

        // Penyapuan kedua tidak boleh mengembalikan slot yang sama untuk kedua kalinya.
        assertEquals(0, slotReaper.reap(simId));
        assertEquals(3, semaphoreManager.getAvailablePermits(simId));
    }

    @Test
    @DisplayName("Release with a stale fencing token must be rejected without adding a slot back")
    void testStaleReleaseIsRejected() throws InterruptedException {
        String soloSim = newSimId();
        semaphoreManager.initSemaphore(soloSim, 1);

        SlotLease stale = semaphoreManager.tryAcquire(
                soloSim, "user-mati", Duration.ofMillis(150));
        assertNotNull(stale);
        Thread.sleep(250);
        assertEquals(1, slotReaper.reap(soloSim));

        // Pemilik baru mengambil slot yang sama, karena simulasi ini hanya punya satu slot.
        SlotLease current = semaphoreManager.tryAcquire(soloSim, "user-baru");
        assertNotNull(current);
        assertEquals(stale.slot(), current.slot());
        assertNotEquals(stale.fencingToken(), current.fencingToken());

        assertEquals(SemaphoreManager.ReleaseResult.STALE_TOKEN, semaphoreManager.release(stale),
                "Pemilik lama tidak boleh berhasil melepas slot yang sudah pindah tangan");
        assertEquals(0, semaphoreManager.getAvailablePermits(soloSim),
                "Pelepasan basi tidak boleh menambah token ke antrean");
        assertEquals(1, semaphoreManager.getActivePermits(soloSim));
        assertEquals("user-baru", semaphoreManager.getSlotOwners(soloSim).get(current.slot()).userId());

        assertEquals(SemaphoreManager.ReleaseResult.RELEASED, semaphoreManager.release(current));
        assertEquals(1, semaphoreManager.getAvailablePermits(soloSim));
    }

    @Test
    @DisplayName("A reaped owner releasing late must not inflate the slot queue")
    void testReleaseAfterReapDoesNotInflateQueue() throws InterruptedException {
        SlotLease lease = semaphoreManager.tryAcquire(
                simId, "user-mati", Duration.ofMillis(150));
        assertNotNull(lease);
        Thread.sleep(250);
        assertEquals(1, slotReaper.reap(simId));
        assertEquals(3, semaphoreManager.getAvailablePermits(simId));

        assertEquals(SemaphoreManager.ReleaseResult.ALREADY_REAPED, semaphoreManager.release(lease));
        assertEquals(3, semaphoreManager.getAvailablePermits(simId),
                "Jumlah slot tidak boleh melebihi jumlah yang diinisialisasi");
    }

    @Test
    @DisplayName("Concurrent holders must never exceed the configured slot count")
    void testConcurrencyNeverExceedsSlotCount() throws InterruptedException {
        String busySim = newSimId();
        int slots = 5;
        int workers = 60;
        semaphoreManager.initSemaphore(busySim, slots);

        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        AtomicInteger acquired = new AtomicInteger();
        Set<String> occupied = ConcurrentHashMap.newKeySet();
        AtomicInteger doubleBooked = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(workers);

        for (int i = 1; i <= workers; i++) {
            final String userId = "user-" + i;
            executor.submit(() -> {
                try {
                    start.await();
                    // Tidak ada penantian: yang ditolak mencoba lagi, persis seperti orang yang
                    // menekan tombol beli berulang kali.
                    SlotLease lease = null;
                    long giveUpAt = System.currentTimeMillis() + 10_000;
                    while (lease == null && System.currentTimeMillis() < giveUpAt) {
                        lease = semaphoreManager.tryAcquire(busySim, userId);
                        if (lease == null) {
                            Thread.sleep(5);
                        }
                    }
                    if (lease == null) {
                        return;
                    }
                    try {
                        acquired.incrementAndGet();
                        if (!occupied.add(lease.slot())) {
                            doubleBooked.incrementAndGet();
                        }
                        peak.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                        Thread.sleep(10);
                        inFlight.decrementAndGet();
                        occupied.remove(lease.slot());
                    } finally {
                        semaphoreManager.release(lease);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "Seluruh worker harus selesai");
        executor.shutdown();

        assertEquals(0, doubleBooked.get(), "Satu slot tidak boleh dipegang dua worker sekaligus");
        assertTrue(peak.get() <= slots,
                "Pemegang bersamaan mencapai " + peak.get() + ", melebihi " + slots + " slot");
        assertEquals(workers, acquired.get(), "Semua worker seharusnya kebagian lewat percobaan ulang");
        assertEquals(0, semaphoreManager.getActivePermits(busySim));
        assertEquals(slots, semaphoreManager.getAvailablePermits(busySim),
                "Jumlah slot harus kembali utuh, tidak berkurang dan tidak membengkak");
    }

    @Test
    @DisplayName("Should manage atomic stock reservation and user sessions in Redis")
    void testStockAndSessionManagement() {
        semaphoreManager.initStock(simId, 2);

        assertTrue(semaphoreManager.tryReserveStock(simId));
        assertTrue(semaphoreManager.tryReserveStock(simId));
        assertFalse(semaphoreManager.tryReserveStock(simId));

        long now = System.currentTimeMillis();
        semaphoreManager.saveSession(simId,
                new PurchaseSession("user-abc", SessionState.PAYING, "slot-1", "tok", now, now),
                Duration.ofMinutes(1));

        PurchaseSession stored = semaphoreManager.getSession(simId, "user-abc");
        assertNotNull(stored);
        assertEquals(SessionState.PAYING, stored.state());
        assertEquals("slot-1", stored.slot());
        // Sesi harus mengingat penanda kepemilikan slotnya, kalau tidak reaper tidak punya cara
        // membereskan sesi yang terlantar.
        assertEquals("tok", stored.fencingToken());
    }

    @Test
    @DisplayName("Two simulations must not share slots or stock")
    void testSimulationsAreIsolated() throws InterruptedException {
        String other = newSimId();
        semaphoreManager.initSemaphore(other, 1);
        semaphoreManager.initStock(simId, 1);
        semaphoreManager.initStock(other, 1);

        // Menghabiskan slot di satu simulasi tidak boleh memengaruhi simulasi lain
        SlotLease lease = semaphoreManager.tryAcquire(other, "user-x");
        assertNotNull(lease);
        assertEquals(0, semaphoreManager.getAvailablePermits(other));
        assertEquals(3, semaphoreManager.getAvailablePermits(simId));
        assertEquals(0, semaphoreManager.getActivePermits(simId));

        assertTrue(semaphoreManager.tryReserveStock(other));
        assertFalse(semaphoreManager.tryReserveStock(other));
        assertTrue(semaphoreManager.tryReserveStock(simId), "Stok simulasi lain harus tetap utuh");

        // Reaper yang menyapu satu simulasi tidak boleh menyentuh simulasi lain
        assertEquals(0, slotReaper.reap(simId));
        assertEquals(1, semaphoreManager.getActivePermits(other));

        semaphoreManager.release(lease);
    }
}
