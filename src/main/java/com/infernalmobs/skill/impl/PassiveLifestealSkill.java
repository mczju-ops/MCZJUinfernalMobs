package com.infernalmobs.skill.impl;

import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.attribute.Attribute;

/**
 * 吸血：受击后在持续时间内心跳回血。
 * 由 CombatService 在受击时设置 buff，tick 时治疗。
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
        // 由 CombatService 在 tick 中根据 buff 治疗，此处仅设置 buff
        // Buff 的 set 在 CombatService.onPlayerAttackMob 中调用
    }

    public void setLifestealBuff(SkillContext ctx, long untilTick) {
        ctx.getMobState().setBuff(BUFF_KEY, untilTick);
    }
}
