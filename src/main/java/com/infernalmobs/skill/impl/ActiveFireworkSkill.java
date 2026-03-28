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
 * 烟花：攻击或受击时，在玩家位置发射红色球状烟花爆炸。
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

        // 需求：烟花的小爆炸发生在“玩家位置”。
        Location targetLoc = target.getLocation().clone();

        Firework fw = targetLoc.getWorld().spawn(targetLoc, Firework.class);
        fw.setMetadata("infernalmobs_firework_source", new org.bukkit.metadata.FixedMetadataValue(ctx.getPlugin(), ctx.getEntity().getUniqueId()));
        fw.setMetadata("infernalmobs_skill_id", new org.bukkit.metadata.FixedMetadataValue(ctx.getPlugin(), getId()));
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

        // 保持静止，避免下一 tick 漂移导致爆点偏移。
        fw.setVelocity(new Vector(0, 0, 0));
        ctx.getPlugin().getServer().getScheduler().runTaskLater(ctx.getPlugin(), () -> {
            if (fw.isValid()) fw.detonate();
        }, 1L);
    }
}
