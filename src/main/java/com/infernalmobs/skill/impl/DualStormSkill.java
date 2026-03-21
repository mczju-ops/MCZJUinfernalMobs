package com.infernalmobs.skill.impl;

import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.entity.Player;

/**
 * 风暴：怪物攻击玩家或玩家攻击怪物时（DUAL），概率在玩家位置召唤闪电（参考原版 InfernalMobs）。
 */
public class DualStormSkill implements Skill {

    @Override
    public String getId() {
        return "storm";
    }

    @Override
    public SkillType getType() {
        return SkillType.DUAL;
    }

    @Override
    public void onEquip(SkillContext ctx, SkillConfig config) {}

    @Override
    public void onUnequip(SkillContext ctx) {}

    @Override
    public void onTrigger(SkillContext ctx, SkillConfig config) {
        Player target = ctx.getTargetPlayer();
        if (target == null || !target.isOnline()) return;
        if (ctx.getEntity().isDead()) return;

        double chance = config.getDouble("chance", 0.22);
        if (Math.random() >= chance) return;
        if (ctx.isWeakened() && Math.random() < 0.5) return;  // 削弱: 概率减小50%

        target.getWorld().strikeLightning(target.getLocation());
    }
}
