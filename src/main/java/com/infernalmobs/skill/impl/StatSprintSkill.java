package com.infernalmobs.skill.impl;

import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 疾速：装配时施加常驻速度药水（无限时长），不依赖附近是否有玩家。
 */
public class StatSprintSkill implements Skill {

    @Override
    public String getId() {
        return "sprint";
    }

    @Override
    public SkillType getType() {
        return SkillType.STAT;
    }

    @Override
    public void onEquip(SkillContext ctx, SkillConfig config) {
        LivingEntity entity = ctx.getEntity();
        if (entity == null || !entity.isValid()) return;
        int amplifier = config != null ? config.getInt("amplifier", 1) : 1;
        entity.addPotionEffect(new PotionEffect(
                PotionEffectType.SPEED,
                PotionEffect.INFINITE_DURATION,
                amplifier,
                false,
                true));
    }

    @Override
    public void onUnequip(SkillContext ctx) {
        LivingEntity entity = ctx.getEntity();
        if (entity == null || !entity.isValid()) return;
        entity.removePotionEffect(PotionEffectType.SPEED);
    }
}
