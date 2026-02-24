package com.infernalmobs.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
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
    private String affixCountFormula;
    private int affixMin;
    private int affixMax;
    private int affixTierThreshold;
    private Map<String, Integer> skillWeights;
    private Map<String, SkillConfig> skillConfigs;
    private List<RegionConfig> regions;
    private Map<String, PresetConfig> presets;
    private DeathMessageConfig deathMessageConfig;
    private MobRegistryConfig mobRegistryConfig;
    private boolean debug;

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

        debug = config.getBoolean("debug", false);
        enabledWorlds = config.getStringList("enabled-worlds");
        if (config.contains("defaults")) {
            ConfigurationSection d = config.getConfigurationSection("defaults");
            levelBase = d.getInt("level.base", 1);
            levelScale = d.getInt("level.scale", 100);
            levelMax = d.getInt("level.max", 100);
            levelFallbackMin = d.getInt("level.fallback-min", 1);
            levelFallbackMax = d.getInt("level.fallback-max", 15);
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
        mobRegistryConfig = loadMobRegistryConfig();
    }

    /** 从配置文件重新读取技能参数等，等同于 load()，语义上表示“重载”。 */
    public void reload() {
        load();
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
                    Map.of(1, "&f初级"), List.of(), Map.of());
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

        List<String> messages = sec.getStringList("messages");
        if (messages.isEmpty()) messages = List.of("&e<player> &f杀死了 &r<mob>&f!");

        Map<String, String> mobNames = loadMobNames();
        return new DeathMessageConfig(enable, namePrefix, defaultWeapon,
                levelPrefixes, messages, mobNames);
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
            int priority = r.getInt("priority", 0);

            Map<String, Integer> pool = new HashMap<>();
            if (r.contains("skill-pool")) {
                for (String k : r.getConfigurationSection("skill-pool").getKeys(false)) {
                    pool.put(k, r.getInt("skill-pool." + k, 10));
                }
            }
            list.add(new RegionConfig(id, world, minX, minY, minZ, maxX, maxY, maxZ,
                    levelMin, levelMax, pool.isEmpty() ? null : pool, priority));
        }
        return list;
    }

    private int getCoord(ConfigurationSection r, String node, String axis, int def) {
        if (r.contains(node + "." + axis)) return r.getInt(node + "." + axis, def);
        if (r.contains(node) && r.isConfigurationSection(node)) {
            return r.getInt(node + "." + axis, def);
        }
        return def;
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
    public MobRegistryConfig getMobRegistryConfig() { return mobRegistryConfig; }
    public FileConfiguration getRaw() { return config; }

    public boolean isDebug() { return debug; }
    public void setDebug(boolean debug) { this.debug = debug; }
}
