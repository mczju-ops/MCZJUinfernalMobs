package com.infernalmobs.api.impl;

import com.infernalmobs.api.InfernalMobHandle;
import com.infernalmobs.api.InfernalGuaranteedLootStatus;
import com.infernalmobs.api.InfernalKillStats;
import com.infernalmobs.api.InfernalLootReward;
import com.infernalmobs.api.InfernalMobsApi;
import com.infernalmobs.api.InfernalPlayerKillStats;
import com.infernalmobs.config.ConfigLoader;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.factory.MobFactory;
import com.infernalmobs.model.MobState;
import com.infernalmobs.service.CombatService;
import com.infernalmobs.service.GuaranteedLootService;
import com.infernalmobs.service.KillStatsService;
import com.infernalmobs.service.LootService;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * {@link InfernalMobsApi} 默认实现，由 InfernalMobsPlugin 注册到 ServicesManager。
 */
public class InfernalMobsApiImpl implements InfernalMobsApi {

    private final CombatService combatService;
    private final MobFactory mobFactory;
    private final ConfigLoader configLoader;
    private final Supplier<LootService> lootServiceSupplier;
    private final KillStatsService killStatsService;
    private final Supplier<GuaranteedLootService> guaranteedLootServiceSupplier;

    public InfernalMobsApiImpl(
            CombatService combatService,
            MobFactory mobFactory,
            ConfigLoader configLoader,
            Supplier<LootService> lootServiceSupplier,
            KillStatsService killStatsService,
            Supplier<GuaranteedLootService> guaranteedLootServiceSupplier
    ) {
        this.combatService = combatService;
        this.mobFactory = mobFactory;
        this.configLoader = configLoader;
        this.lootServiceSupplier = lootServiceSupplier;
        this.killStatsService = killStatsService;
        this.guaranteedLootServiceSupplier = guaranteedLootServiceSupplier;
    }

    @Override
    public boolean isInfernal(LivingEntity entity) {
        if (entity == null) return false;
        return combatService.getMobState(entity.getUniqueId()) != null;
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
    public List<InfernalGuaranteedLootStatus> getGuaranteedLootStatuses(UUID playerId) {
        if (playerId == null || guaranteedLootServiceSupplier == null) return List.of();
        GuaranteedLootService service = guaranteedLootServiceSupplier.get();
        if (service == null) return List.of();

        Map<String, Integer> progressById = service.getProgressById(playerId.toString());
        LootService lootService = lootServiceSupplier != null ? lootServiceSupplier.get() : null;
        return service.getActiveRules().stream()
                .sorted(Comparator.comparing(rule -> rule.id))
                .map(rule -> {
                    int storedProgress = progressById.getOrDefault(rule.progressId, 0);
                    boolean completed = storedProgress < 0;
                    String rewardDisplayName = lootService != null
                            ? lootService.getLootDisplayName(rule.itemId)
                            : rule.itemId;
                    return new InfernalGuaranteedLootStatus(
                            rule.id,
                            rule.progressId,
                            completed ? rule.count : storedProgress,
                            rule.count,
                            completed,
                            rule.resetOnDrop,
                            rule.levelMin,
                            rule.levelMax >= 0 ? rule.levelMax : null,
                            rule.itemId,
                            rewardDisplayName,
                            rule.itemAmount
                    );
                })
                .toList();
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
