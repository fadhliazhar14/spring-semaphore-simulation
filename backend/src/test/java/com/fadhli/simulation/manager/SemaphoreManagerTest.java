package com.fadhli.simulation.manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SemaphoreManagerTest {

    private SemaphoreManager semaphoreManager;

    @BeforeEach
    void setUp() {
        semaphoreManager = new SemaphoreManager();
        semaphoreManager.init(3);
    }

    @Test
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
    }
}
