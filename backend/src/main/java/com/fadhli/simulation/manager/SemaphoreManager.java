package com.fadhli.simulation.manager;

import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class SemaphoreManager {

    private volatile Semaphore semaphore;
    private final AtomicInteger totalPermits = new AtomicInteger(10);
    private final AtomicInteger activePermits = new AtomicInteger(0);

    public SemaphoreManager() {
        this.semaphore = new Semaphore(totalPermits.get(), true);
    }

    public synchronized void init(int permits) {
        if (permits <= 0) {
            throw new IllegalArgumentException("Permits must be greater than 0");
        }
        this.totalPermits.set(permits);
        this.activePermits.set(0);
        this.semaphore = new Semaphore(permits, true);
    }

    public boolean tryAcquire(long timeout, TimeUnit unit) throws InterruptedException {
        boolean acquired = this.semaphore.tryAcquire(timeout, unit);
        if (acquired) {
            activePermits.incrementAndGet();
        }
        return acquired;
    }

    public void release() {
        if (activePermits.get() > 0) {
            activePermits.decrementAndGet();
        }
        this.semaphore.release();
    }

    public int getAvailablePermits() {
        return this.semaphore.availablePermits();
    }

    public int getQueueLength() {
        return this.semaphore.getQueueLength();
    }

    public int getTotalPermits() {
        return this.totalPermits.get();
    }

    public int getActivePermits() {
        return Math.max(0, this.activePermits.get());
    }
}
