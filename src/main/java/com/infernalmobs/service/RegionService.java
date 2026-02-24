package com.infernalmobs.service;

import com.infernalmobs.config.PresetConfig;
import com.infernalmobs.config.RegionConfig;
import org.bukkit.Location;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 区域与预设查询服务。
 */
public class RegionService {

    private final List<RegionConfig> regions;   // 按 priority 降序
    private final Map<String, PresetConfig> presets;

    public RegionService(List<RegionConfig> regions, Map<String, PresetConfig> presets) {
        this.regions = regions != null ? new ArrayList<>(regions) : new ArrayList<>();
        this.regions.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        this.presets = presets != null ? new HashMap<>(presets) : new HashMap<>();
    }

    /**
     * 获取位置所匹配的优先级最高的区域，无匹配则返回 null。
     */
    public RegionConfig getRegionAt(Location loc) {
        for (RegionConfig r : regions) {
            if (r.contains(loc)) return r;
        }
        return null;
    }

    /**
     * 在指定区域、世界下，按权重随机返回一个预设，或 null 表示不替换。
     */
    public PresetConfig rollPreset(String regionId, String worldName) {
        List<PresetConfig> candidates = presets.values().stream()
                .filter(p -> p.canSpawnIn(regionId, worldName))
                .filter(p -> p.getWeight() > 0)
                .toList();
        if (candidates.isEmpty()) return null;

        for (PresetConfig p : candidates) {
            if (ThreadLocalRandom.current().nextDouble() < p.getWeight()) {
                return p;
            }
        }
        return null;
    }
}
