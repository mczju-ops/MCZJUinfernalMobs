package com.infernalmobs.config;

import org.bukkit.entity.EntityType;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 击杀播报配置。怪物名格式 [LvN]前缀+名，按等级档位着色。
 */
public record DeathMessageConfig(
        boolean enable,
        String namePrefix,
        String defaultWeapon,
        Map<Integer, String> levelPrefixes,
        Map<Integer, String> levelTierColors,
        List<String> messages,
        Map<String, String> mobNames,
        boolean slainByEnable,
        List<String> slainByMessages,
        boolean slainByWithWeaponEnable,
        String slainByWithWeaponWhen,
        List<String> slainByWithWeaponMessages,
        String playerColorNormal,
        String playerColorOp,
        int globalBroadcastLevelThreshold
) {
    /** 等级前缀：初级(1-3)/中级(4-6)/高级(7-9)/炒鸡(10+)。Lv15 的炒鸡用 &lt;obfuscated&gt; 乱码效果。 */
    public String getLevelPrefix(int level) {
        if (level <= 3) return "初级";
        if (level <= 6) return "中级";
        if (level <= 9) return "高级";
        if (level >= 15) return "<obfuscated>Infernal</obfuscated>";
        return "炒鸡";
    }

    /** 等级档位颜色：初级白/中级蓝/高级紫/炒鸡10-12金/13-15红。MiniMessage 标签如 &lt;white&gt;。 */
    public String getLevelTierColor(int level) {
        if (levelTierColors != null && !levelTierColors.isEmpty()) {
            return levelTierColors.entrySet().stream()
                    .filter(e -> level >= e.getKey())
                    .max(Comparator.comparingInt(Map.Entry::getKey))
                    .map(Map.Entry::getValue)
                    .orElse("<white>");
        }
        if (level <= 3) return "<white>";
        if (level <= 6) return "<blue>";
        if (level <= 9) return "<dark_purple>";
        if (level <= 12) return "<gold>";
        return "<red>";
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
