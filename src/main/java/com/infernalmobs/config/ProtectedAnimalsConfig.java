package com.infernalmobs.config;

import org.bukkit.entity.EntityType;

import java.util.Collections;
import java.util.Set;

/**
 * 「炒鸡小动物」清单：非炒鸡怪时清除掉落，玩家击杀时发送提示。
 */
public record ProtectedAnimalsConfig(
        boolean enabled,
        Set<EntityType> types,
        String messageTemplate,
        boolean clearExp
) {
    public static ProtectedAnimalsConfig disabled() {
        return new ProtectedAnimalsConfig(false, Collections.emptySet(),
                "<bold><red><player_name>欺负炒鸡小动物！", true);
    }

    public boolean protects(EntityType type) {
        return enabled && types != null && types.contains(type);
    }
}
