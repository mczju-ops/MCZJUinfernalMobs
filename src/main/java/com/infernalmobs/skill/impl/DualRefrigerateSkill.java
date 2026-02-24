package com.infernalmobs.skill.impl;

import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.entity.Player;

/**
 * 冰冻：攻击或受击时，概率冰冻玩家。
 */
public class DualRefrigerateSkill implements Skill {

    @Override
    public String getId() {
        return "refrigerate";
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

        double chance = config.getDouble("chance", 0.3);
        if (Math.random() >= chance) return;

        int ticks = config.getInt("freeze-ticks", 140);
        target.setFreezeTicks(Math.max(target.getFreezeTicks(), ticks));
    }
}
