package com.infernalmobs.config;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    /** 区域内允许成为炒鸡怪的实体类型（白名单）。空表示不限制。 */
    private final Set<EntityType> infernalAllowTypes;
    /** 区域内禁止成为炒鸡怪的实体类型（黑名单）。有值优先级高于白名单。 */
    private final Set<EntityType> infernalDenyTypes;
    /** 区域专属：morph 变身目标池（目标类型）。空/未配置表示使用全局 skills.morph.morph-types。 */
    private final List<EntityType> morphTargetTypes;

    public RegionConfig(String id, String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                        int levelMin, int levelMax,
                        Map<String, Integer> skillPool,
                        int priority,
                        Set<EntityType> infernalAllowTypes,
                        Set<EntityType> infernalDenyTypes,
                        List<EntityType> morphTargetTypes) {
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
        this.infernalAllowTypes = infernalAllowTypes != null ? Set.copyOf(infernalAllowTypes) : Collections.emptySet();
        this.infernalDenyTypes = infernalDenyTypes != null ? Set.copyOf(infernalDenyTypes) : Collections.emptySet();
        this.morphTargetTypes = morphTargetTypes != null ? List.copyOf(morphTargetTypes) : List.of();
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

    /**
     * 判断该实体类型在此区域内是否允许成为炒鸡怪。
     * - 命中黑名单：禁止
     * - 未命中黑名单且白名单非空：白名单命中则允许，否则禁止
     * - 白名单为空：不限制（除了黑名单外都允许）
     */
    public boolean canInfernalize(EntityType type) {
        if (type == null) return true;
        if (!infernalDenyTypes.isEmpty() && infernalDenyTypes.contains(type)) return false;
        if (!infernalAllowTypes.isEmpty()) return infernalAllowTypes.contains(type);
        return true;
    }

    /**
     * 区域是否配置了 morph 目标池。
     * 空列表表示未配置（等价于使用全局 skills.morph 的 morph-types / 默认池）。
     */
    public List<EntityType> getMorphTargetTypes() {
        return morphTargetTypes;
    }
}
