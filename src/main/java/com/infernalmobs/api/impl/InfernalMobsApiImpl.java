package com.infernalmobs.api.impl;

import com.infernalmobs.api.InfernalMobHandle;
import com.infernalmobs.api.InfernalMobsApi;
import com.infernalmobs.model.MobState;
import com.infernalmobs.service.CombatService;
import org.bukkit.entity.LivingEntity;

import java.util.Optional;

/**
 * {@link InfernalMobsApi} 默认实现，由 InfernalMobsPlugin 注册到 ServicesManager。
 */
public class InfernalMobsApiImpl implements InfernalMobsApi {

    private final CombatService combatService;

    public InfernalMobsApiImpl(CombatService combatService) {
        this.combatService = combatService;
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
        return Optional.of(new InfernalMobHandle(entity, state));
    }
}
