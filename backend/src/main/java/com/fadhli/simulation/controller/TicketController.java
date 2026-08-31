package com.fadhli.simulation.controller;

import com.fadhli.simulation.dto.InitSimulationRequest;
import com.fadhli.simulation.dto.SimulationStatusDto;
import com.fadhli.simulation.model.TicketEvent;
import com.fadhli.simulation.service.SseService;
import com.fadhli.simulation.service.TicketService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class TicketController {

    private final TicketService ticketService;
    private final SseService sseService;
    private final ExecutorService batchExecutor = Executors.newFixedThreadPool(50);

    public TicketController(TicketService ticketService, SseService sseService) {
        this.ticketService = ticketService;
        this.sseService = sseService;
    }

    @PostMapping("/simulation/init")
    public ResponseEntity<TicketEvent> initSimulation(@RequestBody InitSimulationRequest request) {
        String name = (request.getEventName() != null && !request.getEventName().isBlank())
                ? request.getEventName()
                : "War Ticket Semaphore Simulation";
        int stock = request.getTotalTickets() > 0 ? request.getTotalTickets() : 100;
        int permits = request.getSemaphorePermits() > 0 ? request.getSemaphorePermits() : 5;
        int delayMs = request.getProcessingDelayMs() >= 0 ? request.getProcessingDelayMs() : 30;

        TicketEvent event = ticketService.initSimulation(name, stock, permits);
        if (request.getUseDelay() != null) {
            ticketService.setDelayEnabled(request.getUseDelay());
        }
        return ResponseEntity.ok(event);
    }

    @GetMapping(value = "/simulation/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamStatus() {
        return sseService.subscribe();
    }

    @GetMapping("/simulation/status")
    public ResponseEntity<SimulationStatusDto> getStatus() {
        return ResponseEntity.ok(ticketService.getCurrentStatus());
    }

    @PostMapping("/simulation/traffic")
    public ResponseEntity<String> triggerTraffic(
            @RequestParam(defaultValue = "100") int requestCount,
            @RequestParam(defaultValue = "true") boolean useDelay) {
        Long eventId = ticketService.getCurrentEventId();
        if (eventId == null) {
            return ResponseEntity.badRequest().body("Inisialisasi simulasi terlebih dahulu.");
        }

        ticketService.setDelayEnabled(useDelay);

        CompletableFuture.runAsync(() -> {
            for (int i = 1; i <= requestCount; i++) {
                final String userId = "bot-" + String.format("%04d", i);
                batchExecutor.submit(() -> ticketService.purchaseTicket(eventId, userId));
            }
        });

        return ResponseEntity.ok("Traffic simulation with " + requestCount + " requests triggered!");
    }
}
