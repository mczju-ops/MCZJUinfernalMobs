package com.infernalmobs.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * loot.yml 解析：等级区间 + rewards 列表（id, amount, weight, commands），与 ItemCreator 配置风格一致。
 */
public class LootConfig {

    private final boolean enable;
    private final boolean replaceVanillaDrops;
    private final List<LevelPoolEntry> levelPools;

    public LootConfig(boolean enable, boolean replaceVanillaDrops, List<LevelPoolEntry> levelPools) {
        this.enable = enable;
        this.replaceVanillaDrops = replaceVanillaDrops;
        this.levelPools = levelPools != null ? new ArrayList<>(levelPools) : List.of();
    }

    public boolean isEnable() {
        return enable;
    }

    public boolean isReplaceVanillaDrops() {
        return replaceVanillaDrops;
    }

    /** 根据等级取该区间的 rewards 列表，未匹配则返回空列表。 */
    public List<RewardEntry> getRewardsForLevel(int level) {
        for (LevelPoolEntry e : levelPools) {
            if (level >= e.min && level <= e.max) return e.rewards;
        }
        return List.of();
    }

    public static final class LevelPoolEntry {
        public final int min;
        public final int max;
        public final List<RewardEntry> rewards;

        public LevelPoolEntry(int min, int max, List<RewardEntry> rewards) {
            this.min = min;
            this.max = max;
            this.rewards = rewards != null ? new ArrayList<>(rewards) : List.of();
        }
    }

    public static final class RewardEntry {
        public final String id;
        public final int amount;
        public final int weight;
        public final List<String> commands;

        public RewardEntry(String id, int amount, int weight, List<String> commands) {
            this.id = id != null ? id : "";
            this.amount = Math.max(1, amount);
            this.weight = Math.max(0, weight);
            this.commands = commands != null ? new ArrayList<>(commands) : List.of();
        }
    }

    public static LootConfig load(File file) {
        if (file == null || !file.exists()) {
            return new LootConfig(false, false, List.of());
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        boolean enable = yml.getBoolean("enable", false);
        boolean replaceVanillaDrops = yml.getBoolean("replace-vanilla-drops", false);
        List<LevelPoolEntry> levelPools = new ArrayList<>();
        List<?> raw = yml.getList("level-pools");
        if (raw != null) {
            for (Object o : raw) {
                LevelPoolEntry entry = parseLevelPoolEntry(o);
                if (entry != null) levelPools.add(entry);
            }
        }
        return new LootConfig(enable, replaceVanillaDrops, levelPools);
    }

    private static LevelPoolEntry parseLevelPoolEntry(Object o) {
        int min = 1, max = 1;
        List<RewardEntry> rewards = new ArrayList<>();
        if (o instanceof ConfigurationSection sec) {
            min = sec.getInt("min", 1);
            max = sec.getInt("max", 1);
            parseRewards(sec.getList("rewards"), rewards);
            return new LevelPoolEntry(min, max, rewards);
        }
        if (o instanceof java.util.Map<?, ?> map) {
            min = getIntFromMap(map, "min", 1);
            max = getIntFromMap(map, "max", 1);
            Object r = map.get("rewards");
            if (r instanceof List<?> list) parseRewards(list, rewards);
            return new LevelPoolEntry(min, max, rewards);
        }
        return null;
    }

    private static void parseRewards(List<?> raw, List<RewardEntry> out) {
        if (raw == null) return;
        for (Object o : raw) {
            RewardEntry e = parseRewardEntry(o);
            if (e != null && !e.id.isEmpty()) out.add(e);
        }
    }

    private static RewardEntry parseRewardEntry(Object o) {
        String id = "";
        int amount = 1, weight = 10;
        List<String> commands = new ArrayList<>();
        if (o instanceof ConfigurationSection sec) {
            id = sec.getString("id", "");
            amount = sec.getInt("amount", 1);
            weight = sec.getInt("weight", 10);
            if (sec.contains("commands")) {
                for (Object c : sec.getList("commands", Collections.emptyList())) {
                    if (c != null) commands.add(c.toString());
                }
            }
            return new RewardEntry(id, amount, weight, commands);
        }
        if (o instanceof java.util.Map<?, ?> map) {
            Object idObj = map.get("id");
            id = idObj != null ? idObj.toString().trim() : "";
            amount = getIntFromMap(map, "amount", 1);
            weight = getIntFromMap(map, "weight", 10);
            Object cmdObj = map.get("commands");
            if (cmdObj instanceof List<?> list) {
                for (Object c : list) {
                    if (c != null) commands.add(c.toString());
                }
            }
            return new RewardEntry(id, amount, weight, commands);
        }
        return null;
    }

    private static int getIntFromMap(java.util.Map<?, ?> map, String key, int def) {
        Object v = map.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        if (v != null) try { return Integer.parseInt(v.toString()); } catch (NumberFormatException ignored) {}
        return def;
    }
}
