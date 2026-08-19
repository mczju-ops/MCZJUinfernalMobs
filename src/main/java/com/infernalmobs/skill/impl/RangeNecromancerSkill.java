package com.infernalmobs.skill.impl;

import com.infernalmobs.api.event.affix.triggered.InfernalMobNecromancerEvent;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
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
        LivingEntity mob = ctx.getEntity();
        Player target = ctx.getTargetPlayer();
        if (mob == null || !mob.isValid() || target == null || !target.isOnline()) return;
        if (ctx.isWeakened() && Math.random() < 0.5) return;  // 削弱: 概率减小50%

        Vector direction = target.getEyeLocation().toVector().subtract(mob.getEyeLocation().toVector());
        if (direction.lengthSquared() < 0.01) return;
        direction.normalize();

        Location spawnLocation = mob.getEyeLocation().add(direction);
        Vector velocity = direction.clone().multiply(config.getDouble("velocity", 1.0));
        float explosionPower = (float) config.getDouble("explosion-power", 1.0);
        boolean charged = config.getBoolean("charged", false);
        int lifetimeTicks = config.getInt("entity-lifetime-ticks", 100);

        InfernalMobNecromancerEvent event = new InfernalMobNecromancerEvent(
                mob, target, ctx.getHandle(), ctx.getMobState().getProfile().getLevel(),
                spawnLocation, velocity, explosionPower, charged, lifetimeTicks);
        if (!ctx.fire(event) || event.getLifetimeTicks() == 0) return;

        Vector projectileVelocity = event.getVelocity();
        WitherSkull skull = event.getSpawnLocation().getWorld().spawn(event.getSpawnLocation(), WitherSkull.class, s -> {
            s.setShooter(mob);
            s.setDirection(projectileVelocity);
            s.setVelocity(projectileVelocity);
            s.setYield(event.getExplosionPower());
            s.setCharged(event.isCharged());
            s.setIsIncendiary(false);
            s.setMetadata("infernalmobs_source", new org.bukkit.metadata.FixedMetadataValue(ctx.getPlugin(), mob.getUniqueId()));
            s.setMetadata("infernalmobs_necromancer_handle", new org.bukkit.metadata.FixedMetadataValue(ctx.getPlugin(), ctx.getHandle()));
            s.setMetadata("infernalmobs_necromancer_level", new org.bukkit.metadata.FixedMetadataValue(ctx.getPlugin(), event.getLevel()));
            s.setMetadata("infernalmobs_skill_id", new org.bukkit.metadata.FixedMetadataValue(ctx.getPlugin(), getId()));
        });
        scheduleProjectileLifetime(skull, ctx.getPlugin(), event.getLifetimeTicks());
    }

    private static void scheduleProjectileLifetime(org.bukkit.entity.Entity entity,
                                                   org.bukkit.plugin.Plugin plugin, int maxTicks) {
        entity.getScheduler().runDelayed(plugin, task -> {
            if (entity.isValid()) entity.remove();
        }, null, maxTicks);
    }
}
