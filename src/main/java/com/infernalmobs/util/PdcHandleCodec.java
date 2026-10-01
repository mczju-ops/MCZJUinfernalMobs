package com.infernalmobs.util;

import com.infernalmobs.api.InfernalMobHandle;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.stream.Collectors;

/** 在临时实体 PDC 中保存和恢复 API 句柄的稳定字段。 */
public final class PdcHandleCodec {

    private PdcHandleCodec() {}

    public static void write(PersistentDataContainer pdc, InfernalMobHandle handle) {
        write(pdc, handle, Keys.FIREWORK_HANDLE_AFFIXES, Keys.FIREWORK_HANDLE_SUPPRESSED,
                Keys.FIREWORK_HANDLE_DISPLAY_NAME);
    }

    public static void write(PersistentDataContainer pdc, InfernalMobHandle handle,
                             NamespacedKey affixesKey,
                             NamespacedKey suppressedKey,
                             NamespacedKey displayNameKey) {
        pdc.set(affixesKey, PersistentDataType.STRING,
                String.join(",", handle.getAffixIds()));
        pdc.set(suppressedKey, PersistentDataType.STRING,
                String.join(",", handle.getSuppressedAffixIds()));
        if (handle.getDisplayName() == null) {
            pdc.remove(displayNameKey);
        } else {
            pdc.set(displayNameKey, PersistentDataType.STRING, handle.getDisplayName());
        }
    }

    public static InfernalMobHandle read(PersistentDataContainer pdc, LivingEntity entity, int level) {
        return read(pdc, entity, level, Keys.FIREWORK_HANDLE_AFFIXES, Keys.FIREWORK_HANDLE_SUPPRESSED,
                Keys.FIREWORK_HANDLE_DISPLAY_NAME);
    }

    public static InfernalMobHandle read(PersistentDataContainer pdc, LivingEntity entity, int level,
                                          NamespacedKey affixesKey,
                                          NamespacedKey suppressedKey,
                                          NamespacedKey displayNameKey) {
        String affixes = pdc.get(affixesKey, PersistentDataType.STRING);
        String suppressed = pdc.get(suppressedKey, PersistentDataType.STRING);
        String displayName = pdc.get(displayNameKey, PersistentDataType.STRING);
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
