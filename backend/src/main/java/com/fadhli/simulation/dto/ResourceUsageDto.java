package com.fadhli.simulation.dto;

/**
 * Pemakaian sumber daya satu instance, seperti yang digambar papan observasi.
 *
 * @param heapUsedBytes memori heap terpakai
 * @param heapMaxBytes  batas atas heap
 * @param cpuPercent    beban CPU proses, negatif bila pengukurnya tidak tersedia
 * @param threadCount   jumlah thread hidup
 */
public record ResourceUsageDto(long heapUsedBytes, long heapMaxBytes, double cpuPercent,
                               int threadCount) {
}
