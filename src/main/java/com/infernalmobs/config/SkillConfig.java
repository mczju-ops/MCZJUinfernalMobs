package com.infernalmobs.config;

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

    public SkillConfig(String skillId, String display, Map<String, Object> values) {
        this.skillId = skillId;
        this.display = display;
        this.values = freezeMap(values);
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
