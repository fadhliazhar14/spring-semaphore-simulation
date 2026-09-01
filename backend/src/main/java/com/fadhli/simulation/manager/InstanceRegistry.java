package com.fadhli.simulation.manager;

import org.redisson.api.RMap;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Daftar instance aplikasi yang sedang hidup, dengan detak jantung di Redis.
 *
 * <p>Ini bukan kebutuhan teknis — slot tetap terjaga benar tanpa daftar ini. Gunanya untuk
 * pengamatan: tanpa daftar instance, mematikan satu instance tidak menghasilkan apa pun yang
 * terlihat sampai reaper bekerja beberapa detik kemudian, sehingga sebab dan akibatnya sulit
 * dihubungkan. Dengan daftar ini, lencana instance padam lebih dulu, lalu slot-slotnya berubah
 * merah, lalu ditarik reaper. Urutan itulah yang menjelaskan kenapa lease perlu ada.
 *
 * <p>Sengaja tidak memakai TTL per-key. Sebuah ZSET dengan skor waktu terakhir terlihat bisa
 * dibaca sekali jalan untuk seluruh instance, dan memberi tahu bukan hanya siapa yang hidup
 * tetapi juga sudah berapa lama sebuah instance tidak terdengar.
 */
@Component
public class InstanceRegistry {

    /** Key global, bukan per simulasi: instance hidup lebih lama daripada satu simulasi. */
    private static final String KEY = "sim:instances";

    /**
     * Cuplikan sumber daya tiap instance. Terpisah dari detak jantung karena keduanya punya sifat
     * berbeda: detak cukup satu angka waktu, sedangkan cuplikan ini berubah isi setiap detik dan
     * hanya berguna selama instance-nya masih hidup.
     */
    private static final String USAGE_KEY = "sim:instances:usage";

    /** Batas diam sebelum sebuah instance dianggap mati. Tiga kali jarak detak jantung. */
    static final Duration STALE_AFTER = Duration.ofSeconds(3);

    /** Batas diam sebelum entri instance dibuang sama sekali dari daftar. */
    private static final Duration FORGET_AFTER = Duration.ofSeconds(60);

    private static final Duration KEY_TTL = Duration.ofHours(1);

    private final RedissonClient redisson;
    private final ApplicationInstance instance;

    public InstanceRegistry(RedissonClient redisson, ApplicationInstance instance) {
        this.redisson = redisson;
        this.instance = instance;
    }

    @Scheduled(fixedRate = 1000)
    public void heartbeat() {
        long now = System.currentTimeMillis();
        RScoredSortedSet<String> instances = registry();
        instances.add(now, instance.id());

        // Tiap instance hanya bisa mengukur JVM-nya sendiri, jadi tiap instance menyiarkan
        // cuplikannya sendiri dan papan menyusunnya kembali dari Redis.
        RMap<String, String> usage = redisson.getMap(USAGE_KEY, StringCodec.INSTANCE);
        usage.put(instance.id(), ResourceUsage.sample().encode());
        usage.expire(KEY_TTL);
        // Instance yang sudah lama sekali tidak terdengar dibuang agar daftarnya tidak menumpuk
        // sisa percobaan lama. Yang baru saja mati sengaja dibiarkan supaya sempat terlihat padam.
        instances.removeRangeByScore(
                Double.NEGATIVE_INFINITY, true, now - FORGET_AFTER.toMillis(), false);
        instances.expire(KEY_TTL);
    }

    /** Seluruh instance yang tercatat, terbaru lebih dulu, beserta lama diamnya. */
    public List<InstanceHeartbeat> instances() {
        long now = System.currentTimeMillis();
        Map<String, String> usage = redisson.<String, String>getMap(
                USAGE_KEY, StringCodec.INSTANCE).readAllMap();

        List<InstanceHeartbeat> result = new ArrayList<>();
        registry().entryRange(0, -1).forEach(entry -> {
            long silentMs = now - entry.getScore().longValue();
            result.add(new InstanceHeartbeat(
                    entry.getValue(),
                    Math.max(0, silentMs),
                    silentMs <= STALE_AFTER.toMillis(),
                    entry.getValue().equals(instance.id()),
                    ResourceUsage.decode(usage.get(entry.getValue()))));
        });
        result.sort(Comparator.comparing(InstanceHeartbeat::id));
        return result;
    }

    private RScoredSortedSet<String> registry() {
        return redisson.getScoredSortedSet(KEY, StringCodec.INSTANCE);
    }

    /**
     * @param id       identitas instance
     * @param silentMs sudah berapa lama instance ini tidak berdetak
     * @param alive    benar bila detaknya masih dalam batas wajar
     * @param self     benar bila ini instance yang sedang menyusun laporan
     * @param usage    cuplikan sumber daya terakhir, {@code null} bila belum pernah tercatat
     */
    public record InstanceHeartbeat(String id, long silentMs, boolean alive, boolean self,
                                    ResourceUsage usage) {
    }
}
