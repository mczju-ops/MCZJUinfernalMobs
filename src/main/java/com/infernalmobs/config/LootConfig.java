package com.infernalmobs.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 掉落配置：loot.yml 提供主开关，loot/ 下各等级池子，special_loot.yml 提供难打怪物额外掉落。
 */
public class LootConfig {

    private final boolean enable;
    private final boolean replaceVanillaDrops;
    private final boolean rotationEnable;
    private final int rotationSets;
    private final boolean dropTimesEnable;
    private final List<DropTimesRule> dropTimesRules;
    /** 掉落条目 id -> 显示名（中文名） */
    private final Map<String, String> lootDisplayNames;
    private final File lootFolder;
    private final SpecialLootConfig specialLootConfig;
    private final java.util.Map<Integer, List<RewardEntry>> cache = new ConcurrentHashMap<>();

    public LootConfig(boolean enable, boolean replaceVanillaDrops, boolean rotationEnable, int rotationSets,
                      boolean dropTimesEnable, List<DropTimesRule> dropTimesRules,
                      Map<String, String> lootDisplayNames,
                      File lootFolder, SpecialLootConfig specialLootConfig) {
        this.enable = enable;
        this.replaceVanillaDrops = replaceVanillaDrops;
        this.rotationEnable = rotationEnable;
        this.rotationSets = Math.max(1, rotationSets);
        this.dropTimesEnable = dropTimesEnable;
        this.dropTimesRules = dropTimesRules != null ? List.copyOf(dropTimesRules) : List.of();
        this.lootDisplayNames = lootDisplayNames != null ? new HashMap<>(lootDisplayNames) : Map.of();
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

    /**
     * 获取掉落条目的显示名：优先 loot_name.yml 映射，否则返回原始 id。
     */
    public String getLootDisplayName(String itemId) {
        if (itemId == null || itemId.isEmpty()) return "";
        if (lootDisplayNames != null && !lootDisplayNames.isEmpty()) {
            String v = lootDisplayNames.get(itemId);
            if (v == null) v = lootDisplayNames.get(itemId.toLowerCase());
            if (v == null) v = lootDisplayNames.get(itemId.toUpperCase());
            if (v != null && !v.isBlank()) return v;
        }
        return itemId;
    }

    /**
     * 同一只怪死亡后，按等级决定抽取掉落条目的次数（允许重复）。
     * 若配置未开启或无法匹配，默认返回 1。
     */
    public int rollDropTimes(int level) {
        if (!dropTimesEnable || dropTimesRules == null || dropTimesRules.isEmpty()) return 1;
        int lv = Math.max(1, level);
        for (DropTimesRule r : dropTimesRules) {
            if (lv < r.levelMin || lv > r.levelMax) continue;
            int min = Math.max(1, r.minTimes);
            int max = Math.max(min, r.maxTimes);
            if (min == max) return min;
            return min + ThreadLocalRandom.current().nextInt(max - min + 1);
        }
        // 找不到匹配区间时兜底为 1
        return 1;
    }

    public static final class DropTimesRule {
        public final int levelMin;
        public final int levelMax;
        public final int minTimes;
        public final int maxTimes;

        public DropTimesRule(int levelMin, int levelMax, int minTimes, int maxTimes) {
            this.levelMin = Math.min(levelMin, levelMax);
            this.levelMax = Math.max(levelMin, levelMax);
            this.minTimes = minTimes;
            this.maxTimes = maxTimes;
        }
    }

    private static int[] parseMinMaxTimesFromPair(Object rawPair, String ctx) {
        if (rawPair == null) return null;
        int minTimes = 1;
        int maxTimes = 1;
        if (rawPair instanceof List<?> list) {
            if (list.size() >= 2) {
                minTimes = getInt(list.get(0), 1);
                maxTimes = getInt(list.get(1), minTimes);
            } else if (list.size() == 1) {
                minTimes = getInt(list.get(0), 1);
                maxTimes = minTimes;
            } else {
                return null;
            }
        } else if (rawPair instanceof Number n) {
            minTimes = n.intValue();
            maxTimes = minTimes;
        } else {
            // 也允许写成字符串 "1,2" / "1 2"
            String s = rawPair.toString().trim();
            if (!s.isEmpty()) {
                String[] parts = s.split("[,\\s]+");
                if (parts.length >= 2) {
                    minTimes = parseSafeInt(parts[0], 1);
                    maxTimes = parseSafeInt(parts[1], minTimes);
                } else if (parts.length == 1) {
                    minTimes = parseSafeInt(parts[0], 1);
                    maxTimes = minTimes;
                } else {
                    return null;
                }
            } else {
                return null;
            }
        }

        minTimes = Math.max(1, minTimes);
        maxTimes = Math.max(minTimes, maxTimes);
        return new int[] { minTimes, maxTimes };
    }

    private static int parseSafeInt(String s, int def) {
        if (s == null) return def;
        String t = s.trim();
        if (t.isEmpty()) return def;
        try { return Integer.parseInt(t); } catch (NumberFormatException ignored) { return def; }
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


    /**
     * 校验 loot 与 special_loot 中的物品 id：
     * - 缺少 id（兼容 id/item_id/item-id）会告警
     * - 配置了 ItemCreator 校验时，若既不是已定义物品也不是原版物品会告警
     */
    public void validateEntries(Consumer<String> warning, Predicate<String> customItemExists) {
        Consumer<String> warn = warning != null ? warning : (msg) -> {};

        if (lootFolder != null && lootFolder.isDirectory()) {
            File[] files = lootFolder.listFiles((dir, name) -> name != null && name.toLowerCase().endsWith(".yml"));
            if (files != null) {
                for (File f : files) {
                    YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
                    List<?> rewards = yml.getList("rewards");
                    if (rewards == null) continue;
                    String fileName = "loot/" + f.getName();
                    for (int i = 0; i < rewards.size(); i++) {
                        String path = fileName + " rewards[" + i + "]";
                        String itemId = resolveItemId(rewards.get(i)).trim();
                        if (itemId.isEmpty()) {
                            warn.accept(path + " 缺少 item_id（兼容键：id/item_id/item-id）或为空，已跳过");
                            continue;
                        }
                        if (customItemExists != null && !customItemExists.test(itemId)) {
                            warn.accept("MCZJUItemCreator 未定义 id 为 " + itemId + " 的物品（" + path + "），将无法生成此战利品");
                        }
                    }
                }
            }
        }

        if (specialLootConfig != null && specialLootConfig.enable()) {
            String id = specialLootConfig.itemId();
            if (id == null || id.trim().isEmpty()) {
                warn.accept("special_loot.yml item-id 为空，将无法生成特殊战利品");
            } else if (customItemExists != null && !customItemExists.test(id)) {
                warn.accept("MCZJUItemCreator 未定义 id 为 " + id + " 的物品（special_loot.yml item-id），将无法生成特殊战利品");
            }
        }
    }

    

    public static final class RewardEntry {
        public final String id;
        public final int amount;
        /** 抽取权重，支持小数（如 0.8）；与整型权重共用同一套加权随机。 */
        public final double weight;
        public final List<String> commands;
        /** 掉落此条时是否向全服广播 */
        public final boolean broadcast;
        /** 广播模板（MiniMessage），支持 {player}/{item}/{amount}/{level} 或 <player>/<item>/<amount>/<level> */
        public final String broadcastMessage;
        /**
         * 轮换套号集合，null 表示不受轮换影响。
         * 支持在配置中写 `rotation-set: 1` 或 `rotation-set: [1,2]`。
         */
        public final java.util.Set<Integer> rotationSets;

        public RewardEntry(String id, int amount, double weight, List<String> commands,
                           boolean broadcast, String broadcastMessage,
                           java.util.Set<Integer> rotationSets) {
            this.id = id != null ? id : "";
            this.amount = Math.max(1, amount);
            this.weight = Math.max(0, weight);
            this.commands = commands != null ? new ArrayList<>(commands) : List.of();
            this.broadcast = broadcast;
            this.broadcastMessage = broadcastMessage != null ? broadcastMessage : "";
            this.rotationSets = rotationSets;
        }
    }

    /**
     * 从插件数据目录加载：loot.yml 主配置，loot/ 等级池子，special_loot.yml 难打怪物额外掉落。
     */
    public static LootConfig load(File dataFolder) {
        if (dataFolder == null || !dataFolder.exists()) {
            return new LootConfig(false, false, false, 3,
                    false, List.of(),
                    Map.of(),
                    null, SpecialLootConfig.DISABLED);
        }
        File rootFile = new File(dataFolder, "loot.yml");
        boolean enable = false;
        boolean replaceVanillaDrops = false;
        boolean rotationEnable = false;
        int rotationSets = 3;
        boolean dropTimesEnable = false;
        List<DropTimesRule> dropTimesRules = List.of();
        if (rootFile.isFile()) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(rootFile);
            enable = yml.getBoolean("enable", false);
            replaceVanillaDrops = yml.getBoolean("replace-vanilla-drops", false);
            ConfigurationSection rot = yml.getConfigurationSection("rotation");
            if (rot != null) {
                rotationEnable = rot.getBoolean("enable", false);
                rotationSets = Math.max(1, rot.getInt("sets", 3));
            }

            ConfigurationSection dt = yml.getConfigurationSection("drop-times");
            if (dt != null) {
                dropTimesEnable = dt.getBoolean("enable", false);
                List<DropTimesRule> rules = new ArrayList<>();

                // 1) ranges 写法：drop-times: { ranges: [ [minLevel,maxLevel,minTimes,maxTimes], ... ] }
                List<?> rawRanges = dt.getList("ranges", Collections.emptyList());
                if (rawRanges != null && !rawRanges.isEmpty()) {
                    for (Object raw : rawRanges) {
                        DropTimesRule rule = parseDropTimesRule(raw);
                        if (rule != null) rules.add(rule);
                    }
                } else {
                    // 2) 逐级写法：drop-times: { 1: [1,1], 2: [1,1], ... , fallback: [5,5] }
                    for (String key : dt.getKeys(false)) {
                        if ("enable".equalsIgnoreCase(key) || "ranges".equalsIgnoreCase(key) || "fallback".equalsIgnoreCase(key)) continue;
                        Integer level = tryParseInt(key);
                        if (level == null) continue;
                        List<?> pair = dt.getList(key);
                        int[] minMax = parseMinMaxTimesFromPair(pair, "drop-times." + key);
                        if (minMax == null) continue;
                        rules.add(new DropTimesRule(level, level, minMax[0], minMax[1]));
                    }
                }

                // fallback：没匹配到具体等级区间时兜底
                List<?> fb = dt.getList("fallback", null);
                if (fb != null) {
                    int[] minMax = parseMinMaxTimesFromPair(fb, "drop-times.fallback");
                    if (minMax != null) {
                        rules.add(new DropTimesRule(1, Integer.MAX_VALUE, minMax[0], minMax[1]));
                    }
                }

                dropTimesRules = rules;
            }
        }
        File lootFolder = new File(dataFolder, "loot");
        if (!lootFolder.exists() || !lootFolder.isDirectory()) {
            lootFolder = null;
        }
        Map<String, String> lootDisplayNames = loadLootDisplayNames(new File(dataFolder, "loot_name.yml"));
        SpecialLootConfig specialLootConfig = loadSpecialLootConfig(new File(dataFolder, "special_loot.yml"));
        return new LootConfig(enable, replaceVanillaDrops, rotationEnable, rotationSets,
                dropTimesEnable, dropTimesRules,
                lootDisplayNames,
                lootFolder, specialLootConfig);
    }

    private static Map<String, String> loadLootDisplayNames(File file) {
        Map<String, String> out = new HashMap<>();
        if (file == null || !file.isFile()) return out;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            String v = yml.getString(key);
            if (v != null && !v.isBlank()) out.put(key, v);
        }
        return out;
    }

    private static DropTimesRule parseDropTimesRule(Object raw) {
        // 支持：[-] [level-min, level-max, min-times, max-times]
        if (raw instanceof List<?> list && list.size() >= 4) {
            int levelMin = getInt(list.get(0), 1);
            int levelMax = getInt(list.get(1), levelMin);
            int minTimes = getInt(list.get(2), 1);
            int maxTimes = getInt(list.get(3), minTimes);
            return new DropTimesRule(levelMin, levelMax, minTimes, maxTimes);
        }
        // 支持：{level-min:1, level-max:3, min-times:1, max-times:1}
        if (raw instanceof java.util.Map<?, ?> map) {
            int levelMin = getIntFromMap(map, "level-min", 1);
            int levelMax = getIntFromMap(map, "level-max", levelMin);
            int minTimes = getIntFromMap(map, "min-times", 1);
            int maxTimes = getIntFromMap(map, "max-times", minTimes);
            return new DropTimesRule(levelMin, levelMax, minTimes, maxTimes);
        }
        return null;
    }

    private static Integer tryParseInt(String s) {
        if (s == null) return null;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) { return null; }
    }

    private static int getInt(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        if (o != null) {
            try { return Integer.parseInt(o.toString().trim()); } catch (NumberFormatException ignored) {}
        }
        return def;
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
        int amount = 1;
        double weight = 10;
        List<String> commands = new ArrayList<>();
        boolean broadcast = false;
        String broadcastMessage = "";
        java.util.Set<Integer> rotationSets = null;
        if (o instanceof ConfigurationSection sec) {
            id = getStringWithAliases(sec, "id", "item_id", "item-id");
            amount = sec.getInt("amount", 1);
            weight = sec.getDouble("weight", 10);
            broadcast = sec.getBoolean("broadcast", false);
            broadcastMessage = sec.getString("broadcast-message", "");
            if (sec.contains("commands")) {
                for (Object c : sec.getList("commands", Collections.emptyList())) {
                    if (c != null) commands.add(c.toString());
                }
            }
            if (sec.contains("rotation-set")) rotationSets = parseRotationSets(sec.get("rotation-set"));
            return new RewardEntry(id, amount, weight, commands, broadcast, broadcastMessage, rotationSets);
        }
        if (o instanceof java.util.Map<?, ?> map) {
            id = getStringWithAliases(map, "id", "item_id", "item-id");
            amount = getIntFromMap(map, "amount", 1);
            weight = getDoubleFromMap(map, "weight", 10);
            Object brObj = map.get("broadcast");
            if (brObj instanceof Boolean b) broadcast = b;
            else if (brObj != null) broadcast = "true".equalsIgnoreCase(brObj.toString()) || "1".equals(brObj.toString());
            Object bmObj = map.get("broadcast-message");
            if (bmObj != null) broadcastMessage = bmObj.toString();
            Object cmdObj = map.get("commands");
            if (cmdObj instanceof List<?> list) {
                for (Object c : list) {
                    if (c != null) commands.add(c.toString());
                }
            }
            if (map.containsKey("rotation-set")) rotationSets = parseRotationSets(map.get("rotation-set"));
            return new RewardEntry(id, amount, weight, commands, broadcast, broadcastMessage, rotationSets);
        }
        return null;
    }

    /**
     * 解析 rotation-set 节点为整数集合。
     * 支持：int（单值）、List（数组）、string（"1,2" 或 "1 2"）。
     */
    private static java.util.Set<Integer> parseRotationSets(Object raw) {
        if (raw == null) return null;
        java.util.Set<Integer> out = new java.util.HashSet<>();
        if (raw instanceof Number n) {
            out.add(n.intValue());
            return out;
        }
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o == null) continue;
                Integer v = parseRotationSetInt(o);
                if (v != null) out.add(v);
            }
            return out;
        }
        String s = raw.toString().trim();
        if (s.isEmpty()) return out;
        for (String part : s.split("[,\\s]+")) {
            Integer v = parseRotationSetInt(part);
            if (v != null) out.add(v);
        }
        return out;
    }

    private static Integer parseRotationSetInt(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Number n) return n.intValue();
        String s = raw.toString().trim();
        if (s.isEmpty()) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String resolveItemId(Object o) {
        if (o instanceof ConfigurationSection sec) return getStringWithAliases(sec, "id", "item_id", "item-id");
        if (o instanceof java.util.Map<?, ?> map) return getStringWithAliases(map, "id", "item_id", "item-id");
        return "";
    }

    private static String getStringWithAliases(ConfigurationSection sec, String... keys) {
        if (sec == null || keys == null) return "";
        for (String key : keys) {
            String v = sec.getString(key);
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return "";
    }

    private static String getStringWithAliases(java.util.Map<?, ?> map, String... keys) {
        if (map == null || keys == null) return "";
        for (String key : keys) {
            Object v = map.get(key);
            if (v != null) {
                String s = v.toString().trim();
                if (!s.isEmpty()) return s;
            }
        }
        return "";
    }

    private static boolean isVanillaItemId(String id) {
        if (id == null || id.isBlank()) return false;
        String upper = id.toUpperCase().replace(" ", "_");
        try {
            Material mat = Material.valueOf(upper);
            return mat.isItem() && !mat.isAir();
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static int getIntFromMap(java.util.Map<?, ?> map, String key, int def) {
        Object v = map.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        if (v != null) try { return Integer.parseInt(v.toString()); } catch (NumberFormatException ignored) {}
        return def;
    }

    private static double getDoubleFromMap(java.util.Map<?, ?> map, String key, double def) {
        Object v = map.get(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        if (v != null) try { return Double.parseDouble(v.toString().trim()); } catch (NumberFormatException ignored) {}
        return def;
    }
}
