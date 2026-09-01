package com.fadhli.simulation.manager;

/**
 * Bukti kepemilikan satu slot konkurensi.
 *
 * <p>Menggantikan {@code boolean} yang dulu dikembalikan saat mengambil permit. Semaphore biasa
 * hanya bisa menjawab "berapa banyak yang sedang jalan"; simulasi ini perlu menjawab "siapa
 * menempati slot nomor berapa", karena itulah yang ditampilkan papan observasi.
 *
 * @param simId        simulasi pemilik slot
 * @param slot         token slot, misalnya {@code slot-3}
 * @param fencingToken penanda unik satu masa kepemilikan. Pelepasan hanya sah kalau penandanya
 *                     masih cocok, sehingga pemilik lama yang slotnya sudah direbut reaper tidak
 *                     bisa ikut mengembalikan token dan menggelembungkan jumlah permit.
 */
public record SlotLease(String simId, String slot, String fencingToken) {

    /** Nama token untuk slot bernomor tertentu. Harus sama dengan yang ditulis skrip Lua. */
    public static String token(int number) {
        return "slot-" + number;
    }

    /** Nomor urut slot, dipakai papan observasi untuk menentukan baris mana yang menyala. */
    public int slotNumber() {
        int dash = slot.lastIndexOf('-');
        return dash < 0 ? -1 : Integer.parseInt(slot.substring(dash + 1));
    }
}
