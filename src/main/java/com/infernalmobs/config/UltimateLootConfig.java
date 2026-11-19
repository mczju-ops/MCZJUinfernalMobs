package com.infernalmobs.config;

import org.bukkit.entity.EntityType;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** 终极战利品配置快照：每种证章拥有独立积分池，等级只决定自然掉率。 */
public final class UltimateLootConfig {
    private final boolean enabled;
    private final BigDecimal pityThreshold;
    private final Map<String, LootRule> rules;
    private final Map<EntityType, LootRule> rulesByType;

    public UltimateLootConfig(boolean enabled, BigDecimal pityThreshold, Map<String, LootRule> rules) {
        this.enabled = enabled;
        this.pityThreshold = pityThreshold == null || pityThreshold.signum() <= 0
                ? BigDecimal.ONE : pityThreshold;
        this.rules = Collections.unmodifiableMap(new LinkedHashMap<>(rules != null ? rules : Map.of()));
        Map<EntityType, LootRule> byType = new LinkedHashMap<>();
        this.rules.values().forEach(rule -> rule.entityTypes.forEach(type -> byType.put(type, rule)));
        this.rulesByType = Collections.unmodifiableMap(byType);
    }

    public static UltimateLootConfig disabled() { return new UltimateLootConfig(false, BigDecimal.ONE, Map.of()); }
    public boolean isEnabled() { return enabled; }
    public BigDecimal pityThreshold() { return pityThreshold; }
    public Map<String, LootRule> getRules() { return rules; }

    public LootRule findRule(EntityType type, int level, boolean mamaChild) {
        LootRule rule = rulesByType.get(type);
        return rule != null && rule.appliesToLevel(level)
                && (!mamaChild || !rule.excludeMamaChildren) ? rule : null;
    }

    public Set<String> configuredItemIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        rules.values().forEach(rule -> ids.add(rule.reward.itemId));
        return Set.copyOf(ids);
    }

    public static final class LootRule {
        public final String id;
        public final Set<EntityType> entityTypes;
        public final boolean excludeMamaChildren;
        public final Map<Integer, BigDecimal> naturalChances;
        public final int levelMin;
        public final int levelMax;
        /** 兼容旧版状态查询；新模型不按该字段累计。 */
        public final int requiredRolls = 1;
        /** 兼容现有内部状态查询；新模型不再使用一次性规则。 */
        public final boolean resetAfterReward = true;
        public final LootReward reward;

        public LootRule(String id, Set<EntityType> entityTypes, boolean excludeMamaChildren,
                         Map<Integer, BigDecimal> naturalChances, int levelMin, int levelMax,
                         LootReward reward) {
            this.id = id;
            this.entityTypes = Set.copyOf(entityTypes);
            this.excludeMamaChildren = excludeMamaChildren;
            this.naturalChances = Collections.unmodifiableMap(new LinkedHashMap<>(naturalChances));
            this.levelMin = levelMin;
            this.levelMax = levelMax;
            this.reward = reward;
        }

        public boolean appliesToLevel(int level) {
            return level >= levelMin && (levelMax < 0 || level <= levelMax)
                    && naturalChances.containsKey(level);
        }

        public BigDecimal naturalChance(int level) { return naturalChances.getOrDefault(level, BigDecimal.ZERO); }
    }

    public static final class LootReward {
        public final String itemId;
        public final int amount;
        public final java.util.List<String> commands;
        public final boolean broadcast;
        public final String broadcastMessage;

        public LootReward(String itemId, int amount, java.util.List<String> commands,
                           boolean broadcast, String broadcastMessage) {
            this.itemId = itemId;
            this.amount = Math.max(1, amount);
            this.commands = java.util.List.copyOf(commands != null ? commands : java.util.List.of());
            this.broadcast = broadcast;
            this.broadcastMessage = broadcastMessage == null ? "" : broadcastMessage;
        }
    }
}
