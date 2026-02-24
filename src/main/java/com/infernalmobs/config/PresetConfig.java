package com.infernalmobs.config;

import java.util.Collections;
import java.util.List;

/**
 * 预设怪物配置：固定技能组合，仅在指定区域/世界有概率刷新。
 */
public class PresetConfig {

    public static class SkillEntry {
        private final String id;

        public SkillEntry(String id) {
            this.id = id;
        }
        public String getId() { return id; }
    }

    private final String id;
    private final String display;
    private final int level;
    private final List<SkillEntry> skills;
    private final List<String> regionIds;  // 仅在些区域可刷新
    private final List<String> worlds;     // 可选：进一步限制世界，空表示不限制
    private final double weight;           // 0~1，在区域内刷怪时替换为预设的概率

    public PresetConfig(String id, String display, int level, List<SkillEntry> skills,
                        List<String> regionIds, List<String> worlds, double weight) {
        this.id = id;
        this.display = display != null ? display : id;
        this.level = level;
        this.skills = skills != null ? List.copyOf(skills) : Collections.emptyList();
        this.regionIds = regionIds != null ? List.copyOf(regionIds) : Collections.emptyList();
        this.worlds = worlds != null ? List.copyOf(worlds) : Collections.emptyList();
        this.weight = Math.max(0, Math.min(1, weight));
    }

    public String getId() { return id; }
    public String getDisplay() { return display; }
    public int getLevel() { return level; }
    public List<SkillEntry> getSkills() { return skills; }
    public List<String> getRegionIds() { return regionIds; }
    public List<String> getWorlds() { return worlds; }
    public double getWeight() { return weight; }

    /** 是否在指定区域 ID 和世界内可刷新 */
    public boolean canSpawnIn(String regionId, String worldName) {
        if (!regionIds.contains(regionId)) return false;
        if (worlds.isEmpty()) return true;
        return worlds.contains(worldName);
    }
}
