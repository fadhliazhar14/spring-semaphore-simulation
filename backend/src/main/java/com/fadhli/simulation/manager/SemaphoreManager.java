package com.fadhli.simulation.manager;

import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class SemaphoreManager {

    private static final Logger log = LoggerFactory.getLogger(SemaphoreManager.class);
    private static final String SEMAPHORE_KEY = "simulation:semaphore:global";
    private static final String STOCK_KEY_PREFIX = "simulation:stock:";
    private static final String SESSION_KEY_PREFIX = "simulation:session:";

    private final RedissonClient redissonClient;
    private final AtomicInteger totalPermits = new AtomicInteger(10);
    private final AtomicInteger waitingRequests = new AtomicInteger(0);

    public SemaphoreManager(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
        init(totalPermits.get());
    }

    public synchronized void init(int permits) {
        if (permits <= 0) {
            throw new IllegalArgumentException("Permits must be greater than 0");
        }
        this.totalPermits.set(permits);
        this.waitingRequests.set(0);
        RSemaphore semaphore = redissonClient.getSemaphore(SEMAPHORE_KEY);
        semaphore.delete();
        semaphore.trySetPermits(permits);
        log.info("Initialized Redisson Semaphore with {} permits", permits);
    }

    public void initStock(Long eventId, int stock) {
        if (eventId != null) {
            RAtomicLong stockCounter = redissonClient.getAtomicLong(STOCK_KEY_PREFIX + eventId);
            stockCounter.set(stock);
            log.info("Initialized Redis stock for event {} with {} tickets", eventId, stock);
        }
    }

    public boolean tryAcquire(long timeout, TimeUnit unit) throws InterruptedException {
        waitingRequests.incrementAndGet();
        try {
            RSemaphore semaphore = redissonClient.getSemaphore(SEMAPHORE_KEY);
            return semaphore.tryAcquire(timeout, unit);
        } finally {
            waitingRequests.decrementAndGet();
        }
    }

    public void release() {
        RSemaphore semaphore = redissonClient.getSemaphore(SEMAPHORE_KEY);
        semaphore.release();
    }

    public boolean tryReserveStock(Long eventId) {
        if (eventId == null) return false;
        RAtomicLong stockCounter = redissonClient.getAtomicLong(STOCK_KEY_PREFIX + eventId);
        long remaining = stockCounter.decrementAndGet();
        if (remaining < 0) {
            stockCounter.incrementAndGet(); // rollback atomic counter
            return false;
        }
        return true;
    }

    public void recordSession(Long eventId, String userId, String status, Duration ttl) {
        if (eventId != null && userId != null) {
            RBucket<String> session = redissonClient.getBucket(SESSION_KEY_PREFIX + eventId + ":" + userId);
            session.set(status, ttl);
        }
    }

    public String getSession(Long eventId, String userId) {
        if (eventId == null || userId == null) return null;
        RBucket<String> session = redissonClient.getBucket(SESSION_KEY_PREFIX + eventId + ":" + userId);
        return session.get();
    }

    public int getAvailablePermits() {
        RSemaphore semaphore = redissonClient.getSemaphore(SEMAPHORE_KEY);
        return semaphore.availablePermits();
    }

    public int getQueueLength() {
        return Math.max(0, this.waitingRequests.get());
    }

    public int getTotalPermits() {
        return this.totalPermits.get();
    }

    public int getActivePermits() {
        int available = getAvailablePermits();
        int total = getTotalPermits();
        return Math.max(0, total - available);
    }
}

