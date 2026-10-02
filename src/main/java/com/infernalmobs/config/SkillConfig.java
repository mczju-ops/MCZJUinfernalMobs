package com.infernalmobs.config;

import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 单个技能的不可变配置。保留轻量类型 getter，让技能实现不依赖 Bukkit 的可变 ConfigurationSection。
 */
public final class SkillConfig {

    private final String skillId;
    private final String display;
    private final Map<String, Object> values;
    private final Map<String, SoundConfig> sounds;
    private final Map<String, Material> materials;
    private final Map<String, List<EntityType>> entityTypeLists;
    private final List<PreparseWarning> preparseWarnings;

    public SkillConfig(String skillId, String display, Map<String, Object> values) {
        this.skillId = skillId;
        this.display = display;
        this.values = freezeMap(values);
        this.sounds = Collections.unmodifiableMap(parseSounds(this.values, ""));
        PreparsedValues preparsed = parseTypedValues(this.values, "");
        this.materials = Collections.unmodifiableMap(preparsed.materials());
        this.entityTypeLists = Collections.unmodifiableMap(preparsed.entityTypeLists());
        this.preparseWarnings = List.copyOf(preparsed.warnings());
    }

    public String getSkillId() {
        return skillId;
    }

    public String getDisplay() {
        return display;
    }

    public int getInt(String key, int def) {
        Object value = getValue(key);
        return value instanceof Number number ? number.intValue() : def;
    }

    public double getDouble(String key, double def) {
        Object value = getValue(key);
        return value instanceof Number number ? number.doubleValue() : def;
    }

    public String getString(String key, String def) {
        Object value = getValue(key);
        return value instanceof String string ? string : def;
    }

    /** 读取预解析的物品材质；无效配置返回默认值。 */
    public Material getMaterial(String key, Material def) {
        Material material = materials.get(key);
        return material != null ? material : def;
    }

    /** 读取预解析的实体类型列表；无效项已在配置加载阶段跳过。 */
    public List<EntityType> getEntityTypeList(String key) {
        return entityTypeLists.getOrDefault(key, List.of());
    }

    /** 读取预解析的实体类型集合；无效项已在配置加载阶段跳过。 */
    public Set<EntityType> getEntityTypeSet(String key) {
        return Set.copyOf(getEntityTypeList(key));
    }

    /** 未配置 enabled-holders 表示不限制实体类型。 */
    public boolean isHolderAllowed(EntityType entityType) {
        if (entityType == null) return false;
        Set<EntityType> holders = getEntityTypeSet("enabled-holders");
        return holders.isEmpty() || holders.contains(entityType);
    }

    /** 返回构造配置时发现的材质/实体类型警告，由配置解析器统一输出。 */
    List<PreparseWarning> getPreparseWarnings() {
        return preparseWarnings;
    }

    public List<String> getStringList(String key) {
        Object value = getValue(key);
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }

    public boolean getBoolean(String key, boolean def) {
        Object value = getValue(key);
        return value instanceof Boolean bool ? bool : def;
    }

    /** 读取嵌套声音配置；无效声音 ID 时返回 null，声音不可用不影响技能主逻辑。 */
    public SoundConfig getSound(String key) {
        return sounds.get(key);
    }

    private static Map<String, SoundConfig> parseSounds(Map<String, Object> values, String prefix) {
        LinkedHashMap<String, SoundConfig> result = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            if (value instanceof Map<?, ?> map) {
                if (key.endsWith("sound")) {
                    SoundConfig sound = parseSoundValue(map);
                    if (sound != null) result.put(path, sound);
                }
                Map<String, Object> nested = new LinkedHashMap<>();
                map.forEach((nestedKey, nestedValue) -> nested.put(String.valueOf(nestedKey), nestedValue));
                result.putAll(parseSounds(nested, path));
            }
        });
        return result;
    }

    private static SoundConfig parseSoundValue(Map<?, ?> map) {
        Object idValue = map.get("id");
        if (!(idValue instanceof String id) || id.isBlank()) return null;
        try {
            String normalized = id.trim();
            if (!normalized.contains(":")) normalized = "minecraft:" + normalized;
            Key soundKey = Key.key(normalized);
            float volume = numberAsFloat(map.get("volume"), 1.0f);
            float pitch = numberAsFloat(map.get("pitch"), 1.0f);
            return new SoundConfig(soundKey, volume, pitch);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static float numberAsFloat(Object value, float fallback) {
        if (!(value instanceof Number number)) return fallback;
        float result = number.floatValue();
        return Float.isFinite(result) && result >= 0.0f ? result : fallback;
    }

    private static PreparsedValues parseTypedValues(Map<String, Object> values, String prefix) {
        LinkedHashMap<String, Material> materials = new LinkedHashMap<>();
        LinkedHashMap<String, List<EntityType>> entityTypeLists = new LinkedHashMap<>();
        List<PreparseWarning> warnings = new ArrayList<>();
        values.forEach((key, value) -> parseTypedValue(key, value,
                prefix.isEmpty() ? key : prefix + "." + key, materials, entityTypeLists, warnings));
        return new PreparsedValues(materials, entityTypeLists, warnings);
    }

    private static void parseTypedValue(String key, Object value, String path,
                                        Map<String, Material> materials,
                                        Map<String, List<EntityType>> entityTypeLists,
                                        List<PreparseWarning> warnings) {
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> nested = new LinkedHashMap<>();
            map.forEach((nestedKey, nestedValue) -> nested.put(String.valueOf(nestedKey), nestedValue));
            nested.forEach((nestedKey, nestedValue) -> parseTypedValue(nestedKey, nestedValue,
                    path + "." + nestedKey, materials, entityTypeLists, warnings));
            return;
        }
        if ("item".equals(key)) {
            if (!(value instanceof String raw)) {
                warnings.add(new PreparseWarning(path, "必须是物品材质名"));
                return;
            }
            try {
                Material material = Material.valueOf(raw.trim().toUpperCase(Locale.ROOT));
                if (!material.isItem()) {
                    warnings.add(new PreparseWarning(path, raw + " 不是可创建物品的材质"));
                    return;
                }
                materials.put(pathWithoutSkillPrefix(path), material);
            } catch (IllegalArgumentException ex) {
                warnings.add(new PreparseWarning(path, "无法识别物品材质 " + raw));
            }
            return;
        }
        if (!isEntityTypeListKey(key)) return;
        if (!(value instanceof List<?> list)) {
            warnings.add(new PreparseWarning(path, "必须是实体类型列表"));
            return;
        }
        LinkedHashSet<EntityType> parsed = new LinkedHashSet<>();
        for (Object item : list) {
            if (!(item instanceof String raw)) {
                warnings.add(new PreparseWarning(path, "包含非字符串实体类型，已跳过"));
                continue;
            }
            try {
                parsed.add(EntityType.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                warnings.add(new PreparseWarning(path, "无法识别实体类型 " + raw + "，已跳过"));
            }
        }
        entityTypeLists.put(pathWithoutSkillPrefix(path), List.copyOf(parsed));
    }

    private static boolean isEntityTypeListKey(String key) {
        return key.endsWith("-types") || key.endsWith("-holders")
                || key.endsWith("-riders") || key.endsWith("-mounts")
                || key.endsWith("-passengers");
    }

    private static String pathWithoutSkillPrefix(String path) {
        return path.startsWith("skills.") ? path.substring("skills.".length()) : path;
    }

    public int getDurationTicks(String key, int def) {
        int value = getInt(key, def);
        return value < 0 ? PotionEffect.INFINITE_DURATION : value;
    }

    private Object getValue(String path) {
        Object current = values;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) return null;
            current = map.get(part);
        }
        return current;
    }

    private static Map<String, Object> freezeMap(Map<String, Object> source) {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (value != null) copy.put(key, freezeValue(value));
        });
        return Collections.unmodifiableMap(copy);
    }

    private static Object freezeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> nested = new LinkedHashMap<>();
            map.forEach((key, nestedValue) -> {
                if (nestedValue != null) nested.put(String.valueOf(key), freezeValue(nestedValue));
            });
            return Collections.unmodifiableMap(nested);
        }
        if (value instanceof List<?> list) {
            List<Object> nested = new ArrayList<>(list.size());
            list.forEach(item -> {
                if (item != null) nested.add(freezeValue(item));
            });
            return List.copyOf(nested);
        }
        return value;
    }

    private record PreparsedValues(Map<String, Material> materials,
                                   Map<String, List<EntityType>> entityTypeLists,
                                   List<PreparseWarning> warnings) {}

    record PreparseWarning(String path, String message) {}
}
