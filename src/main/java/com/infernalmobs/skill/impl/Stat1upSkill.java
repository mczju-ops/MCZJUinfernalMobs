package com.infernalmobs.skill.impl;

import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.model.MobState;
import com.infernalmobs.particle.ParticleEffect;
import com.infernalmobs.service.CombatService;
import com.infernalmobs.particle.ParticleSource;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
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
     * 由 CombatService 在满足条件时调用。
     */
    public void trigger(LivingEntity entity, SkillConfig config, MobState mobState) {
        if (entity == null || !entity.isValid()) return;
        var attr = entity.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        double maxHp = attr.getValue();
        double zCap = CombatService.zombieFamilyHealCap(entity, mobState);
        if (!Double.isInfinite(zCap)) {
            maxHp = Math.min(maxHp, zCap);
        }
        maxHp = Math.min(maxHp, entity.getMaxHealth());
        entity.setHealth(maxHp);
        String soundKey = config.getString("sound", "BLOCK_BREWING_STAND_BREW");
        try {
            Sound s = Sound.valueOf(soundKey.toUpperCase().replace(".", "_"));
            entity.getWorld().playSound(entity.getLocation(), s, 1f, 1f);
        } catch (IllegalArgumentException ignored) {}

        Location at = entity.getLocation();
        ParticleEffect.create()
                .source(ParticleSource.spiral(at, 0.4, 1.2, 2.0))
                .particle(Particle.HEART)
                .density(10)
                .offset(0.08, 0.08, 0.08)
                .play(at);
    }
}
