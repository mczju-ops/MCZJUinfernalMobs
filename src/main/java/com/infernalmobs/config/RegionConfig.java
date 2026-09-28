package com.infernalmobs.config;

import org.bukkit.Location;

/** 空间区域及其在加载期解析完成的最终生成规则。 */
public record RegionConfig(
        String id,
        String world,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ,
        int priority,
        int declarationOrder,
        SpawnRules rules
) {
    public RegionConfig {
        int normalizedMinX = Math.min(minX, maxX);
        int normalizedMinY = Math.min(minY, maxY);
        int normalizedMinZ = Math.min(minZ, maxZ);
        int normalizedMaxX = Math.max(minX, maxX);
        int normalizedMaxY = Math.max(minY, maxY);
        int normalizedMaxZ = Math.max(minZ, maxZ);
        minX = normalizedMinX;
        minY = normalizedMinY;
        minZ = normalizedMinZ;
        maxX = normalizedMaxX;
        maxY = normalizedMaxY;
        maxZ = normalizedMaxZ;
    }

    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null) return false;
        if (!location.getWorld().getName().equals(world)) return false;
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    // 暂时保留 Java 调用层命名，配置格式本身不提供旧字段兼容。
    public String getId() { return id; }
    public String getWorld() { return world; }
    public int getPriority() { return priority; }
    public int getLevelMin() { return rules.levelWeights().keySet().stream().min(Integer::compareTo).orElse(1); }
    public int getLevelMax() { return rules.levelWeights().keySet().stream().max(Integer::compareTo).orElse(1); }
    public java.util.Map<Integer, Integer> getLevelChances() { return rules.levelWeights(); }
    public java.util.Map<String, Integer> getSkillPool() { return rules.skillPool(); }
    public java.util.List<org.bukkit.entity.EntityType> getMorphTargetTypes() { return rules.morphPool(); }
}
