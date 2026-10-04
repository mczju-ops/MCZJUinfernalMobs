package com.infernalmobs.config;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** 完整预解析的掉落配置快照。战斗期间不会再读取 YAML。 */
public final class LootConfig {

    private final boolean enabled;
    private final boolean replaceVanillaDrops;
    private final boolean rotationEnabled;
    private final int rotationSets;
    private final Map<Integer, DropTimes> dropTimes;
    private final DropTimes fallbackDropTimes;
    private final Map<Integer, List<RewardEntry>> rewardsByLevel;
    private final SpecialLootConfig specialLoot;

    public LootConfig(boolean enabled, boolean replaceVanillaDrops,
                      boolean rotationEnabled, int rotationSets,
                      Map<Integer, DropTimes> dropTimes, DropTimes fallbackDropTimes,
                      Map<Integer, List<RewardEntry>> rewardsByLevel,
                      SpecialLootConfig specialLoot) {
        this.enabled = enabled;
        this.replaceVanillaDrops = replaceVanillaDrops;
        this.rotationEnabled = rotationEnabled;
        this.rotationSets = Math.max(1, rotationSets);
        this.dropTimes = Map.copyOf(dropTimes != null ? dropTimes : Map.of());
        this.fallbackDropTimes = fallbackDropTimes != null ? fallbackDropTimes : new DropTimes(1, 1);
        LinkedHashMap<Integer, List<RewardEntry>> rewards = new LinkedHashMap<>();
        if (rewardsByLevel != null) {
            rewardsByLevel.forEach((level, entries) -> rewards.put(level, List.copyOf(entries)));
        }
        this.rewardsByLevel = Map.copyOf(rewards);
        this.specialLoot = specialLoot != null ? specialLoot : SpecialLootConfig.DISABLED;
    }

    public static LootConfig disabled() {
        return new LootConfig(false, false, false, 1, Map.of(),
                new DropTimes(1, 1), Map.of(), SpecialLootConfig.DISABLED);
    }

    public boolean isEnable() {
        return enabled;
    }

    public boolean isReplaceVanillaDrops() {
        return replaceVanillaDrops;
    }

    public boolean isRotationEnable() {
        return rotationEnabled;
    }

    public int getRotationSets() {
        return rotationSets;
    }

    public SpecialLootConfig getSpecialLootConfig() {
        return specialLoot;
    }

    public List<RewardEntry> getRewardsForLevel(int level) {
        return rewardsByLevel.getOrDefault(level, List.of());
    }

    public int rollDropTimes(int level) {
        DropTimes range = dropTimes.getOrDefault(Math.max(1, level), fallbackDropTimes);
        if (range.min() == range.max()) return range.min();
        return ThreadLocalRandom.current().nextInt(range.min(), range.max() + 1);
    }

    public Set<String> configuredItemIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        rewardsByLevel.values().forEach(entries -> entries.forEach(entry -> ids.add(entry.id)));
        if (specialLoot.enable() && !specialLoot.itemId().isBlank()) ids.add(specialLoot.itemId());
        return Set.copyOf(ids);
    }

    public record DropTimes(int min, int max) {
        public DropTimes {
            min = Math.max(1, min);
            max = Math.max(min, max);
        }
    }

    public static final class RewardEntry {
        public final String id;
        public final int amount;
        public final double weight;
        public final List<String> commands;
        public final boolean broadcast;
        public final String broadcastMessage;
        public final Set<Integer> rotationSets;

        public RewardEntry(String id, int amount, double weight, List<String> commands,
                           boolean broadcast, String broadcastMessage, Set<Integer> rotationSets) {
            this.id = id;
            this.amount = amount;
            this.weight = weight;
            this.commands = List.copyOf(commands != null ? commands : List.of());
            this.broadcast = broadcast;
            this.broadcastMessage = broadcastMessage != null ? broadcastMessage : "";
            this.rotationSets = rotationSets == null || rotationSets.isEmpty()
                    ? null : Set.copyOf(rotationSets);
        }
    }
}
