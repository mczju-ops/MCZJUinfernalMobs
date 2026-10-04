package com.infernalmobs.service;

import com.infernalmobs.config.ConfigLoader;
import com.infernalmobs.config.RegionConfig;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** 按最终生成规则中的逐等级权重表抽取等级。 */
public final class MobLevelService {

    private final ConfigLoader config;

    public MobLevelService(ConfigLoader config) {
        this.config = config;
    }

    public int computeLevel(org.bukkit.Location ignored, RegionConfig region) {
        Map<Integer, Integer> weights = region != null
                ? region.rules().levelWeights()
                : config.currentSnapshot().baseSpawnRules().levelWeights();
        return weightedPickLevel(weights);
    }

    private static int weightedPickLevel(Map<Integer, Integer> weights) {
        if (weights.isEmpty()) return 1;
        long total = weights.values().stream().mapToLong(Integer::longValue).sum();
        if (total <= 0) return weights.keySet().stream().min(Integer::compareTo).orElse(1);

        long roll = ThreadLocalRandom.current().nextLong(total);
        var entries = new ArrayList<>(weights.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        for (Map.Entry<Integer, Integer> entry : entries) {
            roll -= entry.getValue();
            if (roll < 0) return entry.getKey();
        }
        return entries.getLast().getKey();
    }
}
