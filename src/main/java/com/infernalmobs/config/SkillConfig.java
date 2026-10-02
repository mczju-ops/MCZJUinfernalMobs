package com.infernalmobs.config;

import net.kyori.adventure.key.Key;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单个技能的不可变配置。保留轻量类型 getter，让技能实现不依赖 Bukkit 的可变 ConfigurationSection。
 */
public final class SkillConfig {

    private final String skillId;
    private final String display;
    private final Map<String, Object> values;
    private final Map<String, SoundConfig> sounds;

    public SkillConfig(String skillId, String display, Map<String, Object> values) {
        this.skillId = skillId;
        this.display = display;
        this.values = freezeMap(values);
        this.sounds = Collections.unmodifiableMap(parseSounds(this.values, ""));
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
}
