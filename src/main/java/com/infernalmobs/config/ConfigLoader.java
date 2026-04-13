package com.infernalmobs.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

/**
 * 配置加载器，统一管理 config.yml 的读取与重载。
 */
public class ConfigLoader {

    private final JavaPlugin plugin;
    private FileConfiguration config;

    private List<String> enabledWorlds;
    private int levelBase;
    private int levelScale;
    private int levelMax;
    private int levelFallbackMin;
    private int levelFallbackMax;
    private Set<EntityType> infernalAllowTypesDefault = Collections.emptySet();
    private Set<EntityType> infernalDenyTypesDefault = Collections.emptySet();
    private String affixCountFormula;
    private int affixMin;
    private int affixMax;
    private int affixTierThreshold;
    private Map<String, Integer> skillWeights;
    private Map<String, SkillConfig> skillConfigs;
    private List<RegionConfig> regions;
    private Map<String, PresetConfig> presets;
    private DeathMessageConfig deathMessageConfig;
    private ProtectedAnimalsConfig protectedAnimalsConfig = ProtectedAnimalsConfig.disabled();
    private MobRegistryConfig mobRegistryConfig;
    private Map<String, String> skillNames;
    private boolean debug;
    /** 炒鸡怪经验倍率：最终经验 = 原版经验 × 等级 × expMultiplier，0 表示不修改。 */
    private double expMultiplier;
    /** 全知之眼射线判定距离（格），默认 20。 */
    private double infernalEyeRange = 20.0;
    /** 幻形之锁（morph_controller）右键空气/方块时准星射线距离（格），0 表示禁用射线仅保留原版右键实体。 */
    private double morphControllerRayRange = 8.0;
    /** 全局默认单级权重表（level-chances），无区域时使用。空则退回 fallbackMin/fallbackMax 均匀随机。 */
    private Map<Integer, Integer> globalLevelChances = Collections.emptyMap();
    private Set<org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason> infernalSpawnReasons = Set.of(
            org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL,
            org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER,
            org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.PATROL,
            org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.REINFORCEMENTS
    );

    public ConfigLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 从 config.yml 加载/重载全部配置（含技能参数、权重、区域等）。 */
    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();

        File mobNameFile = new File(plugin.getDataFolder(), "mob_name.yml");
        if (!mobNameFile.exists()) plugin.saveResource("mob_name.yml", false);
        File skillNameFile = new File(plugin.getDataFolder(), "skill_name.yml");
        if (!skillNameFile.exists()) plugin.saveResource("skill_name.yml", false);

        debug = config.getBoolean("debug", false);
        expMultiplier = config.getDouble("exp-multiplier", 1.0);
        infernalEyeRange = config.getDouble("infernal-eye.range", 20.0);
        morphControllerRayRange = config.getDouble("morph-controller.ray-range", 8.0);
        enabledWorlds = config.getStringList("enabled-worlds");
        infernalSpawnReasons = loadSpawnReasons();
        if (config.contains("defaults")) {
            ConfigurationSection d = config.getConfigurationSection("defaults");
            levelBase = d.getInt("level.base", 1);
            levelScale = d.getInt("level.scale", 100);
            levelMax = d.getInt("level.max", 100);
            levelFallbackMin = d.getInt("level.fallback-min", 1);
            levelFallbackMax = d.getInt("level.fallback-max", 15);
            globalLevelChances = loadLevelChancesFromSection(d, "level.chances", "levelChance", "levelChances", "level-chance", "level-chances");
            ConfigurationSection infernalSec = d.getConfigurationSection("infernal");
            if (infernalSec != null) {
                infernalAllowTypesDefault = loadEntityTypeSet(infernalSec,
                        "allow-types", "allow", "whitelist", "infernal-allow-types");
                infernalDenyTypesDefault = loadEntityTypeSet(infernalSec,
                        "deny-types", "deny", "blacklist", "infernal-deny-types");
            } else {
                // 兼容旧结构：defaults.infernal-allow-types / defaults.infernal-deny-types
                infernalAllowTypesDefault = loadEntityTypeSet(d,
                        "infernal-allow-types", "infernal-whitelist", "whitelist", "allow");
                infernalDenyTypesDefault = loadEntityTypeSet(d,
                        "infernal-deny-types", "infernal-blacklist", "blacklist", "deny");
            }
            affixCountFormula = d.getString("affix.count-formula", "level");
            affixMin = d.getInt("affix.min", 1);
            affixMax = d.getInt("affix.max", 6);
            affixTierThreshold = d.getInt("affix.tier-threshold", 15);
        } else {
            levelBase = config.getInt("level.base", 1);
            levelScale = config.getInt("level.scale", 100);
            levelMax = config.getInt("level.max", 100);
            levelFallbackMin = config.getInt("level.fallback-min", 1);
            levelFallbackMax = config.getInt("level.fallback-max", 15);
            globalLevelChances = loadLevelChancesFromSection(config, "level.chances", "levelChance", "levelChances", "level-chance", "level-chances");
            infernalAllowTypesDefault = Collections.emptySet();
            infernalDenyTypesDefault = Collections.emptySet();
            affixCountFormula = "tier";
            affixMin = config.getInt("affix.min", 1);
            affixMax = config.getInt("affix.max", 6);
            affixTierThreshold = config.getInt("affix.tier-threshold", 15);
        }

        skillWeights = new HashMap<>();
        if (config.contains("skill-weights")) {
            for (String key : config.getConfigurationSection("skill-weights").getKeys(false)) {
                skillWeights.put(key, config.getInt("skill-weights." + key, 10));
            }
        }

        skillConfigs = new HashMap<>();
        if (config.contains("skills")) {
            for (String key : config.getConfigurationSection("skills").getKeys(false)) {
                String path = "skills." + key;
                SkillConfig sc = SkillConfig.from(config, path, key);
                if (sc != null) skillConfigs.put(key, sc);
            }
        }

        regions = loadRegions();
        presets = loadPresets();
        deathMessageConfig = loadDeathMessageConfig();
        protectedAnimalsConfig = loadProtectedAnimalsConfig();
        mobRegistryConfig = loadMobRegistryConfig();
        skillNames = loadSkillNames();
    }

    /** 从配置文件重新读取技能参数等，等同于 load()，语义上表示“重载”。 */
    public void reload() {
        load();
    }

    private ProtectedAnimalsConfig loadProtectedAnimalsConfig() {
        ConfigurationSection sec = config.getConfigurationSection("protected-animals");
        if (sec == null) {
            return ProtectedAnimalsConfig.disabled();
        }
        boolean enable = sec.getBoolean("enable", true);
        Set<EntityType> types = new HashSet<>();
        for (String raw : sec.getStringList("types")) {
            if (raw == null || raw.isBlank()) continue;
            try {
                types.add(EntityType.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("[protected-animals] 未知实体类型，已跳过: " + raw);
            }
        }
        String message = sec.getString("message", "<bold><red><player_name>欺负炒鸡小动物！");
        boolean clearExp = sec.getBoolean("clear-exp", true);
        Set<EntityType> frozen = types.isEmpty() ? Set.of() : Set.copyOf(types);
        return new ProtectedAnimalsConfig(enable, frozen, message, clearExp);
    }

    private MobRegistryConfig loadMobRegistryConfig() {
        ConfigurationSection sec = config.getConfigurationSection("mob-registry");
        if (sec == null) {
            return new MobRegistryConfig(false, 120, 64, 300, false);
        }
        return new MobRegistryConfig(
                sec.getBoolean("cleanup-enabled", false),
                sec.getInt("cleanup-interval-seconds", 120),
                sec.getDouble("inactive-radius", 64),
                sec.getInt("inactive-seconds", 300),
                sec.getBoolean("kill-on-shutdown", false)
        );
    }

    private DeathMessageConfig loadDeathMessageConfig() {
        ConfigurationSection sec = config.getConfigurationSection("death-messages");
        if (sec == null) {
            return new DeathMessageConfig(false, "&finfernal", "拳头",
                    Map.of(), Map.of(), List.of(), Map.of(), false, List.of(), false, "enchanted", List.of(),
                    "<green>", "<dark_red>", 11, false, List.of(), 48.0);
        }
        boolean enable = sec.getBoolean("enable", true);
        String namePrefix = sec.getString("name-prefix", "&finfernal");
        String defaultWeapon = sec.getString("default-weapon", "拳头");

        Map<Integer, String> levelPrefixes = new HashMap<>();
        if (sec.contains("level-prefixes")) {
            ConfigurationSection lp = sec.getConfigurationSection("level-prefixes");
            if (lp != null) {
                for (String key : lp.getKeys(false)) {
                    try {
                        levelPrefixes.put(Integer.parseInt(key), lp.getString(key, "&f初级"));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        if (levelPrefixes.isEmpty()) levelPrefixes.put(1, "&f初级");

        Map<Integer, String> levelTierColors = new HashMap<>();
        if (sec.contains("level-tier-colors")) {
            ConfigurationSection ltc = sec.getConfigurationSection("level-tier-colors");
            if (ltc != null) {
                for (String key : ltc.getKeys(false)) {
                    try {
                        levelTierColors.put(Integer.parseInt(key), ltc.getString(key, "<white>"));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        List<String> messages = sec.getStringList("messages");
        if (messages.isEmpty()) messages = List.of("&e<player> &f杀死了 &r<mob>&f!");

        Map<String, String> mobNames = loadMobNames();
        boolean slainByEnable = sec.getBoolean("slain-by.enable", true);
        List<String> slainByMessages = sec.getStringList("slain-by.messages");
        if (slainByMessages.isEmpty()) slainByMessages = List.of("<gray><player></gray> was slain by <mob>");
        ConfigurationSection sb = sec.getConfigurationSection("slain-by");
        boolean slainByWithWeaponEnable = sb != null && sb.getBoolean("with-weapon.enable", true);
        String slainByWithWeaponWhen = sb != null ? sb.getString("with-weapon.when", "enchanted") : "enchanted";
        List<String> slainByWithWeaponMessages = sb != null && sb.contains("with-weapon.messages")
                ? sb.getStringList("with-weapon.messages") : List.of();
        String playerColorNormal = sec.getString("player-color-normal", "<green>");
        String playerColorOp = sec.getString("player-color-op", "<dark_red>");
        int globalBroadcastLevelThreshold = sec.getInt("global-broadcast-level-threshold", 11);

        ConfigurationSection ksSec = sec.getConfigurationSection("kill-steal");
        boolean killStealEnable = ksSec != null && ksSec.getBoolean("enable", true);
        List<String> killStealMessages = ksSec != null ? ksSec.getStringList("messages") : List.of();
        if (killStealMessages.isEmpty()) killStealMessages = List.of(
                "<nearest_player><white>附近的</white><mob><white>被</white><source><white>抢人头了！</white>");
        double killStealRange = ksSec != null ? ksSec.getDouble("range", 48.0) : 48.0;

        return new DeathMessageConfig(enable, namePrefix, defaultWeapon,
                levelPrefixes, levelTierColors, messages, mobNames, slainByEnable, slainByMessages,
                slainByWithWeaponEnable, slainByWithWeaponWhen, slainByWithWeaponMessages,
                playerColorNormal, playerColorOp, globalBroadcastLevelThreshold,
                killStealEnable, killStealMessages, killStealRange);
    }

    /** 从 skill_name.yml 加载技能 id → MiniMessage 显示名，未配置则返回 null 表示用 config 或 id。 */
    private Map<String, String> loadSkillNames() {
        Map<String, String> out = new HashMap<>();
        File f = new File(plugin.getDataFolder(), "skill_name.yml");
        if (!f.isFile()) return out;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
        for (String key : yml.getKeys(false)) {
            String val = yml.getString(key);
            if (val != null && !val.isEmpty()) out.put(key, val);
        }
        return out;
    }

    /** 获取技能显示名：优先 skill_name.yml，否则 config display，否则 id。 */
    public String getSkillDisplay(String skillId, SkillConfig skillConfig) {
        if (skillNames != null && skillNames.containsKey(skillId)) return skillNames.get(skillId);
        if (skillConfig != null) return skillConfig.getDisplay();
        return skillId != null ? skillId : "";
    }

    /** 从插件数据目录的 mob_name.yml 加载实体类型 → 中文显示名。 */
    private Map<String, String> loadMobNames() {
        Map<String, String> out = new HashMap<>();
        File f = new File(plugin.getDataFolder(), "mob_name.yml");
        if (!f.isFile()) return out;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
        for (String key : yml.getKeys(false)) {
            String val = yml.getString(key);
            if (val != null && !val.isEmpty()) out.put(key, val);
        }
        return out;
    }

    private List<RegionConfig> loadRegions() {
        List<RegionConfig> list = new ArrayList<>();
        ConfigurationSection sec = config.getConfigurationSection("regions");
        if (sec == null) return list;
        for (String id : sec.getKeys(false)) {
            String path = "regions." + id;
            ConfigurationSection r = config.getConfigurationSection(path);
            if (r == null) continue;
            String world = r.getString("world");
            if (world == null) continue;

            int minX = getCoord(r, "min", "x", 0);
            int minY = getCoord(r, "min", "y", 0);
            int minZ = getCoord(r, "min", "z", 0);
            int maxX = getCoord(r, "max", "x", 0);
            int maxY = getCoord(r, "max", "y", 256);
            int maxZ = getCoord(r, "max", "z", 0);

            int levelMin = r.getInt("level-min", 1);
            int levelMax = r.getInt("level-max", 100);
            List<RegionConfig.LevelRange> levelRanges = loadLevelRanges(r);
            Map<Integer, Integer> levelChances = loadLevelChancesFromSection(r, "level-chances", "levelChance", "levelChances", "level-chance");
            int priority = r.getInt("priority", 0);

            Map<String, Integer> pool = new HashMap<>();
            if (r.contains("skill-pool")) {
                for (String k : r.getConfigurationSection("skill-pool").getKeys(false)) {
                    pool.put(k, r.getInt("skill-pool." + k, 10));
                }
            }

            // 炒鸡怪实体类型黑/白名单（按区域生效）
            Set<EntityType> allowTypes = loadEntityTypeSet(r,
                    "infernal-allow-types", "infernal-whitelist", "whitelist", "allow");
            Set<EntityType> denyTypes = loadEntityTypeSet(r,
                    "infernal-deny-types", "infernal-blacklist", "blacklist", "deny");

            // 区域专属 morph 目标池：morph-types / morph-targets
            List<EntityType> morphTargets = loadEntityTypeList(r,
                    "morph-types", "morph-targets", "morph-pool");

            list.add(new RegionConfig(id, world, minX, minY, minZ, maxX, maxY, maxZ,
                    levelMin, levelMax,
                    levelRanges.isEmpty() ? null : levelRanges,
                    levelChances.isEmpty() ? null : levelChances,
                    pool.isEmpty() ? null : pool,
                    priority,
                    allowTypes, denyTypes, morphTargets));
        }
        return list;
    }

    /**
     * 解析区域 level-ranges 配置段，返回带权重的等级区间列表。
     * 支持两种格式：
     *   列表格式：
     *     level-ranges:
     *       - min: 1
     *         max: 5
     *         weight: 30
     *   映射格式（key 为 "min-max"）：
     *     level-ranges:
     *       "1-5": 30
     *       "6-10": 50
     */
    private List<RegionConfig.LevelRange> loadLevelRanges(ConfigurationSection r) {
        if (r == null || !r.contains("level-ranges")) return Collections.emptyList();

        List<RegionConfig.LevelRange> result = new ArrayList<>();

        // 列表格式
        if (r.isList("level-ranges")) {
            for (Object entry : r.getList("level-ranges")) {
                if (!(entry instanceof java.util.Map<?, ?> map)) continue;
                int min  = toInt(map.get("min"),    1);
                int max  = toInt(map.get("max"),    min);
                int w    = toInt(map.get("weight"), 10);
                if (w > 0) result.add(new RegionConfig.LevelRange(Math.min(min, max), Math.max(min, max), w));
            }
        } else if (r.isConfigurationSection("level-ranges")) {
            // 映射格式："min-max": weight
            ConfigurationSection sec = r.getConfigurationSection("level-ranges");
            for (String key : sec.getKeys(false)) {
                int w = sec.getInt(key, 10);
                if (w <= 0) continue;
                String[] parts = key.split("[\\-~]", 2);
                if (parts.length == 2) {
                    int min = toInt(parts[0].trim(), 1);
                    int max = toInt(parts[1].trim(), min);
                    result.add(new RegionConfig.LevelRange(Math.min(min, max), Math.max(min, max), w));
                } else {
                    int lv = toInt(parts[0].trim(), 1);
                    result.add(new RegionConfig.LevelRange(lv, lv, w));
                }
            }
        }
        return result;
    }

    /**
     * 从 parent 中尝试多个候选 key 名，取第一个非空 section 来解析 level-chances。
     * 支持同时兼容 level-chances / levelChance / levelChances / level-chance 等写法。
     */
    private Map<Integer, Integer> loadLevelChancesFromSection(ConfigurationSection parent, String... keys) {
        for (String key : keys) {
            ConfigurationSection sec = parent.getConfigurationSection(key);
            if (sec != null) return loadLevelChancesSection(sec);
        }
        return Collections.emptyMap();
    }

    /**
     * 解析 level-chances ConfigurationSection（key=等级字符串，value=权重整数）。
     * 支持 '1': 25  或  1: 25 两种 key 写法。
     */
    private Map<Integer, Integer> loadLevelChancesSection(ConfigurationSection sec) {
        if (sec == null) return Collections.emptyMap();
        Map<Integer, Integer> map = new HashMap<>();
        for (String key : sec.getKeys(false)) {
            try {
                int level = Integer.parseInt(key.trim());
                int weight = sec.getInt(key, 0);
                if (level > 0 && weight > 0) map.put(level, weight);
            } catch (NumberFormatException ignored) {}
        }
        return map;
    }

    public Map<Integer, Integer> getGlobalLevelChances() { return globalLevelChances; }

    private static int toInt(Object obj, int def) {
        if (obj instanceof Number n) return n.intValue();
        if (obj instanceof String s) {
            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    private Set<EntityType> loadEntityTypeSet(ConfigurationSection r, String... keys) {
        if (r == null || keys == null || keys.length == 0) return Collections.emptySet();

        List<String> raw = new ArrayList<>();
        for (String k : keys) {
            if (k == null || k.trim().isEmpty()) continue;
            if (!r.contains(k)) continue;
            raw.addAll(r.getStringList(k));
        }

        if (raw.isEmpty()) return Collections.emptySet();

        Set<EntityType> out = new HashSet<>();
        for (String s : raw) {
            if (s == null) continue;
            String t = s.trim();
            if (t.isEmpty()) continue;
            try {
                out.add(EntityType.valueOf(t.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("regions 配置中无法识别 EntityType: " + t + "（keys=" + Arrays.toString(keys) + "）");
            }
        }
        return out;
    }

    private List<EntityType> loadEntityTypeList(ConfigurationSection r, String... keys) {
        if (r == null || keys == null || keys.length == 0) return Collections.emptyList();

        List<String> raw = new ArrayList<>();
        for (String k : keys) {
            if (k == null || k.trim().isEmpty()) continue;
            if (!r.contains(k)) continue;
            raw.addAll(r.getStringList(k));
        }
        if (raw.isEmpty()) return Collections.emptyList();

        List<EntityType> out = new ArrayList<>();
        for (String s : raw) {
            if (s == null) continue;
            String t = s.trim();
            if (t.isEmpty()) continue;
            try {
                EntityType type = EntityType.valueOf(t.toUpperCase(Locale.ROOT));
                if (type != null && type.isSpawnable() && LivingEntity.class.isAssignableFrom(type.getEntityClass())) {
                    out.add(type);
                }
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("regions 配置中无法识别 EntityType: " + t + "（keys=" + Arrays.toString(keys) + "）");
            }
        }
        return out;
    }

    private int getCoord(ConfigurationSection r, String node, String axis, int def) {
        if (r.contains(node + "." + axis)) return r.getInt(node + "." + axis, def);
        if (r.contains(node) && r.isConfigurationSection(node)) {
            return r.getInt(node + "." + axis, def);
        }
        return def;
    }

    private Set<org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason> loadSpawnReasons() {
        List<String> raw = config.getStringList("infernal-spawn-reasons");
        if (raw == null || raw.isEmpty()) {
            return Set.of(
                    org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL,
                    org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER,
                    org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.PATROL,
                    org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.REINFORCEMENTS
            );
        }

        Set<org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason> out = new HashSet<>();
        for (String s : raw) {
            if (s == null || s.isBlank()) continue;
            try {
                out.add(org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.valueOf(s.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("infernal-spawn-reasons 包含无效值: " + s + "（已忽略）");
            }
        }
        if (out.isEmpty()) {
            plugin.getLogger().warning("infernal-spawn-reasons 解析后为空，已回退默认值 NATURAL/SPAWNER/PATROL/REINFORCEMENTS");
            return Set.of(
                    org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL,
                    org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER,
                    org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.PATROL,
                    org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.REINFORCEMENTS
            );
        }
        return out;
    }

    private Map<String, PresetConfig> loadPresets() {
        Map<String, PresetConfig> map = new HashMap<>();
        ConfigurationSection sec = config.getConfigurationSection("presets");
        if (sec == null) return map;
        for (String id : sec.getKeys(false)) {
            String path = "presets." + id;
            ConfigurationSection p = config.getConfigurationSection(path);
            if (p == null) continue;

            String display = p.getString("display", id);
            int level = p.getInt("level", 1);
            List<PresetConfig.SkillEntry> skills = new ArrayList<>();
            if (p.contains("skills")) {
                for (Object o : p.getList("skills", Collections.emptyList())) {
                    String skillId = o instanceof Map ? String.valueOf(((Map<?, ?>) o).get("id")) : String.valueOf(o);
                    if (skillId != null && !skillId.isEmpty() && !"null".equals(skillId)) {
                        skills.add(new PresetConfig.SkillEntry(skillId));
                    }
                }
            }

            List<String> regionIds = Collections.emptyList();
            List<String> worlds = Collections.emptyList();
            double weight = 0;
            if (p.contains("spawn")) {
                ConfigurationSection spawn = p.getConfigurationSection("spawn");
                if (spawn != null) {
                    regionIds = spawn.getStringList("regions");
                    worlds = spawn.getStringList("worlds");
                    weight = spawn.getDouble("weight", 0.05);
                }
            }
            map.put(id, new PresetConfig(id, display, level, skills, regionIds, worlds, weight));
        }
        return map;
    }

    public boolean isWorldEnabled(String worldName) {
        return enabledWorlds != null && enabledWorlds.contains(worldName);
    }

    public List<String> getEnabledWorlds() { return enabledWorlds; }
    public int getLevelBase() { return levelBase; }
    public int getLevelScale() { return levelScale; }
    public int getLevelMax() { return levelMax; }
    public int getLevelFallbackMin() { return levelFallbackMin; }
    public int getLevelFallbackMax() { return levelFallbackMax; }
    public String getAffixCountFormula() { return affixCountFormula; }
    public int getAffixMin() { return affixMin; }
    public int getAffixMax() { return affixMax; }
    public int getAffixTierThreshold() { return affixTierThreshold; }
    public Map<String, Integer> getSkillWeights() { return skillWeights; }
    public SkillConfig getSkillConfig(String skillId) { return skillConfigs != null ? skillConfigs.get(skillId) : null; }
    public Map<String, SkillConfig> getSkillConfigs() { return skillConfigs != null ? new HashMap<>(skillConfigs) : Map.of(); }
    public List<RegionConfig> getRegions() { return regions != null ? new ArrayList<>(regions) : Collections.emptyList(); }
    public Map<String, PresetConfig> getPresets() { return presets != null ? new HashMap<>(presets) : Map.of(); }
    public DeathMessageConfig getDeathMessageConfig() { return deathMessageConfig; }
    public ProtectedAnimalsConfig getProtectedAnimalsConfig() { return protectedAnimalsConfig; }
    public MobRegistryConfig getMobRegistryConfig() { return mobRegistryConfig; }
    public double getExpMultiplier() { return expMultiplier; }
    public double getInfernalEyeRange() { return infernalEyeRange; }

    public double getMorphControllerRayRange() { return morphControllerRayRange; }
    public FileConfiguration getRaw() { return config; }
    public Set<org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason> getInfernalSpawnReasons() { return infernalSpawnReasons; }

    /**
     * 无区域匹配（fallback/defaults）时，实体类型是否允许被炒鸡化。
     * - 命中默认黑名单：禁止
     * - 默认白名单非空：必须命中白名单
     * - 默认白名单为空：不限制（除黑名单外都允许）
     */
    public boolean canInfernalizeInDefaults(EntityType type) {
        if (type == null) return true;
        if (!infernalDenyTypesDefault.isEmpty() && infernalDenyTypesDefault.contains(type)) return false;
        if (!infernalAllowTypesDefault.isEmpty()) return infernalAllowTypesDefault.contains(type);
        return true;
    }

    public boolean isDebug() { return debug; }
    public void setDebug(boolean debug) { this.debug = debug; }
}
