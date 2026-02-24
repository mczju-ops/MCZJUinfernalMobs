package com.infernalmobs.model;

import org.bukkit.entity.LivingEntity;

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

    public MobState(UUID entityUuid, MobProfile profile) {
        this.entityUuid = entityUuid;
        this.profile = profile;
        this.statMap = new StatMap();
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
}
