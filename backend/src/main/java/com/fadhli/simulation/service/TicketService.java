package com.fadhli.simulation.service;

import com.fadhli.simulation.dto.SimulationStatusDto;
import com.fadhli.simulation.dto.TicketPurchaseResultDto;
import com.fadhli.simulation.manager.SemaphoreManager;
import com.fadhli.simulation.model.TicketEvent;
import com.fadhli.simulation.repository.TicketEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);
    private static final long DEFAULT_TIMEOUT_MS = 2000;

    private final TicketEventRepository ticketEventRepository;
    private final SemaphoreManager semaphoreManager;
    private final SseService sseService;
    private final TicketService self;

    private final AtomicInteger totalRequests = new AtomicInteger(0);
    private final AtomicInteger successRequests = new AtomicInteger(0);
    private final AtomicInteger failedOutOfStock = new AtomicInteger(0);
    private final AtomicInteger failedTimeout = new AtomicInteger(0);

    private volatile Long currentEventId;
    private volatile int currentTotalTickets = 0;
    private volatile int processingDelayMs = 30;

    public TicketService(TicketEventRepository ticketEventRepository,
                         SemaphoreManager semaphoreManager,
                         SseService sseService,
                         @Lazy TicketService self) {
        this.ticketEventRepository = ticketEventRepository;
        this.semaphoreManager = semaphoreManager;
        this.sseService = sseService;
        this.self = (self != null) ? self : this;
    }

    @Transactional
    public TicketEvent initSimulation(String eventName, int initialStock, int semaphorePermits) {
        return initSimulation(eventName, initialStock, semaphorePermits, 30);
    }

    @Transactional
    public TicketEvent initSimulation(String eventName, int initialStock, int semaphorePermits, int processingDelayMs) {
        totalRequests.set(0);
        successRequests.set(0);
        failedOutOfStock.set(0);
        failedTimeout.set(0);

        this.processingDelayMs = Math.max(0, processingDelayMs);
        semaphoreManager.init(semaphorePermits);

        TicketEvent event = new TicketEvent(eventName, initialStock, initialStock);
        TicketEvent savedEvent = ticketEventRepository.save(event);
        this.currentEventId = savedEvent.getId();
        this.currentTotalTickets = initialStock;

        // Inisialisasi kuota stok tiket di Redis
        semaphoreManager.initStock(savedEvent.getId(), initialStock);

        broadcastCurrentStatus("Simulasi berhasil diinisialisasi");
        return savedEvent;
    }

    public TicketPurchaseResultDto purchaseTicket(Long eventId, String userId) {
        long startTime = System.currentTimeMillis();
        totalRequests.incrementAndGet();

        // 1. Broadcast status saat request masuk antrean
        broadcastCurrentStatus("User " + userId + " masuk antrean...");

        boolean acquired = false;
        try {
            // 2. Acquire permit dari Redisson Distributed Semaphore dengan timeout
            acquired = semaphoreManager.tryAcquire(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!acquired) {
                failedTimeout.incrementAndGet();
                long duration = System.currentTimeMillis() - startTime;
                broadcastCurrentStatus("User " + userId + " gagal karena timeout antrean");
                return TicketPurchaseResultDto.timeout(userId, eventId, duration);
            }

            // 3. Catat sesi aktif user di Redis (TTL 5 menit)
            semaphoreManager.recordSession(eventId, userId, "PROCESSING", Duration.ofMinutes(5));

            // Simulasi waktu proses bisnis (misal validasi pembayaran / antrean tiket)
            if (this.processingDelayMs > 0) {
                try {
                    Thread.sleep(this.processingDelayMs);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }

            // 4. Fast-check & decrement stok tiket di Redis secara atomik
            boolean stockReservedInRedis = semaphoreManager.tryReserveStock(eventId);
            if (!stockReservedInRedis) {
                failedOutOfStock.incrementAndGet();
                semaphoreManager.recordSession(eventId, userId, "FAILED_OUT_OF_STOCK", Duration.ofMinutes(5));
                long duration = System.currentTimeMillis() - startTime;
                broadcastCurrentStatus("User " + userId + " gagal, stok tiket habis (Redis fast-check)");
                return TicketPurchaseResultDto.outOfStock(userId, eventId, duration);
            }

            // 5. Kurangi stok tiket secara atomik di database (PostgreSQL source of truth)
            boolean success = self.processDatabaseTransaction(eventId);
            long duration = System.currentTimeMillis() - startTime;

            if (success) {
                successRequests.incrementAndGet();
                semaphoreManager.recordSession(eventId, userId, "PURCHASED", Duration.ofMinutes(15));
                TicketEvent event = ticketEventRepository.findById(eventId).orElse(null);
                int remaining = event != null ? event.getAvailableTickets() : 0;
                broadcastCurrentStatus("User " + userId + " berhasil mendapatkan tiket");
                return TicketPurchaseResultDto.success(userId, eventId, remaining, duration);
            } else {
                failedOutOfStock.incrementAndGet();
                semaphoreManager.recordSession(eventId, userId, "FAILED_OUT_OF_STOCK", Duration.ofMinutes(5));
                broadcastCurrentStatus("User " + userId + " gagal, stok tiket habis (DB)");
                return TicketPurchaseResultDto.outOfStock(userId, eventId, duration);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long duration = System.currentTimeMillis() - startTime;
            failedTimeout.incrementAndGet();
            broadcastCurrentStatus("User " + userId + " terinterupsi saat menunggu");
            return TicketPurchaseResultDto.error(userId, eventId, "Request interrupted", duration);
        } finally {
            // 6. Release permit di Redisson Semaphore
            if (acquired) {
                semaphoreManager.release();
                broadcastCurrentStatus("Permit dilepaskan setelah melayani " + userId);
            }
        }
    }

    @Transactional
    public boolean processDatabaseTransaction(Long eventId) {
        return ticketEventRepository.decrementTicketStock(eventId) > 0;
    }

    public SimulationStatusDto getCurrentStatus() {
        int availableTickets = 0;
        if (currentEventId != null) {
            availableTickets = ticketEventRepository.findById(currentEventId)
                    .map(TicketEvent::getAvailableTickets)
                    .orElse(0);
        }

        return new SimulationStatusDto(
                availableTickets,
                currentTotalTickets,
                semaphoreManager.getActivePermits(),
                semaphoreManager.getTotalPermits(),
                semaphoreManager.getQueueLength(),
                totalRequests.get(),
                successRequests.get(),
                failedOutOfStock.get(),
                failedTimeout.get(),
                "Status update"
        );
    }

    private void broadcastCurrentStatus(String message) {
        SimulationStatusDto status = getCurrentStatus();
        status.setMessage(message);
        sseService.broadcast(status);
    }

    public Long getCurrentEventId() {
        return currentEventId;
    }

    public int getCurrentTotalTickets() {
        return currentTotalTickets;
    }
}
