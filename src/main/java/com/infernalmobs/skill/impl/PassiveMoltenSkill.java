package com.infernalmobs.skill.impl;

import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * 熔岩：玩家攻击怪物时，玩家获得燃烧效果。
 */
public class PassiveMoltenSkill implements Skill {

    @Override
    public String getId() {
        return "molten";
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

        if (ctx.isWeakened() && Math.random() < 0.5) return;  // 削弱: 概率减小50%

        int fireTicks = config.getInt("fire-ticks", 60);
        ctx.getTargetPlayer().setFireTicks(Math.max(ctx.getTargetPlayer().getFireTicks(), fireTicks));
    }
}
