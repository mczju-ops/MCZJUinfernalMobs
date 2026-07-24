package com.infernalmobs.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * dye.yml 配置：用于管理 Dye 联动的本地回退池与死亡掉落参数。
 */
public final class DyeConfig {

    private final double deathChance;
    private final int dropAmount;
    private final String fallbackItemId;
    private final String fallbackHex;
    private final List<Entry> pool;

    public DyeConfig(double deathChance, int dropAmount, String fallbackItemId, String fallbackHex, List<Entry> pool) {
        this.deathChance = deathChance;
        this.dropAmount = Math.max(1, dropAmount);
        this.fallbackItemId = fallbackItemId != null ? fallbackItemId : "infernal_dye";
        this.fallbackHex = fallbackHex != null ? fallbackHex : "";
        this.pool = pool != null ? List.copyOf(pool) : List.of();
    }

    public double deathChance() { return deathChance; }
    public int dropAmount() { return dropAmount; }
    public String fallbackItemId() { return fallbackItemId; }
    public String fallbackHex() { return fallbackHex; }
    public List<Entry> pool() { return pool; }

    public static DyeConfig load(File dataFolder) {
        if (dataFolder == null) return defaults();
        File f = new File(dataFolder, "dye.yml");
        if (!f.isFile()) return defaults();
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);

        double chance = yml.getDouble("death.chance", 0.35d);
        int amount = yml.getInt("death.amount", 1);
        String itemId = yml.getString("fallback.item-id", "infernal_dye");
        String hex = yml.getString("fallback.hex", "");

        List<Entry> entries = new ArrayList<>();
        for (var map : yml.getMapList("pool")) {
            if (map == null) continue;
            String id = asString(map.get("item-id"));
            if (id.isBlank()) continue;
            double w = asDouble(map.get("weight"), 1.0d);
            if (w <= 0) continue;
            String color = asString(map.get("hex"));
            entries.add(new Entry(id, w, color));
        }
        return new DyeConfig(chance, amount, itemId, hex, entries);
    }

    private static String asString(Object obj) {
        return obj == null ? "" : String.valueOf(obj).trim();
    }

    private static double asDouble(Object obj, double def) {
        if (obj instanceof Number n) return n.doubleValue();
        if (obj != null) {
            try {
                return Double.parseDouble(String.valueOf(obj).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    public static DyeConfig defaults() {
        return new DyeConfig(0.35d, 1, "infernal_dye", "", List.of());
    }

    public record Entry(String itemId, double weight, String hex) {}
}

