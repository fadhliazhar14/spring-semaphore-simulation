package com.fadhli.simulation.controller;

import com.fadhli.simulation.dto.InitSimulationRequest;
import com.fadhli.simulation.dto.SimulationStatusDto;
import com.fadhli.simulation.model.TicketEvent;
import com.fadhli.simulation.service.SseService;
import com.fadhli.simulation.manager.PurchaseSession;
import com.fadhli.simulation.service.SessionService;
import com.fadhli.simulation.service.TicketService;
import com.fadhli.simulation.service.WarTrafficGenerator;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;


@RestController
@RequestMapping("/api")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class TicketController {

    private final TicketService ticketService;
    private final SseService sseService;
    private final WarTrafficGenerator trafficGenerator;
    private final SessionService sessionService;

    public TicketController(TicketService ticketService, SseService sseService,
                            WarTrafficGenerator trafficGenerator, SessionService sessionService) {
        this.ticketService = ticketService;
        this.sseService = sseService;
        this.trafficGenerator = trafficGenerator;
        this.sessionService = sessionService;
    }

    @PostMapping("/simulation/init")
    public ResponseEntity<TicketEvent> initSimulation(@RequestBody InitSimulationRequest request) {
        String name = (request.getEventName() != null && !request.getEventName().isBlank())
                ? request.getEventName()
                : "War Ticket Semaphore Simulation";
        int stock = request.getTotalTickets() > 0 ? request.getTotalTickets() : 100;
        int permits = request.getSemaphorePermits() > 0 ? request.getSemaphorePermits() : 5;
        int thinkTimeMs = request.getThinkTimeMs() >= 0 ? request.getThinkTimeMs() : 300;
        int paySuccess = request.getPaymentSuccessPercent();

        TicketEvent event = ticketService.initSimulation(name, stock, permits, thinkTimeMs, paySuccess);
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

    @PostMapping("/simulation/restock")
    public ResponseEntity<String> restock(@RequestParam(defaultValue = "50") int amount) {
        String simId = ticketService.getCurrentSimId();
        if (simId == null) {
            return ResponseEntity.badRequest().body("Inisialisasi simulasi terlebih dahulu.");
        }
        if (amount <= 0) {
            return ResponseEntity.badRequest().body("Jumlah stok harus lebih dari 0.");
        }
        ticketService.restock(simId, amount);
        return ResponseEntity.ok("Stok ditambah " + amount + " tiket.");
    }

    @PostMapping("/simulation/traffic")
    public ResponseEntity<String> triggerTraffic(
            @RequestParam(defaultValue = "300") int requestCount,
            @RequestParam(defaultValue = "5") int maxAttempts,
            @RequestParam(defaultValue = "300") int retryDelayMs) {
        // simId diselesaikan sekali di sini, bukan sekali per request, agar tiap percobaan tidak
        // membayar satu perjalanan ke Redis hanya untuk mencari tahu simulasi mana yang aktif.
        String simId = ticketService.getCurrentSimId();
        if (simId == null) {
            return ResponseEntity.badRequest().body("Inisialisasi simulasi terlebih dahulu.");
        }

        trafficGenerator.releaseWave(simId, requestCount, maxAttempts, retryDelayMs);
        return ResponseEntity.ok(requestCount + " pembeli dilepas, maksimal "
                + maxAttempts + " percobaan per pembeli.");
    }

    /**
     * Memulai satu sesi pembelian. Terbuka supaya sebuah sesi bisa dijalankan dengan tangan,
     * langkah demi langkah, sambil menonton papannya bergerak.
     */
    @PostMapping("/session/start")
    public ResponseEntity<?> startSession(@RequestParam String userId) {
        String simId = ticketService.getCurrentSimId();
        if (simId == null) {
            return ResponseEntity.badRequest().body("Inisialisasi simulasi terlebih dahulu.");
        }
        PurchaseSession session = sessionService.start(simId, userId);
        if (session == null) {
            // Ditolak, bukan diantrekan. Silakan coba lagi.
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Semua slot terpakai, coba lagi.");
        }
        return ResponseEntity.ok(session);
    }

    /** Memajukan sesi satu langkah. */
    @PostMapping("/session/advance")
    public ResponseEntity<?> advanceSession(@RequestParam String userId) {
        String simId = ticketService.getCurrentSimId();
        if (simId == null) {
            return ResponseEntity.badRequest().body("Inisialisasi simulasi terlebih dahulu.");
        }
        PurchaseSession session = sessionService.advance(simId, userId);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Sesi tidak ditemukan atau sudah kedaluwarsa.");
        }
        return ResponseEntity.ok(session);
    }
}
