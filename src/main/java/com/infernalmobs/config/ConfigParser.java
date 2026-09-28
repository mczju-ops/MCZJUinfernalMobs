package com.infernalmobs.config;

import com.infernalmobs.registry.SkillRegistry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 新版四个核心配置文件的解析器。所有 YAML 对象只在本类加载期间存活。 */
final class ConfigParser {

    static final int CONFIG_VERSION = 1;
    private static final Set<String> CONFIG_ROOT_KEYS = Set.of(
            "config-version", "debug", "exp-multiplier", "enabled-worlds",
            "infernal-spawn-reasons", "allow-types");
    private static final Set<String> SKILLS_ROOT_KEYS = Set.of("pools", "skills");
    private static final Set<String> REGIONS_ROOT_KEYS = Set.of("base-rules", "regions");
    private static final Set<String> MESSAGES_ROOT_KEYS = Set.of("death-messages", "protected-animals");
    private static final Set<String> OVERRIDE_KEYS = Set.of(
            "levels", "skill-pool", "affix-count", "morph-pool");
    private static final Set<String> REGION_KEYS = Set.of(
            "world", "min", "max", "priority", "overrides");
    private static final Set<String> DEATH_MESSAGE_KEYS = Set.of(
            "enabled", "default-weapon", "level-tier-colors", "player-color-normal",
            "player-color-op", "global-broadcast-level-threshold", "messages",
            "slain-by", "kill-steal");
    private static final Set<String> PROTECTED_ANIMAL_KEYS = Set.of(
            "enabled", "clear-exp", "types", "message");

    private final JavaPlugin plugin;
    private final File dataFolder;
    private final List<ConfigDiagnostic> diagnostics = new ArrayList<>();

    ConfigParser(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = plugin.getDataFolder();
    }

    ParseResult parse() {
        YamlConfiguration root = loadRequired("config.yml");
        YamlConfiguration skillsYaml = loadRequired("skills.yml");
        YamlConfiguration skillsSchema = loadBundled("skills.yml");
        YamlConfiguration regionsYaml = loadRequired("regions.yml");
        YamlConfiguration messagesYaml = loadRequired("messages.yml");
        if (hasErrors()) return new ParseResult(null, diagnostics);

        warnUnknownRootKeys("config.yml", root, CONFIG_ROOT_KEYS);
        warnUnknownRootKeys("skills.yml", skillsYaml, SKILLS_ROOT_KEYS);
        warnUnknownRootKeys("regions.yml", regionsYaml, REGIONS_ROOT_KEYS);
        warnUnknownRootKeys("messages.yml", messagesYaml, MESSAGES_ROOT_KEYS);

        GlobalConfig global = parseGlobal(root);
        SkillsResult skills = parseSkills(skillsYaml, skillsSchema);
        SpawnRules baseRules = parseBaseRules(regionsYaml, skills.pools());
        List<RegionConfig> regions = parseRegions(regionsYaml, skills.pools(), baseRules);
        DeathMessageConfig deathMessages = parseDeathMessages(messagesYaml);
        ProtectedAnimalsConfig protectedAnimals = parseProtectedAnimals(messagesYaml);
        if (hasErrors()) return new ParseResult(null, diagnostics);

        boolean degraded = !diagnostics.isEmpty();
        ConfigSnapshot snapshot = new ConfigSnapshot(
                global, skills.skills(), skills.pools(), baseRules, regions,
                deathMessages, protectedAnimals, degraded, diagnostics);
        return new ParseResult(snapshot, diagnostics);
    }

    private GlobalConfig parseGlobal(YamlConfiguration yaml) {
        int version = yaml.getInt("config-version", -1);
        if (version != CONFIG_VERSION) {
            diagnostics.add(ConfigDiagnostic.error("config.yml:config-version",
                    "仅支持版本 " + CONFIG_VERSION + "，实际为 " + version));
        }

        double expMultiplier = yaml.getDouble("exp-multiplier", 1.0);
        if (expMultiplier < 0) {
            warn("config.yml:exp-multiplier", "不能小于 0，已使用 1.0");
            expMultiplier = 1.0;
        }

        List<String> worlds = yaml.getStringList("enabled-worlds").stream()
                .map(String::trim).filter(value -> !value.isEmpty()).distinct().toList();
        if (worlds.isEmpty()) warn("config.yml:enabled-worlds", "列表为空，自动生成不会在任何世界启用");

        Set<CreatureSpawnEvent.SpawnReason> reasons = new LinkedHashSet<>();
        for (String raw : yaml.getStringList("infernal-spawn-reasons")) {
            try {
                reasons.add(CreatureSpawnEvent.SpawnReason.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                warn("config.yml:infernal-spawn-reasons", "无法识别生成原因 " + raw + "，已跳过");
            }
        }
        if (reasons.isEmpty()) {
            warn("config.yml:infernal-spawn-reasons", "没有有效值，已使用安全默认值 NATURAL、SPAWNER");
            reasons.add(CreatureSpawnEvent.SpawnReason.NATURAL);
            reasons.add(CreatureSpawnEvent.SpawnReason.SPAWNER);
        }

        Set<EntityType> allowTypes = parseEntityTypeSet(
                yaml.getStringList("allow-types"), "config.yml:allow-types", false);
        return new GlobalConfig(version, yaml.getBoolean("debug", false), expMultiplier,
                worlds, reasons, allowTypes);
    }

    private SkillsResult parseSkills(YamlConfiguration yaml, YamlConfiguration schemaYaml) {
        LinkedHashMap<String, Map<String, Integer>> pools = new LinkedHashMap<>();
        ConfigurationSection poolsSection = yaml.getConfigurationSection("pools");
        if (poolsSection == null) {
            warn("skills.yml:pools", "缺少词条池配置段，已使用空的 default 池");
        } else {
            for (String poolId : poolsSection.getKeys(false)) {
                ConfigurationSection poolSection = poolsSection.getConfigurationSection(poolId);
                if (poolSection == null) {
                    warn("skills.yml:pools." + poolId, "必须是词条权重映射，已跳过");
                    continue;
                }
                LinkedHashMap<String, Integer> weights = new LinkedHashMap<>();
                for (String rawId : poolSection.getKeys(false)) {
                    String skillId = normalizeId(rawId);
                    if (!SkillRegistry.has(skillId)) {
                        warn("skills.yml:pools." + poolId + "." + rawId, "未知词条 ID，已跳过");
                        continue;
                    }
                    int weight = poolSection.getInt(rawId, 0);
                    if (weight <= 0) {
                        warn("skills.yml:pools." + poolId + "." + rawId, "权重必须大于 0，已跳过");
                        continue;
                    }
                    if (weight > 1_000_000) {
                        warn("skills.yml:pools." + poolId + "." + rawId,
                                "权重过大，已限制为 1000000");
                        weight = 1_000_000;
                    }
                    weights.put(skillId, weight);
                }
                String normalizedPoolId = normalizeId(poolId);
                if (pools.containsKey(normalizedPoolId)) {
                    warn("skills.yml:pools." + poolId, "归一化后与已有词条池 ID 重复，已使用后声明的值");
                }
                pools.put(normalizedPoolId, Collections.unmodifiableMap(new LinkedHashMap<>(weights)));
            }
        }
        if (!pools.containsKey("default")) {
            warn("skills.yml:pools.default", "缺少默认词条池，基础规则将无法抽取词条");
            pools.put("default", Map.of());
        }

        LinkedHashMap<String, SkillConfig> skills = new LinkedHashMap<>();
        ConfigurationSection skillsSection = yaml.getConfigurationSection("skills");
        if (skillsSection == null) {
            warn("skills.yml:skills", "缺少技能配置段，全部技能将使用内置安全默认参数");
        } else {
            for (String rawId : skillsSection.getKeys(false)) {
                String skillId = normalizeId(rawId);
                if (!SkillRegistry.has(skillId)) {
                    warn("skills.yml:skills." + rawId, "未知词条定义，已跳过");
                    continue;
                }
                ConfigurationSection section = skillsSection.getConfigurationSection(rawId);
                if (section == null) {
                    warn("skills.yml:skills." + rawId, "必须是配置映射，已使用内置安全默认参数");
                    ConfigurationSection schema = schemaYaml.getConfigurationSection("skills." + skillId);
                    skills.put(skillId, skillFromSchema(skillId, schema));
                    continue;
                }
                if (section.contains("type")) {
                    warn("skills.yml:skills." + rawId + ".type", "新版不接受 type，技能类型由源码定义");
                }
                ConfigurationSection schema = schemaYaml.getConfigurationSection("skills." + skillId);
                String display = readDisplay(section, schema, skillId);
                Map<String, Object> values = schema != null
                        ? buildValues(section, schema, "skills.yml:skills." + skillId,
                                Set.of("display", "type"))
                        : sectionToMap(section, Set.of("display", "type"));
                if (skills.containsKey(skillId)) {
                    warn("skills.yml:skills." + rawId, "归一化后与已有技能 ID 重复，已使用后声明的值");
                }
                skills.put(skillId, new SkillConfig(skillId, display, values));
            }
        }

        SkillRegistry.getAll().forEach(skill -> {
            if (!skills.containsKey(skill.getId())) {
                warn("skills.yml:skills." + skill.getId(), "缺少技能配置，已使用内置安全默认参数");
                ConfigurationSection schema = schemaYaml.getConfigurationSection("skills." + skill.getId());
                skills.put(skill.getId(), skillFromSchema(skill.getId(), schema));
            }
        });
        return new SkillsResult(skills, pools);
    }

    private SpawnRules parseBaseRules(YamlConfiguration yaml, Map<String, Map<String, Integer>> pools) {
        ConfigurationSection section = yaml.getConfigurationSection("base-rules");
        if (section == null) {
            diagnostics.add(ConfigDiagnostic.error("regions.yml:base-rules", "缺少基础生成规则"));
            return safeSpawnRules();
        }
        warnUnknownKeys("regions.yml:base-rules", section, OVERRIDE_KEYS);
        return parseRules(section, "regions.yml:base-rules", pools, null, true);
    }

    private List<RegionConfig> parseRegions(YamlConfiguration yaml,
                                             Map<String, Map<String, Integer>> pools,
                                             SpawnRules baseRules) {
        ConfigurationSection regionsSection = yaml.getConfigurationSection("regions");
        if (regionsSection == null) {
            if (yaml.contains("regions")) warn("regions.yml:regions", "必须是配置映射，已忽略全部特殊区域");
            return List.of();
        }

        List<RegionConfig> regions = new ArrayList<>();
        Set<String> regionIds = new LinkedHashSet<>();
        int order = 0;
        for (String id : regionsSection.getKeys(false)) {
            String path = "regions.yml:regions." + id;
            ConfigurationSection section = regionsSection.getConfigurationSection(id);
            if (section == null) {
                warn(path, "区域必须是配置映射，已跳过");
                continue;
            }
            try {
                String normalizedId = normalizeId(id);
                warnUnknownKeys(path, section, REGION_KEYS);
                String world = section.getString("world");
                if (world == null || world.isBlank()) throw new IllegalArgumentException("缺少 world");
                int[] min = parseVector(section, "min", path);
                int[] max = parseVector(section, "max", path);
                ConfigurationSection overrides = section.getConfigurationSection("overrides");
                if (overrides != null) warnUnknownKeys(path + ".overrides", overrides, OVERRIDE_KEYS);
                SpawnRules rules = overrides == null
                        ? baseRules
                        : parseRules(overrides, path + ".overrides", pools, baseRules, false);
                if (!regionIds.add(normalizedId)) {
                    throw new IllegalArgumentException("归一化后与已有有效区域 ID 重复");
                }
                regions.add(new RegionConfig(normalizedId, world.trim(),
                        min[0], min[1], min[2], max[0], max[1], max[2],
                        section.getInt("priority", 0), order++, rules));
            } catch (IllegalArgumentException ex) {
                warn(path, ex.getMessage() + "，已跳过整个区域");
            }
        }
        return regions;
    }

    private SpawnRules parseRules(ConfigurationSection section,
                                  String path,
                                  Map<String, Map<String, Integer>> pools,
                                  SpawnRules inherited,
                                  boolean base) {
        Map<Integer, Integer> levels = inherited != null ? inherited.levelWeights() : Map.of();
        if (base || section.contains("levels")) {
            ConfigurationSection levelsSection = section.getConfigurationSection("levels");
            if (levelsSection != null) warnUnknownKeys(path + ".levels", levelsSection, Set.of("weights"));
            ConfigurationSection weights = section.getConfigurationSection("levels.weights");
            levels = parseLevelWeights(weights, path + ".levels.weights");
            if (levels.isEmpty()) {
                if (!base) throw new IllegalArgumentException("levels.weights 没有有效权重");
                warn(path + ".levels.weights", "没有有效权重，已使用 1 级权重 1");
                levels = Map.of(1, 1);
            }
        }

        Map<String, Integer> skillPool = inherited != null ? inherited.skillPool() : Map.of();
        if (base || section.contains("skill-pool")) {
            String poolId = normalizeId(section.getString("skill-pool", "default"));
            skillPool = pools.get(poolId);
            if (skillPool == null) {
                if (!base) throw new IllegalArgumentException("引用了不存在的词条池 " + poolId);
                warn(path + ".skill-pool", "词条池 " + poolId + " 不存在，已使用空池");
                skillPool = Map.of();
            }
        }

        AffixCountRule affixCount = inherited != null ? inherited.affixCount() : new AffixCountRule(1, 1);
        if (base || section.contains("affix-count")) {
            ConfigurationSection count = section.getConfigurationSection("affix-count");
            if (count == null) {
                if (!base) throw new IllegalArgumentException("affix-count 必须是配置映射");
                warn(path + ".affix-count", "缺少词条数量规则，已使用 min=1、max=1");
            } else {
                warnUnknownKeys(path + ".affix-count", count, Set.of("formula", "min", "max"));
                String formula = count.getString("formula", "level");
                if (!"level".equalsIgnoreCase(formula)) {
                    if (!base) throw new IllegalArgumentException("affix-count.formula 只支持 level");
                    warn(path + ".affix-count.formula", "只支持 level，已回退为 level");
                }
                int min = count.getInt("min", 1);
                if (min < 0) {
                    warn(path + ".affix-count.min", "不能小于 0，已调整为 0");
                    min = 0;
                }
                int max = count.getInt("max", 15);
                if (max < min) {
                    if (!base) throw new IllegalArgumentException("affix-count.max 不能小于 min");
                    warn(path + ".affix-count.max", "不能小于 min，已调整为 " + min);
                    max = min;
                }
                affixCount = new AffixCountRule(min, max);
            }
        }

        List<EntityType> morphPool = inherited != null ? inherited.morphPool() : List.of();
        if (base || section.contains("morph-pool")) {
            morphPool = parseEntityTypeList(section.getStringList("morph-pool"),
                    path + ".morph-pool", true);
        }
        return new SpawnRules(levels, skillPool, affixCount, morphPool);
    }

    private DeathMessageConfig parseDeathMessages(YamlConfiguration yaml) {
        ConfigurationSection section = yaml.getConfigurationSection("death-messages");
        if (section == null) {
            warn("messages.yml:death-messages", "缺少死亡播报配置段，已安全禁用");
            return safeDeathMessages();
        }
        warnUnknownKeys("messages.yml:death-messages", section, DEATH_MESSAGE_KEYS);

        Map<Integer, String> colors = parseIntegerStringMap(
                section.getConfigurationSection("level-tier-colors"),
                "messages.yml:death-messages.level-tier-colors");
        List<String> messages = nonEmptyOr(section.getStringList("messages"),
                List.of("<player><white>杀死了</white><mob><white>!</white>"));
        ConfigurationSection slainBy = section.getConfigurationSection("slain-by");
        if (slainBy != null) warnUnknownKeys("messages.yml:death-messages.slain-by",
                slainBy, Set.of("enabled", "messages", "with-weapon"));
        ConfigurationSection withWeapon = slainBy != null
                ? slainBy.getConfigurationSection("with-weapon") : null;
        if (withWeapon != null) warnUnknownKeys("messages.yml:death-messages.slain-by.with-weapon",
                withWeapon, Set.of("enabled", "when", "messages"));
        ConfigurationSection killSteal = section.getConfigurationSection("kill-steal");
        if (killSteal != null) warnUnknownKeys("messages.yml:death-messages.kill-steal",
                killSteal, Set.of("enabled", "range", "messages"));

        return new DeathMessageConfig(
                section.getBoolean("enabled", true),
                section.getString("default-weapon", "拳头"),
                colors, messages,
                slainBy != null && slainBy.getBoolean("enabled", true),
                slainBy != null ? nonEmptyOr(slainBy.getStringList("messages"),
                        List.of("<player><white>被</white><mob><white>击杀!</white>")) : List.of(),
                withWeapon != null && withWeapon.getBoolean("enabled", true),
                withWeapon != null ? withWeapon.getString("when", "enchanted") : "enchanted",
                withWeapon != null ? withWeapon.getStringList("messages") : List.of(),
                section.getString("player-color-normal", "<green>"),
                section.getString("player-color-op", "<dark_red>"),
                Math.max(1, section.getInt("global-broadcast-level-threshold", 11)),
                killSteal != null && killSteal.getBoolean("enabled", false),
                killSteal != null ? nonEmptyOr(killSteal.getStringList("messages"),
                        List.of("<nearest_player><white>附近的</white><mob><white>被</white><source><white>抢人头了！</white>")) : List.of(),
                killSteal != null ? Math.max(0, killSteal.getDouble("range", 48.0)) : 48.0);
    }

    private ProtectedAnimalsConfig parseProtectedAnimals(YamlConfiguration yaml) {
        ConfigurationSection section = yaml.getConfigurationSection("protected-animals");
        if (section == null) {
            warn("messages.yml:protected-animals", "缺少保护动物配置段，已安全禁用");
            return ProtectedAnimalsConfig.disabled();
        }
        warnUnknownKeys("messages.yml:protected-animals", section, PROTECTED_ANIMAL_KEYS);
        Set<EntityType> types = parseEntityTypeSet(section.getStringList("types"),
                "messages.yml:protected-animals.types", false);
        return new ProtectedAnimalsConfig(
                section.getBoolean("enabled", true), types,
                section.getString("message", "<bold><red><player_name>欺负炒鸡小动物！"),
                section.getBoolean("clear-exp", true));
    }

    private YamlConfiguration loadRequired(String name) {
        File file = new File(dataFolder, name);
        if (!file.isFile()) {
            diagnostics.add(ConfigDiagnostic.error(name, "必需配置文件不存在"));
            return new YamlConfiguration();
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            diagnostics.add(ConfigDiagnostic.error(name, "无法读取配置文件：" + ex.getMessage()));
        }
        return yaml;
    }

    private YamlConfiguration loadBundled(String name) {
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStream input = plugin.getResource(name)) {
            if (input == null) {
                diagnostics.add(ConfigDiagnostic.error(name, "jar 内缺少默认资源，无法建立配置 schema"));
                return yaml;
            }
            yaml.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (IOException | InvalidConfigurationException ex) {
            diagnostics.add(ConfigDiagnostic.error(name, "jar 内默认资源无法读取：" + ex.getMessage()));
        }
        return yaml;
    }

    private Map<String, Object> buildValues(ConfigurationSection actual,
                                            ConfigurationSection schema,
                                            String path,
                                            Set<String> excluded) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (String key : actual.getKeys(false)) {
            if (excluded.contains(key)) continue;
            if (!schema.contains(key)) {
                warn(path + "." + key, "未知技能参数");
            }
        }
        for (String key : schema.getKeys(false)) {
            if (excluded.contains(key)) continue;
            Object schemaValue = schema.get(key);
            if (!actual.contains(key)) {
                warn(path + "." + key, "参数缺失，已使用内置安全默认值");
                result.put(key, normalizeYamlValue(schemaValue));
                continue;
            }
            Object actualValue = actual.get(key);
            if (!sameValueKind(actualValue, schemaValue)) {
                warn(path + "." + key, "参数类型不正确，已使用内置安全默认值");
                result.put(key, normalizeYamlValue(schemaValue));
                continue;
            }
            if (!isSemanticallyValid(key, actualValue)) {
                warn(path + "." + key, "参数超出安全范围，已使用内置安全默认值");
                result.put(key, normalizeYamlValue(schemaValue));
                continue;
            }
            if (actualValue instanceof ConfigurationSection actualSection
                    && schemaValue instanceof ConfigurationSection schemaSection) {
                result.put(key, buildValues(actualSection, schemaSection,
                        path + "." + key, Set.of()));
            } else {
                result.put(key, normalizeYamlValue(actualValue));
            }
        }
        return result;
    }

    private boolean sameValueKind(Object actual, Object schema) {
        if (actual == null || schema == null) return actual == schema;
        if (actual instanceof Number && schema instanceof Number) return true;
        if (actual instanceof ConfigurationSection && schema instanceof ConfigurationSection) return true;
        if (actual instanceof List<?> && schema instanceof List<?>) return true;
        return actual.getClass().equals(schema.getClass());
    }

    private boolean isSemanticallyValid(String key, Object value) {
        if (!(value instanceof Number number)) return true;
        double numeric = number.doubleValue();
        if (key.endsWith("chance")) return numeric >= 0 && numeric <= 1;
        if ("duration-ticks".equals(key) || "resistance-duration-ticks".equals(key)) {
            return numeric >= -1;
        }
        if (key.endsWith("-ticks")) return numeric >= 0;
        if (key.endsWith("amplifier")) return numeric >= 0;
        return switch (key) {
            case "armor-tier" -> numeric >= 0 && numeric <= 4;
            case "arrow-count" -> numeric >= 1 && numeric <= 8;
            case "power" -> numeric >= 0 && numeric <= 4;
            case "giant-sphere-thickness" -> numeric >= 0 && numeric <= 1;
            case "no-baby-scale" -> numeric > 0;
            default -> true;
        };
    }

    private String readDisplay(ConfigurationSection actual,
                               ConfigurationSection schema,
                               String skillId) {
        Object value = actual.get("display");
        if (value instanceof String display && !display.isBlank()) return display;
        warn("skills.yml:skills." + skillId + ".display", "显示名缺失或类型不正确，已使用内置默认值");
        return schema != null ? schema.getString("display", skillId) : skillId;
    }

    private SkillConfig skillFromSchema(String skillId, ConfigurationSection schema) {
        if (schema == null) return new SkillConfig(skillId, skillId, Map.of());
        return new SkillConfig(skillId, schema.getString("display", skillId),
                sectionToMap(schema, Set.of("display", "type")));
    }

    private Object normalizeYamlValue(Object value) {
        if (value instanceof ConfigurationSection section) return sectionToMap(section, Set.of());
        return value;
    }

    private Map<Integer, Integer> parseLevelWeights(ConfigurationSection section, String path) {
        if (section == null) return Map.of();
        LinkedHashMap<Integer, Integer> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            try {
                int level = Integer.parseInt(key);
                int weight = section.getInt(key, 0);
                if (level < 1 || weight <= 0) {
                    warn(path + "." + key, "等级和权重都必须大于 0，已跳过");
                    continue;
                }
                result.put(level, weight);
            } catch (NumberFormatException ex) {
                warn(path + "." + key, "等级键必须是整数，已跳过");
            }
        }
        return result;
    }

    private int[] parseVector(ConfigurationSection section, String key, String path) {
        List<Integer> values = section.getIntegerList(key);
        if (values.size() != 3) throw new IllegalArgumentException(key + " 必须是 [x, y, z] 三整数列表");
        return new int[] { values.get(0), values.get(1), values.get(2) };
    }

    private Set<EntityType> parseEntityTypeSet(List<String> raw, String path, boolean livingOnly) {
        return Set.copyOf(parseEntityTypeList(raw, path, livingOnly));
    }

    private List<EntityType> parseEntityTypeList(List<String> raw, String path, boolean livingOnly) {
        LinkedHashSet<EntityType> result = new LinkedHashSet<>();
        for (String value : raw) {
            try {
                EntityType type = EntityType.valueOf(value.trim().toUpperCase(Locale.ROOT));
                if (!type.isSpawnable() || type.getEntityClass() == null
                        || livingOnly && !LivingEntity.class.isAssignableFrom(type.getEntityClass())) {
                    warn(path, value + " 不是可生成的生物类型，已跳过");
                    continue;
                }
                result.add(type);
            } catch (IllegalArgumentException ex) {
                warn(path, "无法识别实体类型 " + value + "，已跳过");
            }
        }
        return List.copyOf(result);
    }

    private Map<Integer, String> parseIntegerStringMap(ConfigurationSection section, String path) {
        if (section == null) return Map.of();
        LinkedHashMap<Integer, String> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            try {
                result.put(Integer.parseInt(key), section.getString(key, "<white>"));
            } catch (NumberFormatException ex) {
                warn(path + "." + key, "键必须是整数，已跳过");
            }
        }
        return result;
    }

    private Map<String, Object> sectionToMap(ConfigurationSection section, Set<String> excluded) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            if (excluded.contains(key)) continue;
            Object value = section.get(key);
            result.put(key, value instanceof ConfigurationSection nested
                    ? sectionToMap(nested, Set.of())
                    : value);
        }
        return result;
    }

    private void warnUnknownRootKeys(String file, ConfigurationSection section, Set<String> known) {
        warnUnknownKeys(file, section, known);
    }

    private void warnUnknownKeys(String path, ConfigurationSection section, Set<String> known) {
        String separator = path.contains(":") ? "." : ":";
        for (String key : section.getKeys(false)) {
            if (!known.contains(key)) warn(path + separator + key, "未知配置键");
        }
    }

    private boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.severity() == ConfigDiagnostic.Severity.ERROR);
    }

    private void warn(String path, String message) {
        diagnostics.add(ConfigDiagnostic.warning(path, message));
    }

    private static String normalizeId(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    private static List<String> nonEmptyOr(List<String> values, List<String> fallback) {
        return values == null || values.isEmpty() ? fallback : List.copyOf(values);
    }

    static SpawnRules safeSpawnRules() {
        return new SpawnRules(Map.of(1, 1), Map.of(), new AffixCountRule(0, 0), List.of());
    }

    static DeathMessageConfig safeDeathMessages() {
        return new DeathMessageConfig(false, "拳头",
                Map.of(), List.of(), false, List.of(),
                false, "enchanted", List.of(), "<green>", "<dark_red>",
                11, false, List.of(), 48.0);
    }

    record ParseResult(ConfigSnapshot snapshot, List<ConfigDiagnostic> diagnostics) {
        ParseResult {
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private record SkillsResult(Map<String, SkillConfig> skills,
                                Map<String, Map<String, Integer>> pools) {}
}
