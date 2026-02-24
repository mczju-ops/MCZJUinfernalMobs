package com.infernalmobs.affix;

import com.infernalmobs.skill.Skill;

/**
 * 词条 = 技能。技能只有有与无，无等级。
 */
public class Affix {

    private final String skillId;
    private final Skill skill;

    public Affix(String skillId, Skill skill) {
        this.skillId = skillId;
        this.skill = skill;
    }

    public String getSkillId() {
        return skillId;
    }

    public Skill getSkill() {
        return skill;
    }
}
