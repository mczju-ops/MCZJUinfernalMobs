package com.infernalmobs.skill.impl;

import com.infernalmobs.api.event.affix.triggered.InfernalMobQuicksandEvent;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 流沙：玩家攻击怪物时，对玩家施加缓慢效果。
 */
public class PassiveQuicksandSkill implements Skill {

    @Override
    public String getId() {
        return "quicksand";
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

        int durationTicks = config.getDurationTicks("duration-ticks", 180);
        int amplifier = config.getInt("amplifier", 1);
        if (ctx.isWeakened()) durationTicks = Math.max(1, durationTicks / 2);

        InfernalMobQuicksandEvent event = new InfernalMobQuicksandEvent(
                ctx.getEntity(), target, ctx.getHandle(), ctx.getMobState().getProfile().getLevel(),
                durationTicks, amplifier);
        if (!ctx.fire(event)) return;
        target.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOWNESS, event.getDurationTicks(), event.getAmplifier(), false, true));
    }
}
