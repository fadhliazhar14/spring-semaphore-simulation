package com.fadhli.simulation.service;

import com.fadhli.simulation.dto.SimulationStatusDto;
import com.fadhli.simulation.dto.TicketPurchaseResultDto;
import com.fadhli.simulation.manager.SemaphoreManager;
import com.fadhli.simulation.model.TicketEvent;
import com.fadhli.simulation.repository.TicketEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TicketConcurrencyIntegrationTest {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private SemaphoreManager semaphoreManager;

    @Autowired
    private TicketEventRepository ticketEventRepository;

    @Test
    @DisplayName("Should maintain consistent ticket metrics and database state under high concurrency")
    void testConcurrentTicketPurchases() throws InterruptedException {
        int initialStock = 50;
        int semaphorePermits = 10;
        int totalRequests = 100;

        TicketEvent event = ticketService.initSimulation("Konser Musik", initialStock, semaphorePermits);
        Long eventId = event.getId();

        ExecutorService executor = Executors.newFixedThreadPool(25);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalRequests);

        List<TicketPurchaseResultDto> results = Collections.synchronizedList(new ArrayList<>());

        for (int i = 1; i <= totalRequests; i++) {
            final String userId = "user-" + String.format("%03d", i);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    TicketPurchaseResultDto result = ticketService.purchaseTicket(eventId, userId);
                    results.add(result);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Start all threads simultaneously
        startLatch.countDown();
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All requests should complete within 30 seconds");

        // Verify metrics vs database state
        SimulationStatusDto status = ticketService.getCurrentStatus();
        TicketEvent finalEvent = ticketEventRepository.findById(eventId).orElseThrow();

        long successCount = results.stream().filter(r -> r.getStatus() == TicketPurchaseResultDto.Status.SUCCESS).count();
        long outOfStockCount = results.stream().filter(r -> r.getStatus() == TicketPurchaseResultDto.Status.FAILED_OUT_OF_STOCK).count();
        long timeoutCount = results.stream().filter(r -> r.getStatus() == TicketPurchaseResultDto.Status.FAILED_TIMEOUT).count();
        long errorCount = results.stream().filter(r -> r.getStatus() == TicketPurchaseResultDto.Status.ERROR).count();

        assertEquals(initialStock, successCount, "Successful purchases should match initial stock");
        assertEquals(totalRequests - initialStock, outOfStockCount, "Out of stock purchases should match excess requests");
        assertEquals(0, finalEvent.getAvailableTickets(), "Database available tickets should be 0");
        assertEquals(0, status.getAvailableTickets(), "Status DTO available tickets should match database");
        assertEquals(initialStock, status.getSuccessRequests(), "Status successRequests count should be correct");
        assertEquals(totalRequests - initialStock, status.getFailedOutOfStock(), "Status failedOutOfStock count should be correct");
        assertEquals(0, status.getActivePermits(), "All permits should be released");

        // Verify session recorded in Redis for successful users
        results.stream()
                .filter(r -> r.getStatus() == TicketPurchaseResultDto.Status.SUCCESS)
                .forEach(r -> {
                    String sessionStatus = semaphoreManager.getSession(eventId, r.getUserId());
                    assertEquals("PURCHASED", sessionStatus, "Successful user session must be PURCHASED in Redis");
                });
    }
}
