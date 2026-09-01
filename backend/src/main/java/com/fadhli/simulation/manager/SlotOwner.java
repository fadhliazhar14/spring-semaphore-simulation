package com.fadhli.simulation.manager;

/**
 * Isi satu entri pada hash {@code sim:{id}:slot:owner}.
 *
 * <p>Langkah bisnis sengaja tidak dicatat di sini melainkan di {@link PurchaseSession}. Satu
 * fakta yang disimpan di dua tempat pada akhirnya selalu tidak sinkron, dan yang berhak menjawab
 * "sedang di langkah mana" adalah sesinya, bukan catatan kepemilikan slot.
 *
 * <p>Disandikan menjadi satu string {@code fencingToken|acquiredAt|instanceId|userId}
 * alih-alih JSON, karena skrip Lua pelepasan slot harus bisa membaca fencing token-nya sendiri
 * tanpa mengurai JSON di dalam Redis. Fencing token berupa UUID dan {@code acquiredAt} berupa
 * angka, jadi keduanya dijamin tidak mengandung pemisah; {@code userId} ditaruh paling belakang
 * supaya isinya bebas apa pun.
 */
public record SlotOwner(String fencingToken, long acquiredAt, String instanceId, String userId) {

    private static final String SEPARATOR = "|";
    private static final int FIELD_COUNT = 4;

    public String encode() {
        return String.join(SEPARATOR,
                fencingToken, String.valueOf(acquiredAt), instanceId, userId);
    }

    /**
     * Mengurai entri owner, atau {@code null} kalau bentuknya tidak dikenali. Entri sisa simulasi
     * lama dengan bentuk berbeda sengaja diabaikan diam-diam alih-alih melempar exception: isinya
     * hanya bahan tampilan, dan simulasi yang sedang berjalan tidak boleh gagal karenanya.
     */
    public static SlotOwner decode(String raw) {
        if (raw == null) {
            return null;
        }
        String[] parts = raw.split("\\|", FIELD_COUNT);
        if (parts.length < FIELD_COUNT) {
            return null;
        }
        try {
            return new SlotOwner(parts[0], Long.parseLong(parts[1]), parts[2], parts[3]);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
