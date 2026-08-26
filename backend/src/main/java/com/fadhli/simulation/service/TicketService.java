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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);
    private static final long DEFAULT_TIMEOUT_MS = 20000;

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
    private volatile boolean delayEnabled = true;

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
        totalRequests.set(0);
        successRequests.set(0);
        failedOutOfStock.set(0);
        failedTimeout.set(0);

        semaphoreManager.init(semaphorePermits);

        TicketEvent event = new TicketEvent(eventName, initialStock, initialStock);
        TicketEvent savedEvent = ticketEventRepository.save(event);
        this.currentEventId = savedEvent.getId();
        this.currentTotalTickets = initialStock;

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
            // 2. Acquire permit dengan timeout
            acquired = semaphoreManager.tryAcquire(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!acquired) {
                failedTimeout.incrementAndGet();
                long duration = System.currentTimeMillis() - startTime;
                broadcastCurrentStatus("User " + userId + " gagal karena timeout antrean");
                return TicketPurchaseResultDto.timeout(userId, eventId, duration);
            }

            // Simulasi proses bisnis (misal pemrosesan pembayaran/alokasi) jika delay diaktifkan
            if (delayEnabled) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }

            // 3. Kurangi stok tiket secara atomik di database via transactional proxy method
            boolean success = self.processDatabaseTransaction(eventId);
            long duration = System.currentTimeMillis() - startTime;

            if (success) {
                successRequests.incrementAndGet();
                TicketEvent event = ticketEventRepository.findById(eventId).orElse(null);
                int remaining = event != null ? event.getAvailableTickets() : 0;
                broadcastCurrentStatus("User " + userId + " berhasil mendapatkan tiket");
                return TicketPurchaseResultDto.success(userId, eventId, remaining, duration);
            } else {
                failedOutOfStock.incrementAndGet();
                broadcastCurrentStatus("User " + userId + " gagal, stok tiket habis");
                return TicketPurchaseResultDto.outOfStock(userId, eventId, duration);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long duration = System.currentTimeMillis() - startTime;
            failedTimeout.incrementAndGet();
            broadcastCurrentStatus("User " + userId + " terinterupsi saat menunggu");
            return TicketPurchaseResultDto.error(userId, eventId, "Request interrupted", duration);
        } finally {
            // 4. Release permit
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

        Runtime runtime = Runtime.getRuntime();
        long usedMemoryBytes = runtime.totalMemory() - runtime.freeMemory();
        double memoryUsageMb = Math.round(((double) usedMemoryBytes / (1024.0 * 1024.0)) * 100.0) / 100.0;

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
                "Status update",
                memoryUsageMb
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

    public boolean isDelayEnabled() {
        return delayEnabled;
    }

    public void setDelayEnabled(boolean delayEnabled) {
        this.delayEnabled = delayEnabled;
    }
}
