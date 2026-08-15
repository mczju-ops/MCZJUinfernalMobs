package com.infernalmobs.skill.impl;

import com.infernalmobs.api.event.affix.triggered.InfernalMobConfusingEvent;
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
        var target = ctx.getTargetPlayer();
        if (target == null || !target.isOnline()) return;

        int duration = config.getDurationTicks("duration-ticks", 80);
        int amplifier = config.getInt("amplifier", 2);
        if (ctx.isWeakened()) duration = Math.max(1, duration / 2);

        InfernalMobConfusingEvent event = new InfernalMobConfusingEvent(
                ctx.getEntity(), target, ctx.getHandle(), ctx.getMobState().getProfile().getLevel(),
                duration, amplifier);
        if (!ctx.fire(event)) return;
        target.addPotionEffect(new PotionEffect(
                PotionEffectType.NAUSEA, event.getDurationTicks(), event.getAmplifier(), false, true));
    }
}
