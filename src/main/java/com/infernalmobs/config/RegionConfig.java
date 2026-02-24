package com.infernalmobs.config;

import org.bukkit.Location;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 区域配置：由世界 + 两角点围成的立方体，定义该区域的等级范围与技能池。
 */
public class RegionConfig {

    private final String id;
    private final String world;
    private final int minX, minY, minZ;
    private final int maxX, maxY, maxZ;
    private final int levelMin;
    private final int levelMax;
    private final Map<String, Integer> skillPool; // 该区域可 roll 的技能及权重，空则用全局
    private final int priority; // 区域优先级，高者先匹配

    public RegionConfig(String id, String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                        int levelMin, int levelMax, Map<String, Integer> skillPool, int priority) {
        this.id = id;
        this.world = world;
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
        this.levelMin = levelMin;
        this.levelMax = levelMax;
        this.skillPool = skillPool != null ? new HashMap<>(skillPool) : Collections.emptyMap();
        this.priority = priority;
    }

    public boolean contains(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(world)) return false;
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public String getId() { return id; }
    public String getWorld() { return world; }
    public int getLevelMin() { return levelMin; }
    public int getLevelMax() { return levelMax; }
    public Map<String, Integer> getSkillPool() { return skillPool; }
    public int getPriority() { return priority; }
}
