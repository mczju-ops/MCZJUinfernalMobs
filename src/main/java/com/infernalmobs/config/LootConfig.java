package com.infernalmobs.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 掉落配置：loot.yml 提供主开关，loot/ 下各等级池子，special_loot.yml 提供难打怪物额外掉落。
 */
public class LootConfig {

    private final boolean enable;
    private final boolean replaceVanillaDrops;
    private final boolean rotationEnable;
    private final int rotationSets;
    private final File lootFolder;
    private final SpecialLootConfig specialLootConfig;
    private final java.util.Map<Integer, List<RewardEntry>> cache = new ConcurrentHashMap<>();

    public LootConfig(boolean enable, boolean replaceVanillaDrops, boolean rotationEnable, int rotationSets,
                      File lootFolder, SpecialLootConfig specialLootConfig) {
        this.enable = enable;
        this.replaceVanillaDrops = replaceVanillaDrops;
        this.rotationEnable = rotationEnable;
        this.rotationSets = Math.max(1, rotationSets);
        this.lootFolder = lootFolder;
        this.specialLootConfig = specialLootConfig != null ? specialLootConfig : SpecialLootConfig.DISABLED;
    }

    public boolean isRotationEnable() { return rotationEnable; }
    public int getRotationSets() { return rotationSets; }

    public SpecialLootConfig getSpecialLootConfig() {
        return specialLootConfig;
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
        /** 轮换套号，null 表示不受轮换影响 */
        public final Integer rotationSet;

        public RewardEntry(String id, int amount, int weight, List<String> commands, Integer rotationSet) {
            this.id = id != null ? id : "";
            this.amount = Math.max(1, amount);
            this.weight = Math.max(0, weight);
            this.commands = commands != null ? new ArrayList<>(commands) : List.of();
            this.rotationSet = rotationSet;
        }
    }

    /**
     * 从插件数据目录加载：loot.yml 主配置，loot/ 等级池子，special_loot.yml 难打怪物额外掉落。
     */
    public static LootConfig load(File dataFolder) {
        if (dataFolder == null || !dataFolder.exists()) {
            return new LootConfig(false, false, false, 3, null, SpecialLootConfig.DISABLED);
        }
        File rootFile = new File(dataFolder, "loot.yml");
        boolean enable = false;
        boolean replaceVanillaDrops = false;
        boolean rotationEnable = false;
        int rotationSets = 3;
        if (rootFile.isFile()) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(rootFile);
            enable = yml.getBoolean("enable", false);
            replaceVanillaDrops = yml.getBoolean("replace-vanilla-drops", false);
            ConfigurationSection rot = yml.getConfigurationSection("rotation");
            if (rot != null) {
                rotationEnable = rot.getBoolean("enable", false);
                rotationSets = Math.max(1, rot.getInt("sets", 3));
            }
        }
        File lootFolder = new File(dataFolder, "loot");
        if (!lootFolder.exists() || !lootFolder.isDirectory()) {
            lootFolder = null;
        }
        SpecialLootConfig specialLootConfig = loadSpecialLootConfig(new File(dataFolder, "special_loot.yml"));
        return new LootConfig(enable, replaceVanillaDrops, rotationEnable, rotationSets, lootFolder, specialLootConfig);
    }

    private static SpecialLootConfig loadSpecialLootConfig(File file) {
        if (file == null || !file.isFile()) return SpecialLootConfig.DISABLED;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        if (!yml.getBoolean("enable", false)) return SpecialLootConfig.DISABLED;
        String itemId = yml.getString("item-id", "nether_star");
        Map<String, Double> rates = new HashMap<>();
        ConfigurationSection ratesSec = yml.getConfigurationSection("rates");
        if (ratesSec != null) {
            for (String key : ratesSec.getKeys(false)) {
                double v = ratesSec.getDouble(key, 0);
                if (v > 0) rates.put(key, v);
            }
        }
        return new SpecialLootConfig(true, itemId != null ? itemId : "nether_star", rates);
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
        Integer rotationSet = null;
        if (o instanceof ConfigurationSection sec) {
            id = sec.getString("id", "");
            amount = sec.getInt("amount", 1);
            weight = sec.getInt("weight", 10);
            if (sec.contains("commands")) {
                for (Object c : sec.getList("commands", Collections.emptyList())) {
                    if (c != null) commands.add(c.toString());
                }
            }
            if (sec.contains("rotation-set")) rotationSet = sec.getInt("rotation-set", 1);
            return new RewardEntry(id, amount, weight, commands, rotationSet);
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
            if (map.containsKey("rotation-set")) rotationSet = getIntFromMap(map, "rotation-set", 1);
            return new RewardEntry(id, amount, weight, commands, rotationSet);
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
