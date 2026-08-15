package com.infernalmobs.skill.impl;

import com.infernalmobs.api.event.affix.triggered.InfernalMobSapperEvent;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 工兵：玩家攻击怪物时，对玩家施加饥饿效果。
 */
public class PassiveSapperSkill implements Skill {

    @Override
    public String getId() {
        return "sapper";
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

        int duration = config.getDurationTicks("duration-ticks", 500);
        int amplifier = config.getInt("amplifier", 1);
        if (ctx.isWeakened()) duration = Math.max(1, duration / 2);

        InfernalMobSapperEvent event = new InfernalMobSapperEvent(
                ctx.getEntity(), target, ctx.getHandle(), ctx.getMobState().getProfile().getLevel(),
                duration, amplifier);
        if (!ctx.fire(event)) return;
        target.addPotionEffect(new PotionEffect(
                PotionEffectType.HUNGER, event.getDurationTicks(), event.getAmplifier(), false, true));
    }
}
