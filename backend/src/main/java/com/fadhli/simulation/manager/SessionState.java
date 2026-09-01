package com.fadhli.simulation.manager;

/**
 * Langkah yang sedang dijalani sebuah sesi pembelian.
 *
 * <p>Tiga keadaan pertama berarti sesi masih hidup dan masih memegang satu slot. Sisanya keadaan
 * akhir: slot sudah dilepas dan sesi tinggal menunggu kedaluwarsa untuk dibersihkan Redis.
 */
public enum SessionState {

    /** Sudah dapat slot, sedang memilih tiket. Belum menyentuh stok. */
    SELECTING(true),

    /** Stok sudah ditahan atas nama sesi ini, sedang menunggu hasil pembayaran. */
    PAYING(true),

    /** Pembayaran berhasil, tinggal dicatat sebagai penjualan di basis data. */
    CONFIRMING(true),

    /** Tiket terbit. */
    DONE(false),

    /** Kalah cepat: stok sudah habis saat sesi ini hendak memesan. */
    OUT_OF_STOCK(false),

    /** Pembayaran ditolak. Stok yang ditahan dikembalikan. */
    PAYMENT_FAILED(false),

    /** Sesi tidak pernah dilanjutkan sampai tenggatnya lewat, lalu dibereskan reaper. */
    ABANDONED(false);

    private final boolean holdingSlot;

    SessionState(boolean holdingSlot) {
        this.holdingSlot = holdingSlot;
    }

    /** Benar bila sesi pada keadaan ini masih memegang slot. */
    public boolean holdingSlot() {
        return holdingSlot;
    }

    /** Benar bila stok sudah ditahan atas nama sesi ini dan harus dikembalikan kalau gagal. */
    public boolean holdingStock() {
        return this == PAYING || this == CONFIRMING;
    }

    public static SessionState of(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
