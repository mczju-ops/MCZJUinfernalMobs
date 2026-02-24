package com.infernalmobs.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 掉落配置：根配置在 loot.yml，各等级池子在 loot/ 文件夹下，按等级拆分为 1.yml、2.yml、…，各自独立权重。
 */
public class LootConfig {

    private final boolean enable;
    private final boolean replaceVanillaDrops;
    private final File lootFolder;
    private final java.util.Map<Integer, List<RewardEntry>> cache = new ConcurrentHashMap<>();

    public LootConfig(boolean enable, boolean replaceVanillaDrops, File lootFolder) {
        this.enable = enable;
        this.replaceVanillaDrops = replaceVanillaDrops;
        this.lootFolder = lootFolder;
    }

    public boolean isEnable() {
        return enable;
    }

    public boolean isReplaceVanillaDrops() {
        return replaceVanillaDrops;
    }

    /** 根据等级读取 loot/<level>.yml 的 rewards 列表，未找到文件则返回空列表。重载后会重新读文件。 */
    public List<RewardEntry> getRewardsForLevel(int level) {
        return cache.computeIfAbsent(level, this::loadRewardsForLevel);
    }

    private List<RewardEntry> loadRewardsForLevel(int level) {
        if (lootFolder == null || !lootFolder.isDirectory()) return List.of();
        File f = new File(lootFolder, level + ".yml");
        if (!f.isFile() || !f.exists()) return List.of();
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
        List<RewardEntry> out = new ArrayList<>();
        parseRewards(yml.getList("rewards"), out);
        return out;
    }

    /** 重载时清缓存，下次按等级取会重新读文件。 */
    public void clearCache() {
        cache.clear();
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

    /**
     * 从插件数据目录加载：loot.yml 提供 enable、replace-vanilla-drops，loot/ 目录下 1.yml、2.yml… 为各等级池子。
     */
    public static LootConfig load(File dataFolder) {
        if (dataFolder == null || !dataFolder.exists()) {
            return new LootConfig(false, false, null);
        }
        File rootFile = new File(dataFolder, "loot.yml");
        boolean enable = false;
        boolean replaceVanillaDrops = false;
        if (rootFile.isFile()) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(rootFile);
            enable = yml.getBoolean("enable", false);
            replaceVanillaDrops = yml.getBoolean("replace-vanilla-drops", false);
        }
        File lootFolder = new File(dataFolder, "loot");
        if (!lootFolder.exists() || !lootFolder.isDirectory()) {
            lootFolder = null;
        }
        return new LootConfig(enable, replaceVanillaDrops, lootFolder);
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
