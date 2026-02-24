package com.infernalmobs.skill.impl;

import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;

/**
 * 冲刺：追击玩家时移速加快。
 * 由 CombatService 在 tick 中检测附近玩家并施加速度效果。
 */
public class StatSprintSkill implements Skill {

    /** Buff 键：速度效果到期 tick，用于只在快消失时重新施加 */
    public static final String BUFF_KEY = "sprint_until";

    @Override
    public String getId() {
        return "sprint";
    }

    @Override
    public SkillType getType() {
        return SkillType.STAT;
    }

    @Override
    public void onEquip(SkillContext ctx, SkillConfig config) {}

    @Override
    public void onUnequip(SkillContext ctx) {}
}
