package com.infernalmobs.skill.impl;

import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 迷乱：玩家攻击怪物时，对玩家施加反胃效果。
 */
public class PassiveConfusingSkill implements Skill {

    @Override
    public String getId() {
        return "confusing";
    }

    @Override
    public SkillType getType() {
        return SkillType.PASSIVE;
    }

    @Override
    public void onEquip(SkillContext ctx, SkillConfig config) {}

    @Override
    public void onUnequip(SkillContext ctx) {}

    @Override
    public void onTrigger(SkillContext ctx, SkillConfig config) {
        if (!(ctx.getTriggerEvent() instanceof EntityDamageByEntityEvent)) return;
        if (ctx.getTargetPlayer() == null || !ctx.getTargetPlayer().isOnline()) return;

        int duration = ctx.isWeakened() ? 20 : config.getInt("duration-ticks", 80);  // 削弱: 1s
        int amplifier = ctx.isWeakened() ? 2 : config.getInt("amplifier", 2);  // 削弱: 反胃III

        ctx.getTargetPlayer().addPotionEffect(new PotionEffect(
                PotionEffectType.NAUSEA, duration, amplifier, false, true));
    }
}
