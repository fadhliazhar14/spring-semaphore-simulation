package com.fadhli.simulation.manager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Menjaga daftar instance yang dipakai papan observasi untuk menunjukkan siapa yang masih hidup.
 *
 * <p>Instance kedua di sini dipalsukan dengan menulis langsung ke ZSET detak jantung, bukan dengan
 * menyalakan JVM kedua. Yang perlu dijaga memang bentuk datanya: kapan sebuah instance dianggap
 * masih berdetak dan kapan dianggap padam.
 */
@SpringBootTest
class InstanceRegistryTest {

    private static final String KEY = "sim:instances";

    @Autowired
    private InstanceRegistry registry;

    @Autowired
    private ApplicationInstance instance;

    @Autowired
    private RedissonClient redisson;

    @Test
    @DisplayName("Should mark a silent instance as dead while keeping the beating one alive")
    void testHeartbeatDistinguishesLiveFromDead() {
        String ghost = "inst-ghost-" + UUID.randomUUID().toString().substring(0, 6);
        RScoredSortedSet<String> beats = redisson.getScoredSortedSet(KEY, StringCodec.INSTANCE);
        try {
            registry.heartbeat();

            // Instance yang detak terakhirnya jauh melewati batas diam, tetapi belum cukup lama
            // untuk dilupakan — persis keadaan sesaat setelah sebuah instance dimatikan.
            long silentSince = System.currentTimeMillis() - InstanceRegistry.STALE_AFTER.toMillis() * 2;
            beats.add(silentSince, ghost);

            List<InstanceRegistry.InstanceHeartbeat> all = registry.instances();

            InstanceRegistry.InstanceHeartbeat self = all.stream()
                    .filter(h -> h.id().equals(instance.id()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Instance sendiri harus tercatat"));
            assertTrue(self.alive(), "Instance yang baru berdetak harus dianggap hidup");
            assertTrue(self.self(), "Instance penyusun laporan harus menandai dirinya sendiri");

            InstanceRegistry.InstanceHeartbeat dead = all.stream()
                    .filter(h -> h.id().equals(ghost))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Instance yang baru padam harus tetap terlihat"));
            assertFalse(dead.alive(), "Instance yang diam melewati batas harus dianggap padam");
            assertFalse(dead.self());
            assertTrue(dead.silentMs() >= InstanceRegistry.STALE_AFTER.toMillis(),
                    "Lama diam harus dilaporkan apa adanya, bukan dibulatkan ke nol");
        } finally {
            beats.remove(ghost);
        }
    }
}
