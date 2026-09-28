package com.infernalmobs.model;

import org.bukkit.entity.EntityType;

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
     * 生成时已经解析并固定的完整 morph 目标池。
     */
    private final List<EntityType> morphTargetTypes;
    /** 被 morph_controller 等道具禁用的词条 skillId 集合。 */
    private final Set<String> suppressedAffixes =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private volatile Runnable persistentStateListener = () -> {};

    public MobState(UUID entityUuid, MobProfile profile) {
        this(entityUuid, profile, List.of());
    }

    public MobState(UUID entityUuid, MobProfile profile, List<EntityType> morphTargetTypes) {
        this.entityUuid = entityUuid;
        this.profile = profile;
        this.statMap = new StatMap();
        this.morphTargetTypes = morphTargetTypes != null ? List.copyOf(morphTargetTypes) : List.of();
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
        if (key == null) return false;
        boolean added = usedOneTime.add(key.toLowerCase(java.util.Locale.ROOT));
        if (added) persistentStateListener.run();
        return added;
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

    public List<EntityType> getMorphTargetTypes() {
        return morphTargetTypes;
    }

    /** 禁用某个词条（morph_controller 等道具调用）。 */
    public void suppressAffix(String skillId) {
        if (skillId != null && suppressedAffixes.add(skillId.toLowerCase(java.util.Locale.ROOT))) {
            persistentStateListener.run();
        }
    }

    /** 解禁某个词条。 */
    public void unsuppressAffix(String skillId) {
        if (skillId != null && suppressedAffixes.remove(skillId.toLowerCase(java.util.Locale.ROOT))) {
            persistentStateListener.run();
        }
    }

    /** 判断词条是否被禁用。 */
    public boolean isAffixSuppressed(String skillId) {
        return skillId != null && suppressedAffixes.contains(skillId.toLowerCase());
    }

    /** 获取所有被禁用的词条 skillId（只读视图）。 */
    public Set<String> getSuppressedAffixes() {
        return java.util.Collections.unmodifiableSet(suppressedAffixes);
    }

    public Set<String> getUsedOneTime() {
        return java.util.Collections.unmodifiableSet(usedOneTime);
    }

    /** 仅供 PDC 恢复入口使用；设置监听器前调用，避免恢复过程产生写回。 */
    public void restorePersistentState(Set<String> suppressed, Set<String> used) {
        suppressedAffixes.clear();
        usedOneTime.clear();
        if (suppressed != null) suppressedAffixes.addAll(suppressed);
        if (used != null) usedOneTime.addAll(used);
    }

    public void setPersistentStateListener(Runnable listener) {
        persistentStateListener = listener != null ? listener : () -> {};
    }

    public void clearPersistentStateListener() {
        persistentStateListener = () -> {};
    }

    /**
     * 变身时将旧状态中需要跨形态持久化的数据复制到本实例：
     * - usedOneTime（含 1up 使用记录，防止变身刷新次数）
     * - suppressedAffixes（morph_controller 等禁用状态）
     */
    public void inheritPersistentState(MobState old) {
        if (old == null) return;
        this.usedOneTime.addAll(old.usedOneTime);
        this.suppressedAffixes.addAll(old.suppressedAffixes);
    }
}
