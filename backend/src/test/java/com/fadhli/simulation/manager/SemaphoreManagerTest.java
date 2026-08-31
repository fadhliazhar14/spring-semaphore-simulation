package com.fadhli.simulation.manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SemaphoreManagerTest {

    @Autowired
    private SemaphoreManager semaphoreManager;

    @BeforeEach
    void setUp() {
        semaphoreManager.init(3);
    }

    @Test
    @DisplayName("Should acquire and release permits correctly with Redisson")
    void testPermitAcquireAndRelease() throws InterruptedException {
        assertEquals(3, semaphoreManager.getTotalPermits());
        assertEquals(3, semaphoreManager.getAvailablePermits());
        assertEquals(0, semaphoreManager.getActivePermits());

        boolean acquired1 = semaphoreManager.tryAcquire(100, TimeUnit.MILLISECONDS);
        assertTrue(acquired1);
        assertEquals(2, semaphoreManager.getAvailablePermits());
        assertEquals(1, semaphoreManager.getActivePermits());

        boolean acquired2 = semaphoreManager.tryAcquire(100, TimeUnit.MILLISECONDS);
        boolean acquired3 = semaphoreManager.tryAcquire(100, TimeUnit.MILLISECONDS);
        assertTrue(acquired2);
        assertTrue(acquired3);
        assertEquals(0, semaphoreManager.getAvailablePermits());
        assertEquals(3, semaphoreManager.getActivePermits());

        // 4th acquire should fail within timeout
        boolean acquired4 = semaphoreManager.tryAcquire(50, TimeUnit.MILLISECONDS);
        assertFalse(acquired4);

        // Release one permit
        semaphoreManager.release();
        assertEquals(1, semaphoreManager.getAvailablePermits());
        assertEquals(2, semaphoreManager.getActivePermits());

        // Clean up remaining
        semaphoreManager.release();
        semaphoreManager.release();
        assertEquals(0, semaphoreManager.getActivePermits());
    }

    @Test
    @DisplayName("Should manage atomic stock reservation and user sessions in Redis")
    void testStockAndSessionManagement() {
        Long eventId = 999L;
        semaphoreManager.initStock(eventId, 2);

        assertTrue(semaphoreManager.tryReserveStock(eventId));
        assertTrue(semaphoreManager.tryReserveStock(eventId));
        assertFalse(semaphoreManager.tryReserveStock(eventId)); // out of stock

        semaphoreManager.recordSession(eventId, "user-abc", "PROCESSING", Duration.ofMinutes(1));
        assertEquals("PROCESSING", semaphoreManager.getSession(eventId, "user-abc"));
    }
}

