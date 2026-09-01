package com.fadhli.simulation.service;

import com.fadhli.simulation.dto.InstanceStateDto;
import com.fadhli.simulation.dto.ResourceUsageDto;
import com.fadhli.simulation.dto.SimulationStatusDto;
import com.fadhli.simulation.dto.SlotStateDto;
import com.fadhli.simulation.manager.ApplicationInstance;
import com.fadhli.simulation.manager.InstanceRegistry;
import com.fadhli.simulation.manager.SemaphoreManager;
import com.fadhli.simulation.manager.SimulationConfig;
import com.fadhli.simulation.manager.SlotLease;
import com.fadhli.simulation.manager.PurchaseSession;
import com.fadhli.simulation.manager.SlotOwner;
import com.fadhli.simulation.manager.SimulationStateStore;
import com.fadhli.simulation.model.TicketEvent;
import com.fadhli.simulation.repository.TicketEventRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class TicketService {

    private static final int DEFAULT_THINK_TIME_MS = 300;
    private static final int DEFAULT_PAYMENT_SUCCESS_PERCENT = 90;

    private final TicketEventRepository ticketEventRepository;
    private final SemaphoreManager semaphoreManager;
    private final SimulationStateStore store;
    private final SseService sseService;
    private final ApplicationInstance instance;
    private final InstanceRegistry instanceRegistry;
    private final TicketService self;

    public TicketService(TicketEventRepository ticketEventRepository,
                         SemaphoreManager semaphoreManager,
                         SimulationStateStore store,
                         SseService sseService,
                         ApplicationInstance instance,
                         InstanceRegistry instanceRegistry,
                         @Lazy TicketService self) {
        this.ticketEventRepository = ticketEventRepository;
        this.semaphoreManager = semaphoreManager;
        this.store = store;
        this.sseService = sseService;
        this.instance = instance;
        this.instanceRegistry = instanceRegistry;
        this.self = (self != null) ? self : this;
    }

    @Transactional
    public TicketEvent initSimulation(String eventName, int initialStock, int semaphorePermits) {
        return initSimulation(eventName, initialStock, semaphorePermits,
                DEFAULT_THINK_TIME_MS, DEFAULT_PAYMENT_SUCCESS_PERCENT);
    }

    @Transactional
    public TicketEvent initSimulation(String eventName, int initialStock, int semaphorePermits,
                                      int thinkTimeMs, int paymentSuccessPercent) {
        TicketEvent savedEvent = ticketEventRepository.save(
                new TicketEvent(eventName, initialStock, initialStock));

        SimulationConfig cfg = store.createSimulation(
                savedEvent.getId(), initialStock, semaphorePermits,
                thinkTimeMs, paymentSuccessPercent);
        semaphoreManager.initSemaphore(cfg.simId(), cfg.permits());
        semaphoreManager.initStock(cfg.simId(), initialStock);

        broadcastCurrentStatus(cfg, "Simulasi berhasil diinisialisasi");
        return savedEvent;
    }

    /**
     * Menambah stok tiket di tengah simulasi.
     *
     * <p>Urutannya disengaja: basis data lebih dulu, baru kuota cepat di Redis. Redis adalah
     * penjaga di depan yang menolak permintaan saat stok habis, sementara basis data adalah
     * kebenaran terakhir. Menaikkan Redis lebih dulu berarti ada jendela waktu ketika request
     * dilewatkan oleh penjaga padahal barangnya belum ada di basis data.
     */
    @Transactional
    public int restock(String simId, int amount) {
        SimulationConfig cfg = store.config(simId);
        if (cfg == null || amount <= 0) {
            return 0;
        }
        ticketEventRepository.restock(cfg.eventId(), amount);
        semaphoreManager.addStock(simId, amount);
        store.increaseBy(simId, SimulationStateStore.METRIC_RESTOCKED, amount);
        sseService.activity("Stok ditambah " + amount + " tiket");
        return amount;
    }

    public String getCurrentSimId() {
        return store.currentSimId();
    }

    public SimulationStatusDto getCurrentStatus() {
        SimulationConfig cfg = store.config(store.currentSimId());
        if (cfg == null) {
            return new SimulationStatusDto(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, "Belum ada simulasi");
        }
        return buildStatus(cfg, "Status update");
    }

    private SimulationStatusDto buildStatus(SimulationConfig cfg, String message) {
        int availableTickets = ticketEventRepository.findById(cfg.eventId())
                .map(TicketEvent::getAvailableTickets)
                .orElse(0);
        SimulationStateStore.Metrics m = store.metrics(cfg.simId());

        // Kapasitas yang dilaporkan adalah stok awal ditambah yang disuntikkan di tengah jalan,
        // supaya perbandingan terjual terhadap total tidak menjadi lebih dari seratus persen.
        int totalCapacity = cfg.totalTickets()
                + (int) store.metric(cfg.simId(), SimulationStateStore.METRIC_RESTOCKED);

        SimulationStatusDto status = new SimulationStatusDto(
                availableTickets,
                totalCapacity,
                semaphoreManager.getActivePermits(cfg.simId()),
                cfg.permits(),
                m.total(),
                m.success(),
                m.outOfStock(),
                m.rejected(),
                m.paymentFailed(),
                m.abandoned(),
                m.gaveUp(),
                message);
        status.setSlots(buildSlots(cfg));
        status.setSessionLeaseMs(SessionService.sessionLease(cfg).toMillis());
        status.setReportedBy(instance.id());
        status.setInstances(instanceRegistry.instances().stream()
                .map(h -> new InstanceStateDto(h.id(), h.alive(), h.silentMs(), h.self(),
                        h.usage() == null ? null : new ResourceUsageDto(
                                h.usage().heapUsedBytes(), h.usage().heapMaxBytes(),
                                h.usage().cpuPercent(), h.usage().threadCount())))
                .toList());
        return status;
    }

    /**
     * Menyusun keadaan tiap slot, termasuk slot yang sedang kosong.
     *
     * <p>Slot kosong tetap dikirim supaya papan observasi punya jumlah kotak yang tetap dan tidak
     * berubah bentuk tiap kali ada yang mengambil atau melepas slot.
     */
    private List<SlotStateDto> buildSlots(SimulationConfig cfg) {
        Map<String, SlotOwner> owners = semaphoreManager.getSlotOwners(cfg.simId());
        Map<String, Long> deadlines = semaphoreManager.getSlotLeaseDeadlines(cfg.simId());
        // Langkah yang sedang dijalani hanya tercatat di sesi. Seluruh sesi yang sedang memegang
        // slot dibaca sekaligus supaya satu snapshot tidak berubah menjadi N perjalanan ke Redis.
        Map<String, PurchaseSession> sessions = semaphoreManager.getSessions(cfg.simId(),
                owners.values().stream().map(SlotOwner::userId).toList());
        long now = System.currentTimeMillis();

        List<SlotStateDto> slots = new ArrayList<>(cfg.permits());
        for (int number = 1; number <= cfg.permits(); number++) {
            String token = SlotLease.token(number);
            SlotOwner owner = owners.get(token);
            if (owner == null) {
                slots.add(SlotStateDto.idle(number));
                continue;
            }
            PurchaseSession session = sessions.get(owner.userId());
            String phase = (session == null) ? "?" : session.state().name();
            long deadline = deadlines.getOrDefault(token, now);
            slots.add(new SlotStateDto(token, number, true, owner.userId(), phase,
                    owner.instanceId(), now - owner.acquiredAt(), deadline - now));
        }
        return slots;
    }

    /** Snapshot lengkap simulasi yang sedang aktif, atau {@code null} bila belum ada. */
    SimulationStatusDto currentSnapshot() {
        SimulationConfig cfg = store.config(store.currentSimId());
        return (cfg == null) ? null : buildStatus(cfg, null);
    }

    private void broadcastCurrentStatus(SimulationConfig cfg, String message) {
        sseService.broadcast(buildStatus(cfg, message));
    }
}
