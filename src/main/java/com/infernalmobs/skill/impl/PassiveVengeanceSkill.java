package com.infernalmobs.skill.impl;

import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.entity.Player;

/**
 * 复仇：受击时 50% 概率反伤玩家。
 */
public class PassiveVengeanceSkill implements Skill {

    @Override
    public String getId() {
        return "vengeance";
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
        Player player = ctx.getTargetPlayer();
        if (player == null || !player.isOnline()) return;

        double chance = config.getDouble("chance", 0.5);
        if (Math.random() >= chance) return;
        if (ctx.isWeakened() && Math.random() < 0.5) return;  // 削弱: 概率减小50%

        double damage = config.getDouble("damage", 10);
        player.damage(damage, ctx.getEntity());

        String soundKey = config.getString("sound", "ENTITY_BREEZE_DEFLECT");
        try {
            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundKey.toUpperCase().replace(".", "_"));
            player.getWorld().playSound(player.getLocation(), sound, 0.6f, 1f);
        } catch (IllegalArgumentException ignored) {}
    }
}
