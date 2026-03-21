package com.infernalmobs.skill.impl;

import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.util.Vector;

/**
 * 烟花：攻击或受击时，在怪物位置发射红色球状烟花爆炸。
 */
public class ActiveFireworkSkill implements Skill {

    @Override
    public String getId() {
        return "firework";
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
        double chance = config.getDouble("chance", 1.0);
        if (chance < 1.0 && Math.random() >= chance) return;
        if (ctx.isWeakened() && Math.random() < 0.5) return;  // 削弱: 概率减小50%

        Location mobLoc = ctx.getEntity().getLocation().clone();

        Firework fw = ctx.getEntity().getWorld().spawn(mobLoc, Firework.class);
        fw.setMetadata("infernalmobs_firework_source", new org.bukkit.metadata.FixedMetadataValue(ctx.getPlugin(), ctx.getEntity().getUniqueId()));
        FireworkMeta meta = fw.getFireworkMeta();
        meta.setPower(config.getInt("power", 1));

        meta.addEffect(FireworkEffect.builder()
                .withColor(Color.RED)
                .withFade(Color.RED)
                .with(FireworkEffect.Type.BALL)
                .trail(config.getSection().getBoolean("trail", false))
                .flicker(config.getSection().getBoolean("flicker", false))
                .build());
        fw.setFireworkMeta(meta);

        int speed = config.getInt("launch-speed", 1);
        Vector dir = mobLoc.getDirection();
        if (dir.lengthSquared() < 0.01) {
            dir = target.getEyeLocation().toVector().subtract(mobLoc.toVector()).normalize();
        }
        fw.setVelocity(dir.multiply(speed));

        ctx.getPlugin().getServer().getScheduler().runTaskLater(ctx.getPlugin(), () -> {
            if (fw.isValid()) fw.detonate();
        }, 2L);
    }
}
