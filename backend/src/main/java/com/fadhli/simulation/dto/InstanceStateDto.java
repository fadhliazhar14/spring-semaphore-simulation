package com.fadhli.simulation.dto;

/**
 * Keadaan satu instance aplikasi seperti yang ditampilkan papan observasi.
 *
 * @param id       identitas instance, misalnya {@code inst-8081}
 * @param alive    benar bila detak jantungnya masih terdengar
 * @param silentMs sudah berapa lama instance ini tidak berdetak
 * @param self     benar bila instance inilah yang menyusun snapshot ini
 * @param usage    pemakaian sumber daya terakhirnya, {@code null} bila belum tercatat
 */
public record InstanceStateDto(String id, boolean alive, long silentMs, boolean self,
                               ResourceUsageDto usage) {
}
