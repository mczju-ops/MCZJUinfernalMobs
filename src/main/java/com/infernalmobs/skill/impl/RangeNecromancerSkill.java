package com.infernalmobs.skill.impl;

import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.entity.WitherSkull;
import org.bukkit.util.Vector;

/**
 * 死灵：玩家靠近时朝玩家释放凋灵之首。
 * 与 ghastly 共享 projectile 冷却，错开释放。
 */
public class RangeNecromancerSkill implements Skill {

    public static final String PROJECTILE_BUFF = "last_projectile_tick";

    @Override
    public String getId() {
        return "necromancer";
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
        if (ctx.getTargetPlayer() == null || !ctx.getTargetPlayer().isOnline()) return;
        if (ctx.isWeakened() && Math.random() < 0.5) return;  // 削弱: 概率减小50%

        double velocity = config.getDouble("velocity", 1.0);
        Vector dir = ctx.getTargetPlayer().getEyeLocation().toVector()
                .subtract(ctx.getEntity().getEyeLocation().toVector()).normalize();
        var spawnAt = ctx.getEntity().getEyeLocation().add(dir);

        WitherSkull skull = ctx.getEntity().getWorld().spawn(spawnAt, WitherSkull.class, s -> {
            s.setDirection(dir.multiply(velocity));
        });
    }
}
