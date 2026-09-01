package com.fadhli.simulation.manager;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Satu sesi pembelian, disimpan sebagai hash ber-TTL di Redis.
 *
 * <p>Sesi inilah yang menjadi satu-satunya catatan tentang langkah mana yang sedang dijalani
 * seseorang. Sebelumnya langkah ikut dicatat pada entri pemilik slot, dan dua salinan dari fakta
 * yang sama selalu berakhir tidak sinkron.
 *
 * @param userId       pemilik sesi
 * @param state        langkah yang sedang dijalani
 * @param slot         slot yang dipegang, {@code null} kalau sesi sudah berakhir
 * @param fencingToken penanda kepemilikan slot, dibutuhkan saat melepasnya
 * @param startedAt    kapan sesi dimulai
 * @param updatedAt    kapan langkah terakhir berpindah
 */
public record PurchaseSession(String userId, SessionState state, String slot, String fencingToken,
                              long startedAt, long updatedAt) {

    public Map<String, String> toMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("userId", userId);
        map.put("state", state.name());
        map.put("slot", slot == null ? "" : slot);
        map.put("fencingToken", fencingToken == null ? "" : fencingToken);
        map.put("startedAt", String.valueOf(startedAt));
        map.put("updatedAt", String.valueOf(updatedAt));
        return map;
    }

    public static PurchaseSession fromMap(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        SessionState state = SessionState.of(map.get("state"));
        if (state == null) {
            return null;
        }
        return new PurchaseSession(
                map.get("userId"),
                state,
                emptyToNull(map.get("slot")),
                emptyToNull(map.get("fencingToken")),
                parse(map.get("startedAt")),
                parse(map.get("updatedAt")));
    }

    /** Bukti kepemilikan slot milik sesi ini, atau {@code null} kalau slotnya sudah dilepas. */
    public SlotLease lease(String simId) {
        return (slot == null || fencingToken == null) ? null
                : new SlotLease(simId, slot, fencingToken);
    }

    private static String emptyToNull(String value) {
        return (value == null || value.isEmpty()) ? null : value;
    }

    private static long parse(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException | NullPointerException e) {
            return 0;
        }
    }
}
