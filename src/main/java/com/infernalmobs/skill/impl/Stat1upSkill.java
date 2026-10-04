package com.infernalmobs.skill.impl;

import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.model.MobState;
import com.infernalmobs.particle.ParticleEffect;
import com.infernalmobs.service.CombatService;
import com.infernalmobs.particle.ParticleSource;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import com.infernalmobs.util.SoundPlayback;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;

/**
 * 1up：血量小于等于阈值时回复全部生命，只触发一次。
 * 通过 CombatService 的 tick/受击检测驱动。
 */
public class Stat1upSkill implements Skill {

    @Override
    public String getId() {
        return "1up";
    }

    @Override
    public SkillType getType() {
        return SkillType.STAT;
    }

    @Override
    public void onEquip(SkillContext ctx, SkillConfig config) {}

    @Override
    public void onUnequip(SkillContext ctx) {}

    /**
     * 计算本次保命默认能够回复的生命值。
     */
    public double calculateRecoveryAmount(LivingEntity entity, MobState mobState) {
        if (entity == null || !entity.isValid()) return 0.0;
        return Math.max(0.0, getHealCeiling(entity, mobState) - entity.getHealth());
    }

    /**
     * 由 CombatService 在满足条件并广播事件后调用。
     */
    public void trigger(LivingEntity entity, SkillConfig config, MobState mobState, double recoveryAmount) {
        if (entity == null || !entity.isValid()) return;
        double safeRecoveryAmount = Double.isFinite(recoveryAmount) ? Math.max(0.0, recoveryAmount) : 0.0;
        double currentHealth = entity.getHealth();
        double healthAfterRecovery = Math.min(getHealCeiling(entity, mobState), currentHealth + safeRecoveryAmount);
        if (healthAfterRecovery > currentHealth) {
            entity.setHealth(healthAfterRecovery);
        }

            SoundPlayback.broadcast(entity.getLocation(), config.getSound("sound"));

        Location at = entity.getLocation();
        ParticleEffect.create()
                .source(ParticleSource.spiral(at, 0.4, 1.2, 2.0))
                .particle(Particle.HEART)
                .density(10)
                .offset(0.08, 0.08, 0.08)
                .play(at);
    }

    private double getHealCeiling(LivingEntity entity, MobState mobState) {
        var attr = entity.getAttribute(Attribute.MAX_HEALTH);
        double zCap = CombatService.zombieRecoveryCapWithoutLeaderBonus(entity, mobState);
        if (attr == null) return zCap;
        return Math.min(attr.getValue(), zCap);
    }
}
