package com.infernalmobs.api.impl;

import com.infernalmobs.api.InfernalMobHandle;
import com.infernalmobs.api.InfernalMobsApi;
import com.infernalmobs.config.ConfigLoader;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.factory.MobFactory;
import com.infernalmobs.model.MobState;
import com.infernalmobs.service.CombatService;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Optional;

/**
 * {@link InfernalMobsApi} 默认实现，由 InfernalMobsPlugin 注册到 ServicesManager。
 */
public class InfernalMobsApiImpl implements InfernalMobsApi {

    private final CombatService combatService;
    private final MobFactory mobFactory;
    private final ConfigLoader configLoader;

    public InfernalMobsApiImpl(CombatService combatService, MobFactory mobFactory, ConfigLoader configLoader) {
        this.combatService = combatService;
        this.mobFactory = mobFactory;
        this.configLoader = configLoader;
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
        return Optional.of(new InfernalMobHandle(entity,
                state.getProfile().getLevel(), state.getProfile().getAffixIds()));
    }

    @Override
    public List<String> getAffixIds(LivingEntity entity) {
        if (entity == null) return List.of();
        MobState state = combatService.getMobState(entity.getUniqueId());
        if (state == null) return List.of();
        return state.getProfile().getAffixIds();
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
