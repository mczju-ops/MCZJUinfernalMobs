package com.infernalmobs.skill.impl;

import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * 末影：受击或攻击时传送到玩家身后。
 */
public class DualEnderSkill implements Skill {

    @Override
    public String getId() {
        return "ender";
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
        if (ctx.getEntity() == null || !ctx.getEntity().isValid()) return;
        Player target = ctx.getTargetPlayer();
        if (target == null || !target.isOnline()) return;

        double chance = config.getDouble("chance", 1.0);
        if (chance < 1.0 && Math.random() >= chance) return;

        Vector behind = target.getLocation().getDirection().multiply(-1).setY(0).normalize();
        double dist = config.getDouble("distance", 2);
        Location dest = target.getLocation().add(behind.multiply(dist));
        dest.setY(target.getLocation().getY());

        for (int i = 0; i < 5; i++) {
            Location tryLoc = dest.clone().add(0, i, 0);
            if (tryLoc.getBlock().getType().isAir() && tryLoc.clone().add(0, 1, 0).getBlock().getType().isAir()) {
                ctx.getEntity().teleport(tryLoc);
                break;
            }
        }

        try {
            ctx.getEntity().getWorld().playSound(ctx.getEntity().getLocation(),
                    Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.8f + (float) Math.random() * 0.4f);
        } catch (IllegalArgumentException ignored) {}
    }
}
