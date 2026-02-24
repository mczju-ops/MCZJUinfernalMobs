package com.infernalmobs.model;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 怪物数值容器，存储血量加成、攻击加成、速度加成等。
 * 线程安全，供多事件同时读写。
 */
public class StatMap {

    public static final String HP_BONUS = "hp_bonus";
    public static final String DAMAGE_BONUS = "damage_bonus";
    public static final String DAMAGE_MULTIPLIER = "damage_multiplier";
    public static final String SPEED_MULTIPLIER = "speed_multiplier";

    private final ConcurrentHashMap<String, Double> stats = new ConcurrentHashMap<>();

    public void add(String key, double value) {
        stats.merge(key, value, Double::sum);
    }

    public void set(String key, double value) {
        stats.put(key, value);
    }

    public double get(String key) {
        return stats.getOrDefault(key, 0.0);
    }

    public double getOrDefault(String key, double def) {
        return stats.getOrDefault(key, def);
    }
}
