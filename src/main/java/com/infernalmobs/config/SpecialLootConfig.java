package com.infernalmobs.config;

import java.util.Collections;
import java.util.Map;

/**
 * 难打怪物额外特殊战利品配置。概率 = rates.实体类型 × 怪物等级。
 */
public record SpecialLootConfig(boolean enable, String itemId, Map<String, Double> rates) {

    public static final SpecialLootConfig DISABLED = new SpecialLootConfig(false, "nether_star", Map.of());

    public Map<String, Double> rates() {
        return rates != null ? Collections.unmodifiableMap(rates) : Map.of();
    }

    public double getRate(String entityTypeName) {
        return rates != null ? rates.getOrDefault(entityTypeName, 0.0) : 0.0;
    }
}
