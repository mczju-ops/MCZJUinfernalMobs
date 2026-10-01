package com.infernalmobs.util;

import com.infernalmobs.api.InfernalMobHandle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.stream.Collectors;

/** 在临时实体 PDC 中保存和恢复 API 句柄的稳定字段。 */
public final class PdcHandleCodec {

    private PdcHandleCodec() {}

    public static void write(PersistentDataContainer pdc, InfernalMobHandle handle) {
        pdc.set(Keys.FIREWORK_HANDLE_AFFIXES, PersistentDataType.STRING,
                String.join(",", handle.getAffixIds()));
        pdc.set(Keys.FIREWORK_HANDLE_SUPPRESSED, PersistentDataType.STRING,
                String.join(",", handle.getSuppressedAffixIds()));
        if (handle.getDisplayName() == null) {
            pdc.remove(Keys.FIREWORK_HANDLE_DISPLAY_NAME);
        } else {
            pdc.set(Keys.FIREWORK_HANDLE_DISPLAY_NAME, PersistentDataType.STRING, handle.getDisplayName());
        }
    }

    public static InfernalMobHandle read(PersistentDataContainer pdc, LivingEntity entity, int level) {
        String affixes = pdc.get(Keys.FIREWORK_HANDLE_AFFIXES, PersistentDataType.STRING);
        String suppressed = pdc.get(Keys.FIREWORK_HANDLE_SUPPRESSED, PersistentDataType.STRING);
        String displayName = pdc.get(Keys.FIREWORK_HANDLE_DISPLAY_NAME, PersistentDataType.STRING);
        if (affixes == null || suppressed == null) return null;

        var affixIds = split(affixes);
        var suppressedIds = Arrays.stream(suppressed.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        InfernalMobHandle handle = new InfernalMobHandle(entity, level, affixIds, suppressedIds);
        handle.setDisplayName(displayName);
        return handle;
    }

    private static java.util.List<String> split(String value) {
        if (value == null || value.isBlank()) return java.util.List.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
