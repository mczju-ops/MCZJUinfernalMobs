package com.infernalmobs.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 新版 loot/ 配置解析器。所有等级池在加载阶段一次性解析。 */
public final class LootConfigParser {

    private static final Set<String> SETTINGS_KEYS = Set.of(
            "enabled", "replace-vanilla-drops", "rotation", "drop-times", "special");
    private static final Set<String> ROTATION_KEYS = Set.of("enabled", "sets");
    private static final Set<String> SPECIAL_KEYS = Set.of("enabled", "item-id", "rates");
    private static final Set<String> REWARD_KEYS = Set.of(
            "id", "amount", "weight", "commands", "broadcast", "broadcast-message", "rotation-set");
    private static final Set<String> ULTIMATE_LOOT_ROOT_KEYS = Set.of("enabled", "pity", "loot");
    private static final Set<String> PITY_KEYS = Set.of("point-threshold");
    private static final Set<String> LOOT_RULE_KEYS = Set.of(
            "entity-types", "exclude-mama-children", "level-min", "level-max", "levels", "reward");
    private static final Set<String> LOOT_REWARD_KEYS = Set.of(
            "item-id", "amount", "commands", "broadcast", "broadcast-message");

    private final File lootFolder;
    private final List<ConfigDiagnostic> diagnostics = new ArrayList<>();
    private boolean fatal;

    public LootConfigParser(File dataFolder) {
        this.lootFolder = new File(dataFolder, "loot");
    }

    public ParseResult parse() {
        YamlConfiguration settings = loadRequired(new File(lootFolder, "settings.yml"), "loot/settings.yml");
        YamlConfiguration ultimateLoot = loadRequired(new File(lootFolder, "ultimate-loot.yml"), "loot/ultimate-loot.yml");
        File levelsFolder = new File(lootFolder, "levels");
        if (!levelsFolder.isDirectory()) {
            error("loot/levels", "缺少等级奖励目录");
        }
        if (fatal) return new ParseResult(null, null, diagnostics);

        LootSettings parsedSettings = parseSettings(settings);
        Map<Integer, List<LootConfig.RewardEntry>> levelRewards = parseLevelRewards(levelsFolder,
                parsedSettings.rotationEnabled(), parsedSettings.rotationSets());
        LootConfig lootConfig = new LootConfig(
                parsedSettings.enabled(), parsedSettings.replaceVanillaDrops(),
                parsedSettings.rotationEnabled(), parsedSettings.rotationSets(),
                parsedSettings.dropTimes(), parsedSettings.fallbackDropTimes(),
                levelRewards, parsedSettings.specialLoot());
        UltimateLootConfig ultimateLootConfig = parseUltimateLoot(ultimateLoot);
        return new ParseResult(lootConfig, ultimateLootConfig, diagnostics);
    }

    private LootSettings parseSettings(YamlConfiguration yaml) {
        warnUnknown(yaml, SETTINGS_KEYS, "loot/settings.yml");
        boolean enabled = bool(yaml, "enabled", false, "loot/settings.yml:enabled");
        boolean replace = bool(yaml, "replace-vanilla-drops", false,
                "loot/settings.yml:replace-vanilla-drops");

        ConfigurationSection rotation = yaml.getConfigurationSection("rotation");
        boolean rotationEnabled = false;
        int rotationSets = 1;
        if (rotation != null) {
            warnUnknown(rotation, ROTATION_KEYS, "loot/settings.yml:rotation");
            rotationEnabled = bool(rotation, "enabled", false, "loot/settings.yml:rotation.enabled");
            rotationSets = positiveInt(rotation, "sets", 1, "loot/settings.yml:rotation.sets");
        } else if (yaml.contains("rotation")) {
            warning("loot/settings.yml:rotation", "必须是配置段，已关闭等级奖励轮换");
        }

        Map<Integer, LootConfig.DropTimes> dropTimes = new LinkedHashMap<>();
        LootConfig.DropTimes fallback = new LootConfig.DropTimes(1, 1);
        ConfigurationSection times = yaml.getConfigurationSection("drop-times");
        if (times == null) {
            warning("loot/settings.yml:drop-times", "缺失或不是配置段，所有等级每次抽取 1 次");
        } else {
            for (String key : times.getKeys(false)) {
                if ("fallback".equals(key)) {
                    LootConfig.DropTimes parsed = parseDropTimes(times.get(key),
                            "loot/settings.yml:drop-times.fallback");
                    if (parsed != null) fallback = parsed;
                    continue;
                }
                Integer level = parsePositiveInteger(key);
                if (level == null) {
                    warning("loot/settings.yml:drop-times." + key, "未知键，已忽略");
                    continue;
                }
                LootConfig.DropTimes parsed = parseDropTimes(times.get(key),
                        "loot/settings.yml:drop-times." + key);
                if (parsed != null) dropTimes.put(level, parsed);
            }
        }

        SpecialLootConfig special = parseSpecial(yaml);
        return new LootSettings(enabled, replace, rotationEnabled, rotationSets,
                dropTimes, fallback, special);
    }

    private SpecialLootConfig parseSpecial(YamlConfiguration yaml) {
        ConfigurationSection section = yaml.getConfigurationSection("special");
        if (section == null) {
            if (yaml.contains("special")) {
                warning("loot/settings.yml:special", "必须是配置段，已关闭特殊实体掉落");
            }
            return SpecialLootConfig.DISABLED;
        }
        warnUnknown(section, SPECIAL_KEYS, "loot/settings.yml:special");
        boolean enabled = bool(section, "enabled", false, "loot/settings.yml:special.enabled");
        String itemId = string(section, "item-id", "", "loot/settings.yml:special.item-id");
        if (enabled && itemId.isBlank()) {
            warning("loot/settings.yml:special.item-id", "启用时不能为空，已关闭特殊实体掉落");
            return SpecialLootConfig.DISABLED;
        }
        Map<String, Double> rates = new LinkedHashMap<>();
        ConfigurationSection rateSection = section.getConfigurationSection("rates");
        if (rateSection != null) {
            for (String rawType : rateSection.getKeys(false)) {
                String type = rawType.toUpperCase(Locale.ROOT);
                try {
                    EntityType.valueOf(type);
                } catch (IllegalArgumentException ex) {
                    warning("loot/settings.yml:special.rates." + rawType, "未知实体类型，已忽略");
                    continue;
                }
                Object rawRate = rateSection.get(rawType);
                if (!(rawRate instanceof Number number) || !Double.isFinite(number.doubleValue())
                        || number.doubleValue() <= 0) {
                    warning("loot/settings.yml:special.rates." + rawType, "必须是大于 0 的有限数字，已忽略");
                    continue;
                }
                rates.put(type, number.doubleValue());
            }
        } else if (enabled) {
            warning("loot/settings.yml:special.rates", "缺失或不是配置段，特殊实体掉落不会触发");
        }
        return new SpecialLootConfig(enabled, itemId, rates);
    }

    private Map<Integer, List<LootConfig.RewardEntry>> parseLevelRewards(
            File levelsFolder, boolean rotationEnabled, int rotationSets) {
        LinkedHashMap<Integer, List<LootConfig.RewardEntry>> result = new LinkedHashMap<>();
        File[] files = levelsFolder.listFiles((_, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return result;
        List<File> sorted = new ArrayList<>(List.of(files));
        sorted.sort(Comparator.comparing(File::getName));
        for (File file : sorted) {
            String baseName = file.getName().substring(0, file.getName().length() - 4);
            Integer level = parsePositiveInteger(baseName);
            String filePath = "loot/levels/" + file.getName();
            if (level == null) {
                warning(filePath, "文件名必须是正整数等级，已忽略");
                continue;
            }
            YamlConfiguration yaml = loadOptional(file, filePath);
            if (yaml == null) continue;
            warnUnknown(yaml, Set.of("rewards"), filePath);
            List<?> rawRewards = yaml.getList("rewards");
            if (rawRewards == null) {
                warning(filePath + ":rewards", "缺失或不是列表，该等级池为空");
                result.put(level, List.of());
                continue;
            }
            List<LootConfig.RewardEntry> rewards = new ArrayList<>();
            for (int index = 0; index < rawRewards.size(); index++) {
                LootConfig.RewardEntry reward = parseLevelReward(rawRewards.get(index),
                        filePath + ":rewards[" + index + "]", rotationEnabled, rotationSets);
                if (reward != null) rewards.add(reward);
            }
            result.put(level, List.copyOf(rewards));
        }
        return result;
    }

    private LootConfig.RewardEntry parseLevelReward(
            Object raw, String path, boolean rotationEnabled, int rotationSets) {
        if (!(raw instanceof Map<?, ?> map)) {
            warning(path, "必须是配置对象，已跳过");
            return null;
        }
        warnUnknown(map, REWARD_KEYS, path);
        String id = mapString(map, "id", "");
        if (id.isBlank()) {
            warning(path + ".id", "不能为空，已跳过奖励");
            return null;
        }
        int amount = mapPositiveInt(map, "amount", 1, path + ".amount");
        double weight = mapPositiveDouble(map, "weight", 10, path + ".weight");
        if (weight <= 0) return null;
        List<String> commands = stringList(map.get("commands"), path + ".commands");
        boolean broadcast = mapBoolean(map, "broadcast", false, path + ".broadcast");
        String message = mapString(map, "broadcast-message", "");
        Object rawRotations = map.get("rotation-set");
        Set<Integer> rotations = parseRotationSets(rawRotations, rotationEnabled, rotationSets,
                path + ".rotation-set");
        if (rawRotations != null && rotations == null) {
            warning(path + ".rotation-set", "没有有效套号，已跳过奖励");
            return null;
        }
        return new LootConfig.RewardEntry(id, amount, weight, commands, broadcast, message, rotations);
    }

    private UltimateLootConfig parseUltimateLoot(YamlConfiguration yaml) {
        warnUnknown(yaml, ULTIMATE_LOOT_ROOT_KEYS, "loot/ultimate-loot.yml");
        boolean enabled = bool(yaml, "enabled", false, "loot/ultimate-loot.yml:enabled");
        ConfigurationSection pity = yaml.getConfigurationSection("pity");
        if (pity != null) warnUnknown(pity, PITY_KEYS, "loot/ultimate-loot.yml:pity");
        BigDecimal threshold = parseDecimal(yaml.getConfigurationSection("pity"), "point-threshold",
                BigDecimal.valueOf(1000), "loot/ultimate-loot.yml:pity.point-threshold");
        if (threshold.signum() <= 0) threshold = BigDecimal.valueOf(1000);
        LinkedHashMap<String, UltimateLootConfig.LootRule> rules = new LinkedHashMap<>();
        ConfigurationSection sections = yaml.getConfigurationSection("loot");
        if (sections == null) {
            warning("loot/ultimate-loot.yml:loot", "缺失或不是配置段，终极战利品规则为空");
            return new UltimateLootConfig(enabled, threshold, rules);
        }
        Map<EntityType, String> owners = new LinkedHashMap<>();
        for (String id : sections.getKeys(false)) {
            ConfigurationSection section = sections.getConfigurationSection(id);
            String path = "loot/ultimate-loot.yml:loot." + id;
            if (section == null) { warning(path, "必须是配置段，已跳过规则"); continue; }
            warnUnknown(section, LOOT_RULE_KEYS, path);
            Set<EntityType> types = parseLootEntityTypes(section.getList("entity-types"), path + ".entity-types");
            if (types.isEmpty()) { warning(path + ".entity-types", "必须配置至少一个有效实体类型，已跳过规则"); continue; }
            boolean duplicate = false;
            for (EntityType type : types) {
                if (owners.containsKey(type)) { warning(path + ".entity-types", "实体类型 " + type + " 已属于规则 " + owners.get(type)); duplicate = true; }
            }
            if (duplicate) continue;
            int levelMin = positiveInt(section, "level-min", 1, path + ".level-min");
            int levelMax = section.contains("level-max") ? positiveInt(section, "level-max", levelMin, path + ".level-max") : -1;
            if (levelMax >= 0 && levelMax < levelMin) { warning(path + ".level-max", "不能小于 level-min，已跳过规则"); continue; }
            ConfigurationSection levels = section.getConfigurationSection("levels");
            if (levels == null || levels.getKeys(false).isEmpty()) { warning(path + ".levels", "必须配置至少一个等级自然掉率，已跳过规则"); continue; }
            Map<Integer, BigDecimal> chances = new LinkedHashMap<>();
            for (String rawLevel : levels.getKeys(false)) {
                Integer level = parsePositiveInteger(rawLevel);
                if (level == null) { warning(path + ".levels." + rawLevel, "等级必须是正整数，已忽略"); continue; }
                BigDecimal chance = parseDecimal(levels, rawLevel, BigDecimal.valueOf(-1), path + ".levels." + rawLevel);
                if (chance.compareTo(BigDecimal.ZERO) < 0 || chance.compareTo(BigDecimal.ONE) > 0) {
                    warning(path + ".levels." + rawLevel, "自然掉率必须在 0..1，已忽略"); continue;
                }
                chances.put(level, chance);
            }
            if (chances.isEmpty()) { warning(path + ".levels", "没有有效自然掉率，已跳过规则"); continue; }
            ConfigurationSection rewardSection = section.getConfigurationSection("reward");
            if (rewardSection == null) { warning(path + ".reward", "必须是配置段，已跳过规则"); continue; }
            UltimateLootConfig.LootReward reward = parseLootReward(rewardSection, path + ".reward");
            if (reward == null) continue;
            rules.put(id, new UltimateLootConfig.LootRule(id, types,
                    bool(section, "exclude-mama-children", true, path + ".exclude-mama-children"),
                    chances, levelMin, levelMax, reward));
            types.forEach(type -> owners.put(type, id));
        }
        return new UltimateLootConfig(enabled, threshold, rules);
    }

    private UltimateLootConfig.LootReward parseLootReward(ConfigurationSection section, String path) {
        warnUnknown(section, LOOT_REWARD_KEYS, path);
        String itemId = string(section, "item-id", "", path + ".item-id");
        if (itemId.isBlank()) { warning(path + ".item-id", "不能为空，已跳过奖励"); return null; }
        int amount = positiveInt(section, "amount", 1, path + ".amount");
        List<String> commands = stringList(section.getList("commands"), path + ".commands");
        boolean broadcast = bool(section, "broadcast", false, path + ".broadcast");
        String message = string(section, "broadcast-message", "", path + ".broadcast-message");
        return new UltimateLootConfig.LootReward(itemId, amount, commands, broadcast, message);
    }

    private BigDecimal parseDecimal(ConfigurationSection section, String key, BigDecimal fallback, String path) {
        if (section == null || !section.contains(key)) return fallback;
        Object raw = section.get(key);
        try {
            BigDecimal value = new BigDecimal(String.valueOf(raw));
            if (value.scale() > 12) value = value.setScale(12, java.math.RoundingMode.HALF_UP);
            return value;
        } catch (NumberFormatException ex) {
            warning(path, "必须是有限数字，已使用默认值");
            return fallback;
        }
    }

    private Set<EntityType> parseLootEntityTypes(List<?> raw, String path) {
        LinkedHashSet<EntityType> types = new LinkedHashSet<>();
        if (raw == null) return types;
        for (Object value : raw) {
            if (!(value instanceof String text)) { warning(path, "包含非字符串实体类型，已忽略"); continue; }
            try {
                EntityType type = EntityType.valueOf(text.trim().toUpperCase(Locale.ROOT));
                if (type.isSpawnable() && type.getEntityClass() != null) types.add(type);
                else warning(path, "实体类型 " + text + " 不可生成，已忽略");
            } catch (IllegalArgumentException ex) { warning(path, "未知实体类型 " + text + "，已忽略"); }
        }
        return Set.copyOf(types);
    }
    private YamlConfiguration loadRequired(File file, String path) {
        if (!file.isFile()) {
            error(path, "缺少必需配置文件");
            return null;
        }
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(file);
            return yaml;
        } catch (IOException | InvalidConfigurationException ex) {
            error(path, "无法读取：" + ex.getMessage());
            return null;
        }
    }

    private YamlConfiguration loadOptional(File file, String path) {
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(file);
            return yaml;
        } catch (IOException | InvalidConfigurationException ex) {
            warning(path, "无法读取，该等级池已跳过：" + ex.getMessage());
            return null;
        }
    }

    private LootConfig.DropTimes parseDropTimes(Object raw, String path) {
        if (!(raw instanceof List<?> values) || values.size() != 2
                || !isWholeNumber(values.get(0)) || !isWholeNumber(values.get(1))) {
            warning(path, "必须是 [最少次数, 最多次数] 两个整数，已使用兜底值");
            return null;
        }
        int minValue = ((Number) values.get(0)).intValue();
        int maxValue = ((Number) values.get(1)).intValue();
        if (minValue < 1 || maxValue < minValue) {
            warning(path, "次数必须满足 1 <= 最少 <= 最多，已使用兜底值");
            return null;
        }
        return new LootConfig.DropTimes(minValue, maxValue);
    }

    private Set<Integer> parseRotationSets(Object raw, boolean enforceMaximum, int maxSet, String path) {
        if (raw == null) return null;
        List<?> values = raw instanceof List<?> list ? list : List.of(raw);
        LinkedHashSet<Integer> result = new LinkedHashSet<>();
        for (Object value : values) {
            if (!isWholeNumber(value)) {
                warning(path, "必须是整数或整数列表，非法值已忽略");
                continue;
            }
            Number number = (Number) value;
            int set = number.intValue();
            if (set < 1 || (enforceMaximum && set > maxSet)) {
                String range = enforceMaximum ? "1.." + maxSet : "正整数范围";
                warning(path, "套号 " + set + " 超出 " + range + "，已忽略");
                continue;
            }
            result.add(set);
        }
        return result.isEmpty() ? null : Set.copyOf(result);
    }

    private boolean bool(ConfigurationSection section, String key, boolean fallback, String path) {
        if (!section.contains(key)) return fallback;
        Object raw = section.get(key);
        if (raw instanceof Boolean value) return value;
        warning(path, "必须是布尔值，已使用 " + fallback);
        return fallback;
    }

    private int positiveInt(ConfigurationSection section, String key, int fallback, String path) {
        if (!section.contains(key)) return fallback;
        Object raw = section.get(key);
        if (isWholeNumber(raw) && ((Number) raw).intValue() > 0) return ((Number) raw).intValue();
        warning(path, "必须是正整数，已使用 " + fallback);
        return fallback;
    }

    private String string(ConfigurationSection section, String key, String fallback, String path) {
        if (!section.contains(key)) return fallback;
        Object raw = section.get(key);
        if (raw instanceof String value) return value.trim();
        warning(path, "必须是字符串，已使用默认值");
        return fallback;
    }

    private int mapPositiveInt(Map<?, ?> map, String key, int fallback, String path) {
        if (!map.containsKey(key)) return fallback;
        Object raw = map.get(key);
        if (isWholeNumber(raw) && ((Number) raw).intValue() > 0) return ((Number) raw).intValue();
        warning(path, "必须是正整数，已使用 " + fallback);
        return fallback;
    }

    private double mapPositiveDouble(Map<?, ?> map, String key, double fallback, String path) {
        if (!map.containsKey(key)) return fallback;
        Object raw = map.get(key);
        if (raw instanceof Number number && Double.isFinite(number.doubleValue()) && number.doubleValue() > 0) {
            return number.doubleValue();
        }
        warning(path, "必须是大于 0 的有限数字，已跳过奖励");
        return -1;
    }

    private boolean mapBoolean(Map<?, ?> map, String key, boolean fallback, String path) {
        if (!map.containsKey(key)) return fallback;
        Object raw = map.get(key);
        if (raw instanceof Boolean value) return value;
        warning(path, "必须是布尔值，已使用 " + fallback);
        return fallback;
    }

    private String mapString(Map<?, ?> map, String key, String fallback) {
        Object raw = map.get(key);
        return raw instanceof String value ? value.trim() : fallback;
    }

    private List<String> stringList(Object raw, String path) {
        if (raw == null) return List.of();
        if (!(raw instanceof List<?> values)) {
            warning(path, "必须是字符串列表，已忽略");
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof String command && !command.isBlank()) result.add(command);
            else warning(path, "包含非字符串或空命令，该项已忽略");
        }
        return List.copyOf(result);
    }

    private Integer parsePositiveInteger(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean isWholeNumber(Object raw) {
        if (!(raw instanceof Number number)) return false;
        double value = number.doubleValue();
        return Double.isFinite(value) && value == Math.rint(value)
                && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE;
    }

    private void warnUnknown(ConfigurationSection section, Set<String> allowed, String path) {
        for (String key : section.getKeys(false)) {
            if (!allowed.contains(key)) warning(path + ":" + key, "未知配置键，已忽略");
        }
    }

    private void warnUnknown(Map<?, ?> map, Set<String> allowed, String path) {
        for (Object rawKey : map.keySet()) {
            String key = String.valueOf(rawKey);
            if (!allowed.contains(key)) warning(path + "." + key, "未知配置键，已忽略");
        }
    }

    private void warning(String path, String message) {
        diagnostics.add(ConfigDiagnostic.warning(path, message));
    }

    private void error(String path, String message) {
        fatal = true;
        diagnostics.add(ConfigDiagnostic.error(path, message));
    }

    private record LootSettings(boolean enabled, boolean replaceVanillaDrops,
                                boolean rotationEnabled, int rotationSets,
                                Map<Integer, LootConfig.DropTimes> dropTimes,
                                LootConfig.DropTimes fallbackDropTimes,
                                SpecialLootConfig specialLoot) {}

    public record ParseResult(LootConfig lootConfig, UltimateLootConfig ultimateLootConfig,
                              List<ConfigDiagnostic> diagnostics) {
        public ParseResult {
            diagnostics = List.copyOf(diagnostics);
        }

        public boolean successful() {
            return lootConfig != null && ultimateLootConfig != null;
        }
    }
}
