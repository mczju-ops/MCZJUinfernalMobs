package com.infernalmobs.skill.impl;

import com.infernalmobs.InfernalMobsPlugin;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.entity.Player;

/**
 * 缴械：概率召唤悦灵信使追击玩家，命中后夺取玩家当时的主手物品并送回主人。
 */
public class DualThiefSkill implements Skill {

    @Override
    public String getId() {
        return "thief";
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
        Player player = ctx.getTargetPlayer();
        if (player == null || !player.isOnline()) return;

        double chance = config.getDouble("chance", 0.10);
        if (Math.random() >= chance) return;
        if (ctx.isWeakened() && Math.random() < 0.5) return;  // 削弱: 概率再减小50%

        if (!(ctx.getPlugin() instanceof InfernalMobsPlugin plugin)) return;
        int cooldownTicks = config.getInt("cooldown-ticks", 80);
        boolean launched = plugin.getThiefCourierTestService().launch(
                player, ctx.getEntity(), ctx.getMobState(), ctx.getOrCreateHandle(),
                ctx.getCurrentTick(), cooldownTicks);
        if (launched) ctx.commitCooldown(getId(), cooldownTicks);
    }
}
