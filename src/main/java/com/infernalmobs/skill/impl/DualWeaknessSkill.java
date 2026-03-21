package com.infernalmobs.skill.impl;

import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 虚弱：受击或攻击时，对玩家施加虚弱效果。
 */
public class DualWeaknessSkill implements Skill {

    @Override
    public String getId() {
        return "weakness";
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

        int duration = ctx.isWeakened() ? 100 : config.getInt("duration-ticks", 500);  // 削弱: 5s
        int amplifier = ctx.isWeakened() ? 1 : config.getInt("amplifier", 1);  // 削弱: 虚弱II

        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, duration, amplifier, false, true));
    }
}
