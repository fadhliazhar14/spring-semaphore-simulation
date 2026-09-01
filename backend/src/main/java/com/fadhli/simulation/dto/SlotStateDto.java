package com.fadhli.simulation.dto;

import com.fadhli.simulation.manager.SlotLease;

/**
 * Keadaan satu slot konkurensi seperti yang digambar papan observasi.
 *
 * <p>Ini isi yang tidak bisa diberikan sebuah semaphore biasa. {@code availablePermits()} hanya
 * menjawab "berapa banyak", sedangkan yang perlu diamati adalah "siapa, sejak kapan, di instance
 * mana, dan berapa lama lagi haknya berlaku".
 *
 * @param slot             token slot, misalnya {@code slot-3}
 * @param number           nomor urut slot, dipakai untuk mengurutkan tampilan
 * @param occupied         benar bila slot sedang dipegang seseorang
 * @param userId           pemegang slot, {@code null} bila kosong
 * @param phase            fase proses yang sedang dijalani pemegangnya
 * @param instanceId       instance aplikasi yang memegang slot ini
 * @param heldForMs        sudah berapa lama slot ini dipegang
 * @param leaseRemainingMs sisa waktu sebelum reaper berhak merebut slot ini; negatif berarti
 *                         tenggatnya sudah lewat dan slot sedang menunggu disapu
 */
public record SlotStateDto(String slot, int number, boolean occupied, String userId, String phase,
                           String instanceId, long heldForMs, long leaseRemainingMs) {

    public static SlotStateDto idle(int number) {
        return new SlotStateDto(SlotLease.token(number), number, false, null, null, null, 0, 0);
    }
}
