package com.infernalmobs.skill.impl;

import com.infernalmobs.api.event.affix.triggered.InfernalMobLifestealEvent;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
/**
 * 吸血：受击后在持续时间内心跳回血。
 * 受击时设置 buff，由 CombatService 在 tick 中按配置治疗。
 */
public class PassiveLifestealSkill implements Skill {

    public static final String BUFF_KEY = "lifesteal_until";

    @Override
    public String getId() {
        return "lifesteal";
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
        if (ctx.isWeakened() && Math.random() < 0.5) return;
        InfernalMobLifestealEvent event = new InfernalMobLifestealEvent(
                ctx.getEntity(), ctx.getTargetPlayer(), ctx.getHandle(),
                ctx.getMobState().getProfile().getLevel());
        if (!ctx.fire(event)) return;

        int duration = config.getInt("duration-ticks", 80);
        setLifestealBuff(ctx, ctx.getCurrentTick() + duration);
    }

    public void setLifestealBuff(SkillContext ctx, long untilTick) {
        ctx.getMobState().setBuff(BUFF_KEY, untilTick);
    }
}
