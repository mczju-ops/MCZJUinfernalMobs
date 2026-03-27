package com.infernalmobs.model;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 怪物的运行时状态，与实体绑定。
 * 持有 StatMap、MobProfile，以及 CD 等运行时数据。
 */
public class MobState {

    private final UUID entityUuid;
    private final MobProfile profile;
    private final StatMap statMap;
    private final java.util.Map<String, Long> skillCooldowns = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<String> usedOneTime = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private final java.util.Map<String, Long> buffs = new java.util.concurrent.ConcurrentHashMap<>();
    /**
     * 区域专属 morph 目标池（变身到哪些 EntityType）。
     * null 表示未配置，由 skills.morph 全局 morph-types 决定。
     */
    private final List<EntityType> morphTargetTypesOverride;
    /** 被 morph_controller 等道具禁用的词条 skillId 集合。 */
    private final Set<String> suppressedAffixes =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    public MobState(UUID entityUuid, MobProfile profile) {
        this(entityUuid, profile, null);
    }

    public MobState(UUID entityUuid, MobProfile profile, List<EntityType> morphTargetTypesOverride) {
        this.entityUuid = entityUuid;
        this.profile = profile;
        this.statMap = new StatMap();
        this.morphTargetTypesOverride = morphTargetTypesOverride != null ? List.copyOf(morphTargetTypesOverride) : null;
    }

    public UUID getEntityUuid() {
        return entityUuid;
    }

    public MobProfile getProfile() {
        return profile;
    }

    public StatMap getStatMap() {
        return statMap;
    }

    public boolean isOnCooldown(String skillId, long currentTick) {
        Long until = skillCooldowns.get(skillId);
        return until != null && currentTick < until;
    }

    public void setCooldown(String skillId, long untilTick) {
        skillCooldowns.put(skillId, untilTick);
    }

    public boolean useOneTimeIfNotUsed(String key) {
        return usedOneTime.add(key);
    }

    public boolean hasUsedOneTime(String key) {
        return usedOneTime.contains(key);
    }

    public void setBuff(String key, long value) {
        buffs.put(key, value);
    }

    public long getBuff(String key) {
        return buffs.getOrDefault(key, 0L);
    }

    public List<EntityType> getMorphTargetTypesOverride() {
        return morphTargetTypesOverride;
    }

    /** 禁用某个词条（morph_controller 等道具调用）。 */
    public void suppressAffix(String skillId) {
        if (skillId != null) suppressedAffixes.add(skillId.toLowerCase());
    }

    /** 解禁某个词条。 */
    public void unsuppressAffix(String skillId) {
        if (skillId != null) suppressedAffixes.remove(skillId.toLowerCase());
    }

    /** 判断词条是否被禁用。 */
    public boolean isAffixSuppressed(String skillId) {
        return skillId != null && suppressedAffixes.contains(skillId.toLowerCase());
    }

    /** 获取所有被禁用的词条 skillId（只读视图）。 */
    public Set<String> getSuppressedAffixes() {
        return java.util.Collections.unmodifiableSet(suppressedAffixes);
    }

    /**
     * 变身时将旧状态中需要跨形态持久化的数据复制到本实例：
     * - usedOneTime（含 1up 使用记录，防止变身刷新次数）
     * - suppressedAffixes（morph_controller 等禁用状态）
     */
    public void inheritPersistentState(MobState old) {
        if (old == null) return;
        old.usedOneTime.forEach(this.usedOneTime::add);
        old.suppressedAffixes.forEach(this.suppressedAffixes::add);
    }
}
