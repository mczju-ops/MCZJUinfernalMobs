package com.infernalmobs.skill.impl;

import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.entity.Fireball;
import org.bukkit.util.Vector;

/**
 * 恶魂：玩家靠近时朝玩家释放火球。
 */
public class RangeGhastlySkill implements Skill {

    @Override
    public String getId() {
        return "ghastly";
    }

    @Override
    public SkillType getType() {
        return SkillType.RANGE;
    }

    @Override
    public void onEquip(SkillContext ctx, SkillConfig config) {}

    @Override
    public void onUnequip(SkillContext ctx) {}

    @Override
    public void onTrigger(SkillContext ctx, SkillConfig config) {
        if (ctx.getEntity() == null || !ctx.getEntity().isValid()) return;
        if (ctx.getTargetPlayer() == null || !ctx.getTargetPlayer().isOnline()) return;

        double damage = config.getDouble("damage", 8);
        double velocity = config.getDouble("velocity", 1.2);
        int fireTicks = config.getInt("fire-ticks", 60);

        Vector dir = ctx.getTargetPlayer().getEyeLocation().toVector()
                .subtract(ctx.getEntity().getEyeLocation().toVector()).normalize();
        var spawnAt = ctx.getEntity().getEyeLocation().add(dir);

        Fireball fb = ctx.getEntity().getWorld().spawn(spawnAt, Fireball.class, f -> {
            f.setDirection(dir.multiply(velocity));
            f.setYield(0);
            f.setIsIncendiary(false);
            f.setMetadata("infernalmobs_damage", new org.bukkit.metadata.FixedMetadataValue(ctx.getPlugin(), damage));
            f.setMetadata("infernalmobs_fire_ticks", new org.bukkit.metadata.FixedMetadataValue(ctx.getPlugin(), fireTicks));
        });
    }
}
