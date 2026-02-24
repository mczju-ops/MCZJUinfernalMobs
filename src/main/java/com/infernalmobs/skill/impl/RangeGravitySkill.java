package com.infernalmobs.skill.impl;

import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 重力：玩家靠近时施加漂浮效果。
 */
public class RangeGravitySkill implements Skill {

    @Override
    public String getId() {
        return "gravity";
    }

    @Override
    public SkillType getType() {
        return SkillType.RANGE;
    }

    @Override
    public void onEquip(SkillContext ctx, SkillConfig config) {}

    @Override
    public void onUnequip(SkillContext ctx) {}

    @Override
    public void onTrigger(SkillContext ctx, SkillConfig config) {
        Player target = ctx.getTargetPlayer();
        if (target == null || !target.isOnline()) return;
        if (target.hasPotionEffect(PotionEffectType.LEVITATION)) return;

        int duration = config.getInt("duration-ticks", 60);
        int amplifier = config.getInt("amplifier", 0);

        target.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, duration, amplifier, false, true));

        try {
            target.getWorld().playSound(target.getLocation(), org.bukkit.Sound.ENTITY_SHULKER_SHOOT, 0.5f, 1.2f);
        } catch (IllegalArgumentException ignored) {}
    }
}
