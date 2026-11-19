package com.infernalmobs.api.impl;

import com.infernalmobs.api.InfernalMobHandle;
import com.infernalmobs.api.InfernalUltimateLootStatus;
import com.infernalmobs.api.InfernalKillStats;
import com.infernalmobs.api.InfernalLootReward;
import com.infernalmobs.api.InfernalMobsApi;
import com.infernalmobs.api.InfernalPlayerKillStats;
import com.infernalmobs.config.ConfigLoader;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.factory.MobFactory;
import com.infernalmobs.model.MobState;
import com.infernalmobs.service.CombatService;
import com.infernalmobs.service.UltimateLootService;
import com.infernalmobs.service.KillStatsService;
import com.infernalmobs.service.LootService;
import com.infernalmobs.service.MobKillStatsService;
import com.infernalmobs.service.SkillService;
import com.infernalmobs.util.Keys;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * {@link InfernalMobsApi} 默认实现，由 InfernalMobsPlugin 注册到 ServicesManager。
 */
public class InfernalMobsApiImpl implements InfernalMobsApi {

    private final CombatService combatService;
    private final MobFactory mobFactory;
    private final ConfigLoader configLoader;
    private final Supplier<LootService> lootServiceSupplier;
    private final KillStatsService killStatsService;
    private final MobKillStatsService mobKillStatsService;
    private final Supplier<UltimateLootService> ultimateLootServiceSupplier;

    public InfernalMobsApiImpl(
            CombatService combatService,
            MobFactory mobFactory,
            ConfigLoader configLoader,
            Supplier<LootService> lootServiceSupplier,
            KillStatsService killStatsService,
            MobKillStatsService mobKillStatsService,
            Supplier<UltimateLootService> ultimateLootServiceSupplier
    ) {
        this.combatService = combatService;
        this.mobFactory = mobFactory;
        this.configLoader = configLoader;
        this.lootServiceSupplier = lootServiceSupplier;
        this.killStatsService = killStatsService;
        this.mobKillStatsService = mobKillStatsService;
        this.ultimateLootServiceSupplier = ultimateLootServiceSupplier;
    }

    @Override
    public boolean isInfernal(LivingEntity entity) {
        if (entity == null) return false;
        return combatService.getMobState(entity.getUniqueId()) != null;
    }

    @Override
    public boolean isThiefCourier(LivingEntity entity) {
        if (entity == null) return false;
        return entity.getPersistentDataContainer().getOrDefault(Keys.THIEF_COURIER, PersistentDataType.BOOLEAN, false);
    }

    @Override
    public Optional<InfernalMobHandle> getHandle(LivingEntity entity) {
        if (entity == null) return Optional.empty();
        MobState state = combatService.getMobState(entity.getUniqueId());
        if (state == null) return Optional.empty();
        return Optional.of(new InfernalMobHandle(
                entity,
                state.getProfile().getLevel(),
                state.getProfile().getAffixIds(),
                state.getSuppressedAffixes()
        ));
    }

    @Override
    public List<String> getAffixIds(LivingEntity entity) {
        if (entity == null) return List.of();
        MobState state = combatService.getMobState(entity.getUniqueId());
        if (state == null) return List.of();
        return state.getProfile().getAffixIds();
    }

    @Override
    public void removeEntity(LivingEntity entity) {
        if (entity == null) return;
        MobState state = combatService.getMobState(entity.getUniqueId());
        if (state != null) {
            combatService.unequipAndUnregister(entity, state, SkillService.UnequipReason.EXTERNAL_REMOVE);
        }
        entity.remove();
    }

    @Override
    public boolean isAffixSuppressed(LivingEntity entity, String skillId) {
        if (entity == null || skillId == null) return false;
        MobState state = combatService.getMobState(entity.getUniqueId());
        return state != null && state.isAffixSuppressed(skillId);
    }

    @Override
    public void setAffixSuppressed(LivingEntity entity, String skillId, boolean suppressed) {
        if (entity == null || skillId == null) return;
        MobState state = combatService.getMobState(entity.getUniqueId());
        if (state == null) return;
        if (suppressed) state.suppressAffix(skillId);
        else state.unsuppressAffix(skillId);
        // 刷新头顶名：被禁词条在悬停中显示删除线，保证外部插件（如 MagicItems）通过 API 禁用词条时视觉一致
        if (mobFactory != null) mobFactory.refreshDisplayName(entity, state);
    }

    @Override
    public String getAffixDisplayName(String affixId) {
        if (affixId == null || affixId.isBlank()) return "";
        if (configLoader == null) return affixId;
        SkillConfig skillConfig = configLoader.getSkillConfig(affixId);
        String display = configLoader.getSkillDisplay(affixId, skillConfig);
        return display == null || display.isBlank() ? affixId : display;
    }

    @Override
    public String getSkillDisplayName(String skillId) {
        return getAffixDisplayName(skillId);
    }

    @Override
    public List<ItemStack> rollLevelLootItems(int mobLevel) {
        if (mobLevel < 1 || lootServiceSupplier == null) return List.of();
        LootService lootService = lootServiceSupplier.get();
        return lootService != null ? lootService.rollLevelLootItems(mobLevel) : List.of();
    }

    @Override
    public List<InfernalLootReward> rollLevelLootRewards(int mobLevel) {
        if (mobLevel < 1 || lootServiceSupplier == null) return List.of();
        LootService lootService = lootServiceSupplier.get();
        return lootService != null ? lootService.rollLevelLootRewards(mobLevel) : List.of();
    }

    @Override
    public InfernalKillStats getKillStats(UUID playerId) {
        if (playerId == null || killStatsService == null) return InfernalKillStats.empty();
        String id = playerId.toString();
        Map<Integer, Integer> killsByLevel = killStatsService.getKillsByLevel(id);
        int totalKills = killsByLevel.values().stream().mapToInt(Integer::intValue).sum();
        return new InfernalKillStats(killsByLevel, totalKills);
    }

    @Override
    public List<InfernalPlayerKillStats> getAllPlayerKillStats() {
        if (killStatsService == null) return List.of();

        List<InfernalPlayerKillStats> result = new ArrayList<>();
        for (KillStatsService.PlayerStatsSnapshot snapshot : killStatsService.getAllPlayerStats()) {
            try {
                UUID playerId = UUID.fromString(snapshot.playerId());
                Map<Integer, Integer> killsByLevel = snapshot.killsByLevel();
                int totalKills = killsByLevel.values().stream().mapToInt(Integer::intValue).sum();
                result.add(new InfernalPlayerKillStats(
                        playerId,
                        snapshot.displayName(),
                        new InfernalKillStats(killsByLevel, totalKills)
                ));
            } catch (IllegalArgumentException ignored) {
                // 历史文件中的非法 UUID 键不属于有效玩家统计，不向外暴露。
            }
        }
        result.sort(Comparator.comparing(InfernalPlayerKillStats::playerId));
        return List.copyOf(result);
    }

    @Override
    public Map<String, Long> getMobKillStats() {
        return mobKillStatsService.getAllLevels();
    }

    @Override
    public Map<String, Long> getMobKillStats(int mobLevel) {
        return mobLevel < 1 ? Map.of() : mobKillStatsService.getKillsByLevel(mobLevel);
    }

    @Override
    public List<InfernalUltimateLootStatus> getUltimateLootStatuses(UUID playerId) {
        if (playerId == null || ultimateLootServiceSupplier == null) return List.of();
        UltimateLootService service = ultimateLootServiceSupplier.get();
        if (service == null) return List.of();

        Map<String, UltimateLootService.LootStats> statsById = service.getStatsById(playerId.toString());
        LootService lootService = lootServiceSupplier != null ? lootServiceSupplier.get() : null;
        return service.getActiveRules().stream()
                .sorted(Comparator.comparing(rule -> rule.id))
                .map(rule -> {
                    var reward = rule.reward;
                    var stats = statsById.getOrDefault(rule.id,
                            new UltimateLootService.LootStats(java.math.BigDecimal.ZERO, 0));
                    var threshold = service.getPityThreshold();
                    int guaranteedCount = floorCount(stats.totalPityPoints(), threshold);
                    int compensation = Math.max(0, guaranteedCount - stats.totalObtained());
                    String rewardDisplayName = lootService != null
                            ? lootService.getLootDisplayName(reward.itemId)
                            : reward.itemId;
                    return new InfernalUltimateLootStatus(
                            rule.id,
                            rule.entityTypes,
                            rule.naturalChances,
                            threshold,
                            stats.totalPityPoints(),
                            guaranteedCount,
                            stats.totalObtained(),
                            compensation,
                            rule.levelMin,
                            rule.levelMax >= 0 ? rule.levelMax : null,
                            reward.itemId,
                            rewardDisplayName,
                            reward.amount
                    );
                })
                .toList();
    }

    private static int floorCount(java.math.BigDecimal points, java.math.BigDecimal threshold) {
        if (points == null || threshold == null || threshold.signum() <= 0) return 0;
        BigInteger value = points.divide(threshold, 0, RoundingMode.FLOOR).toBigInteger();
        return value.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0
                ? Integer.MAX_VALUE : value.intValue();
    }

    @Override
    public LivingEntity spawnInfernalMob(EntityType type, Location location, int level, List<String> affixSkillIds) {
        return spawnInfernalMob(type, location, level, affixSkillIds, null);
    }

    @Override
    public LivingEntity spawnInfernalMob(EntityType type, Location location, int level, List<String> affixSkillIds,
                                         Vector velocity) {
        if (type == null || location == null || location.getWorld() == null) return null;
        if (!(location.getWorld().spawnEntity(location, type) instanceof LivingEntity entity)) return null;
        mobFactory.mechanizeWithAffixes(entity, location, level, affixSkillIds);
        // 词条全无效或被生成事件取消时会保持普通怪，返回 null 表示未成功生成炒鸡怪
        boolean infernal = combatService.getMobState(entity.getUniqueId()) != null;
        if (infernal && velocity != null) {
            entity.setVelocity(velocity);
        }
        return infernal ? entity : null;
    }
}
