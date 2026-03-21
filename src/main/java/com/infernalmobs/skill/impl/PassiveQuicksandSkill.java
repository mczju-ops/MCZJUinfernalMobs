package com.infernalmobs.skill.impl;

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
        if (ctx.getTargetPlayer() == null || !ctx.getTargetPlayer().isOnline()) return;

        int durationTicks = ctx.isWeakened() ? 60 : config.getInt("duration-ticks", 180);  // 削弱: 3s
        int amplifier = ctx.isWeakened() ? 1 : config.getInt("amplifier", 1);  // 削弱: 缓慢II

        ctx.getTargetPlayer().addPotionEffect(new PotionEffect(
                PotionEffectType.SLOWNESS, durationTicks, amplifier, false, true));
    }
}
