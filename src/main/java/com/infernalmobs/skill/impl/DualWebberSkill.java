package com.infernalmobs.skill.impl;

import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * 织网：受击或攻击时，概率在玩家位置放置蜘蛛网。
 */
public class DualWebberSkill implements Skill {

    @Override
    public String getId() {
        return "webber";
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

        double chance = config.getDouble("chance", 0.3);
        if (Math.random() >= chance) return;

        Location loc = target.getLocation().getBlock().getLocation();
        Block block = loc.getBlock();
        if (block.getType().isAir()) {
            block.setType(Material.COBWEB);
        } else {
            Block above = loc.clone().add(0, 1, 0).getBlock();
            if (above.getType().isAir()) {
                above.setType(Material.COBWEB);
            }
        }

        String soundKey = config.getString("sound", "BLOCK_COBWEB_PLACE");
        try {
            Sound s = Sound.valueOf(soundKey.toUpperCase().replace(".", "_"));
            target.getWorld().playSound(loc, s, 1f, 0.8f);
        } catch (IllegalArgumentException ignored) {}
    }
}
