package com.infernalmobs.skill.impl;

import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 壁垒：怪物诞生时获得抗性提升III。
 */
public class StatBullwarkSkill implements Skill {

    @Override
    public String getId() {
        return "bullwark";
    }

    @Override
    public SkillType getType() {
        return SkillType.STAT;
    }

    @Override
    public void onEquip(SkillContext ctx, SkillConfig config) {
        int duration = config.getDurationTicks("duration-ticks", -1);
        int amplifier = config.getInt("amplifier", 2);  // III
        ctx.getEntity().addPotionEffect(new PotionEffect(
                PotionEffectType.RESISTANCE, duration, amplifier, false, true));
    }

    @Override
    public void onUnequip(SkillContext ctx) {}
}
