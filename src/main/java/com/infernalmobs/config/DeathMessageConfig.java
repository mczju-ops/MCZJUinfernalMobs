package com.infernalmobs.config;

import org.bukkit.entity.EntityType;

import java.util.List;
import java.util.Map;

/**
 * 击杀播报配置。
 */
public record DeathMessageConfig(
        boolean enable,
        String namePrefix,
        String defaultWeapon,
        Map<Integer, String> levelPrefixes,
        List<String> messages,
        Map<String, String> mobNames
) {
    public String getLevelPrefix(int level) {
        String s = levelPrefixes.get(level);
        if (s != null) return s;
        int cap = levelPrefixes.keySet().stream().mapToInt(Integer::intValue).max().orElse(15);
        return level > cap ? levelPrefixes.get(cap) : levelPrefixes.getOrDefault(1, "&f初级");
    }

    public String getMobDisplayName(EntityType type) {
        String name = mobNames.get(type.name());
        if (name != null) return name;
        String raw = type.name().toLowerCase().replace('_', ' ');
        if (raw.isEmpty()) return type.name();
        StringBuilder sb = new StringBuilder();
        for (String word : raw.split(" ")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }
}
