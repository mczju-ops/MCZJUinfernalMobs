package com.infernalmobs.skill.impl;

import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * 移形：受击时概率与玩家互换位置。
 * 潜影贝传送音效。
 */
public class PassiveSwapSkill implements Skill {

    @Override
    public String getId() {
        return "swap";
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
        if (ctx.getEntity() == null || !ctx.getEntity().isValid()) return;
        Player player = ctx.getTargetPlayer();
        if (player == null || !player.isOnline()) return;

        double chance = config.getDouble("chance", 0.25);
        if (Math.random() >= chance) return;
        if (ctx.isWeakened() && Math.random() < 0.5) return;  // 削弱: 概率减小50%

        Location mobLoc = ctx.getEntity().getLocation().clone();
        Location playerLoc = player.getLocation().clone();

        ctx.getEntity().teleport(playerLoc);
        player.teleport(mobLoc);

        String soundKey = config.getString("sound", "ENTITY_SHULKER_TELEPORT");
        try {
            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundKey.toUpperCase().replace(".", "_"));
            mobLoc.getWorld().playSound(mobLoc, sound, 0.8f, 1f);
            playerLoc.getWorld().playSound(playerLoc, sound, 0.8f, 1f);
        } catch (IllegalArgumentException ignored) {}
    }
}
