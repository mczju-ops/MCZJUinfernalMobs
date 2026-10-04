package com.infernalmobs.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Comparator;

/**
 * 插件核心配置的单一不可变快照。解析器必须先完整构造该对象，再由 ConfigLoader 一次提交。
 */
public record ConfigSnapshot(
        GlobalConfig global,
        Map<String, SkillConfig> skills,
        Map<String, Map<String, Integer>> skillPools,
        SpawnRules baseSpawnRules,
        List<RegionConfig> regions,
        DeathMessageConfig deathMessages,
        ProtectedAnimalsConfig protectedAnimals,
        boolean degraded,
        List<ConfigDiagnostic> diagnostics
) {
    public ConfigSnapshot {
        skills = Collections.unmodifiableMap(new LinkedHashMap<>(skills));
        LinkedHashMap<String, Map<String, Integer>> frozenPools = new LinkedHashMap<>();
        skillPools.forEach((id, weights) -> frozenPools.put(id,
                Collections.unmodifiableMap(new LinkedHashMap<>(weights))));
        skillPools = Collections.unmodifiableMap(frozenPools);
        regions = regions.stream()
                .sorted(Comparator.comparingInt(RegionConfig::priority).reversed()
                        .thenComparingInt(RegionConfig::declarationOrder))
                .toList();
        diagnostics = List.copyOf(diagnostics);
    }
}
