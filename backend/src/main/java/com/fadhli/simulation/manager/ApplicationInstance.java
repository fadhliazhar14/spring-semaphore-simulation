package com.fadhli.simulation.manager;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Identitas instance aplikasi ini di antara instance lain yang berbagi Redis yang sama.
 *
 * <p>Dipakai untuk mencatat instance mana yang sedang memegang tiap slot. Tanpa catatan itu papan
 * observasi hanya bisa menunjukkan bahwa batas konkurensi ditaati, tetapi tidak bisa menunjukkan
 * hal yang justru ingin dilihat: bahwa batas itu ditaati <em>bersama</em>, dan bahwa slot milik
 * instance yang mati benar-benar berpindah ke instance yang masih hidup.
 */
@Component
public class ApplicationInstance {

    private final String id;

    public ApplicationInstance(@Value("${simulation.instance-id:}") String configuredId,
                               @Value("${server.port:8080}") String port) {
        this.id = (configuredId == null || configuredId.isBlank()) ? "inst-" + port : configuredId;
    }

    public String id() {
        return id;
    }
}
