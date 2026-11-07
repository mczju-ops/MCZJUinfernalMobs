package com.infernalmobs.skill.impl;

import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.service.RevenantManager;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;

/** 怨灵：死亡后在原地留下可融合的怨念。 */
public final class RevenantSkill implements Skill {
    @Override public String getId() { return "revenant"; }
    @Override public SkillType getType() { return SkillType.DEATH; }
    @Override public void onEquip(SkillContext ctx, SkillConfig config) {}
    @Override public void onUnequip(SkillContext ctx) {}

    @Override
    public void onTrigger(SkillContext ctx, SkillConfig config) {
        RevenantManager manager = ctx.getRevenantManager();
        if (manager != null) {
            manager.add(ctx.getEntity().getLocation(),
                    ctx.getMobState().getProfile().getLevel(), ctx.getCurrentTick(),
                    ctx.getEntity(), ctx.getTargetPlayer(), ctx.getHandle());
        }
    }
}
