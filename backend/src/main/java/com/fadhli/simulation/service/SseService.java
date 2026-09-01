package com.fadhli.simulation.service;

import com.fadhli.simulation.dto.SimulationStatusDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SseService {

    private static final Logger log = LoggerFactory.getLogger(SseService.class);
    private static final Long SSE_TIMEOUT = 30 * 60 * 1000L; // 30 minutes

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        emitter.onCompletion(() -> {
            log.info("SSE emitter completed");
            emitters.remove(emitter);
        });

        emitter.onTimeout(() -> {
            log.info("SSE emitter timed out");
            emitter.complete();
            emitters.remove(emitter);
        });

        emitter.onError((ex) -> {
            log.warn("SSE emitter error: {}", ex.getMessage());
            emitters.remove(emitter);
        });

        emitters.add(emitter);

        try {
            emitter.send(SseEmitter.event()
                    .name("INIT")
                    .data("Connected to Real-time War Ticket Simulation Stream"));
        } catch (IOException e) {
            log.error("Failed to send initial SSE event: {}", e.getMessage());
            emitters.remove(emitter);
        }

        return emitter;
    }

    public void broadcast(SimulationStatusDto status) {
        if (emitters.isEmpty()) {
            return;
        }

        send("STATUS_UPDATE", status);
    }

    private void send(String eventName, Object payload) {
        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (Exception e) {
                log.warn("Failed to send SSE event to client: {}", e.getMessage());
                deadEmitters.add(emitter);
            }
        }

        if (!deadEmitters.isEmpty()) {
            emitters.removeAll(deadEmitters);
        }
    }

    /**
     * Mengirim satu baris catatan aktivitas tanpa membawa snapshot apa pun.
     *
     * <p>Dipisahkan dari {@link #broadcast(SimulationStatusDto)} karena keduanya punya sifat yang
     * berbeda. Catatan aktivitas lahir tiap kali sesuatu terjadi dan isinya hanya sebaris teks,
     * sedangkan snapshot mahal — sekali susun berarti sekali baca ke Postgres dan beberapa kali ke
     * Redis. Dulu keduanya dikirim bersamaan, sehingga tiga ratus request menghasilkan hampir
     * seribu snapshot dan papan observasi justru tersendat oleh telemetrinya sendiri.
     */
    public void activity(String message) {
        if (message == null || emitters.isEmpty()) {
            return;
        }
        send("ACTIVITY", message);
    }

    public int getSubscriberCount() {
        return emitters.size();
    }
}
