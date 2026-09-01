package com.fadhli.simulation.manager;

/**
 * Pembangun key Redis untuk satu simulasi.
 *
 * <p>Seluruh key milik sebuah simulasi berada di bawah awalan {@code sim:{simId}}. Ini disengaja:
 * versi sebelumnya memakai satu key global untuk semaphore, sehingga instance yang baru menyala
 * ikut menghapus state simulasi yang sedang berjalan di instance lain. Dengan key ber-simId,
 * inisialisasi simulasi baru tidak pernah menyentuh key milik simulasi yang masih hidup.
 */
public final class SimulationKeys {

    /** Menunjuk simId yang sedang aktif, dibaca oleh semua instance. */
    public static final String CURRENT = "sim:current";

    private final String base;

    public SimulationKeys(String simId) {
        if (simId == null || simId.isBlank()) {
            throw new IllegalArgumentException("simId must not be blank");
        }
        this.base = "sim:" + simId;
    }

    public String config() {
        return base + ":config";
    }

    /** Antrean token slot. Mengambil satu token setara dengan mengambil satu permit. */
    public String slots() {
        return base + ":slots";
    }

    /** Hash slot menuju pemiliknya, memakai penyandian {@link SlotOwner}. */
    public String slotOwner() {
        return base + ":slot:owner";
    }

    /** Sorted set slot menuju tenggat lease dalam epoch milidetik. Sumber data reaper. */
    public String slotLease() {
        return base + ":slot:lease";
    }

    public String stock() {
        return base + ":stock";
    }

    public String metric(String name) {
        return base + ":metric:" + name;
    }

    public String session(String userId) {
        return base + ":session:" + userId;
    }
}
