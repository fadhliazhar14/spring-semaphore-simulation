package com.fadhli.simulation.manager;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;

/**
 * Cuplikan pemakaian sumber daya satu instance aplikasi.
 *
 * <p>Ini yang menjawab pertanyaan yang tidak bisa dijawab papan slot: berapa mahal sebenarnya
 * perang ini bagi mesin yang melayaninya. Batas konkurensi dipasang justru untuk menjaga angka
 * inilah, jadi tanpa menampilkannya, seluruh simulasi cuma menunjukkan akibat tanpa sebab.
 *
 * @param heapUsedBytes memori heap yang sedang terpakai
 * @param heapMaxBytes  batas atas heap; nol berarti tidak dibatasi
 * @param cpuPercent    beban CPU proses ini, 0 sampai 100, atau negatif bila tidak tersedia
 * @param threadCount   jumlah thread hidup di JVM ini
 */
public record ResourceUsage(long heapUsedBytes, long heapMaxBytes, double cpuPercent,
                            int threadCount) {

    private static final String SEPARATOR = "|";
    private static final int FIELD_COUNT = 4;

    public static ResourceUsage sample() {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        return new ResourceUsage(
                heap.getUsed(),
                Math.max(0, heap.getMax()),
                processCpuPercent(),
                ManagementFactory.getThreadMXBean().getThreadCount());
    }

    /**
     * Beban CPU proses ini. Pengukurnya hanya ada di JVM keluarga HotSpot dan mengembalikan nilai
     * negatif pada pemanggilan pertama sebelum ada dua titik ukur, jadi ketidaktersediaannya
     * diteruskan apa adanya sebagai negatif alih-alih dipalsukan menjadi nol.
     */
    private static double processCpuPercent() {
        if (ManagementFactory.getOperatingSystemMXBean()
                instanceof com.sun.management.OperatingSystemMXBean os) {
            double load = os.getProcessCpuLoad();
            return load < 0 ? -1 : load * 100;
        }
        return -1;
    }

    public String encode() {
        return String.join(SEPARATOR,
                String.valueOf(heapUsedBytes), String.valueOf(heapMaxBytes),
                String.valueOf(cpuPercent), String.valueOf(threadCount));
    }

    public static ResourceUsage decode(String raw) {
        if (raw == null) {
            return null;
        }
        String[] parts = raw.split("\\|", FIELD_COUNT);
        if (parts.length < FIELD_COUNT) {
            return null;
        }
        try {
            return new ResourceUsage(Long.parseLong(parts[0]), Long.parseLong(parts[1]),
                    Double.parseDouble(parts[2]), Integer.parseInt(parts[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
