package com.fadhli.simulation.manager;

/**
 * Konfigurasi satu simulasi. Bersifat tetap sepanjang umur sebuah {@code simId}: mengubah
 * parameter berarti membuat simulasi baru dengan simId baru, bukan memutasi yang sedang berjalan.
 * Karena tetap, nilainya aman untuk di-cache di memori tiap instance.
 */
public record SimulationConfig(
        String simId,
        Long eventId,
        int permits,
        int totalTickets,
        int thinkTimeMs,
        int paymentSuccessPercent
) {
}
