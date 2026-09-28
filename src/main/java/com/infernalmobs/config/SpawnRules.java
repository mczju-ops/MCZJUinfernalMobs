package com.infernalmobs.config;

import org.bukkit.entity.EntityType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

/** 已完整解析的生成规则，不包含任何运行期 YAML 节点。 */
public record SpawnRules(
        Map<Integer, Integer> levelWeights,
        Map<String, Integer> skillPool,
        AffixCountRule affixCount,
        List<EntityType> morphPool
) {
    public SpawnRules {
        levelWeights = Collections.unmodifiableMap(new LinkedHashMap<>(levelWeights));
        skillPool = Collections.unmodifiableMap(new LinkedHashMap<>(skillPool));
        morphPool = List.copyOf(morphPool);
    }
}
