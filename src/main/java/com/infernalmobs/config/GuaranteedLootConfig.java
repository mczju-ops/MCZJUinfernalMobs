package com.infernalmobs.config;

import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 完整预解析的保底配置快照。每条规则只维护一份进度。 */
public final class GuaranteedLootConfig {

    private final boolean enabled;
    private final boolean rotationEnabled;
    private final int rotationSets;
    private final Map<String, GuaranteedRule> rules;

    public GuaranteedLootConfig(boolean enabled, boolean rotationEnabled, int rotationSets,
                                Map<String, GuaranteedRule> rules) {
        this.enabled = enabled;
        this.rotationEnabled = rotationEnabled;
        this.rotationSets = Math.max(1, rotationSets);
        this.rules = Collections.unmodifiableMap(
                new LinkedHashMap<>(rules != null ? rules : Map.of()));
    }

    public static GuaranteedLootConfig disabled() {
        return new GuaranteedLootConfig(false, false, 1, Map.of());
    }

    public boolean isEnable() {
        return enabled;
    }

    public Map<String, GuaranteedRule> getRules() {
        return rules;
    }

    public boolean appliesToLevel(GuaranteedRule rule, int level) {
        return rule != null && level >= rule.levelMin
                && (rule.levelMax < 0 || level <= rule.levelMax);
    }

    public GuaranteedReward activeReward(GuaranteedRule rule) {
        if (rule == null || rule.rewards.isEmpty()) return null;
        if (!rotationEnabled) return rule.rewards.getFirst();
        int activeSet = (Calendar.getInstance().get(Calendar.MONTH) % rotationSets) + 1;
        for (GuaranteedReward reward : rule.rewards) {
            if (reward.rotationSets == null || reward.rotationSets.contains(activeSet)) return reward;
        }
        return null;
    }

    public Set<String> configuredItemIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        rules.values().forEach(rule -> rule.rewards.forEach(reward -> ids.add(reward.itemId)));
        return Set.copyOf(ids);
    }

    public static final class GuaranteedRule {
        public final String id;
        public final int levelMin;
        public final int levelMax;
        public final int requiredRolls;
        public final boolean resetAfterReward;
        public final List<GuaranteedReward> rewards;

        public GuaranteedRule(String id, int levelMin, int levelMax, int requiredRolls,
                              boolean resetAfterReward, List<GuaranteedReward> rewards) {
            this.id = id;
            this.levelMin = levelMin;
            this.levelMax = levelMax;
            this.requiredRolls = requiredRolls;
            this.resetAfterReward = resetAfterReward;
            this.rewards = List.copyOf(rewards);
        }
    }

    public static final class GuaranteedReward {
        public final String itemId;
        public final int amount;
        public final Set<Integer> rotationSets;

        public GuaranteedReward(String itemId, int amount, Set<Integer> rotationSets) {
            this.itemId = itemId;
            this.amount = amount;
            this.rotationSets = rotationSets == null || rotationSets.isEmpty()
                    ? null : Set.copyOf(rotationSets);
        }
    }

    public record ActiveRule(GuaranteedRule rule, GuaranteedReward reward) {}
}
