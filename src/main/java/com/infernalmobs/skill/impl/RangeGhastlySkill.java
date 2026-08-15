package com.infernalmobs.skill.impl;

import com.infernalmobs.api.event.affix.triggered.InfernalMobGhastlyEvent;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.Location;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
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
        LivingEntity mob = ctx.getEntity();
        Player target = ctx.getTargetPlayer();
        if (mob == null || !mob.isValid() || target == null || !target.isOnline()) return;
        if (ctx.isWeakened() && Math.random() < 0.5) return;  // 削弱: 概率减小50%

        Vector direction = target.getEyeLocation().toVector().subtract(mob.getEyeLocation().toVector());
        if (direction.lengthSquared() < 0.01) return;
        direction.normalize();

        Location spawnLocation = mob.getEyeLocation().add(direction);
        Vector velocity = direction.clone().multiply(config.getDouble("velocity", 1.2));
        double directDamage = config.getDouble("damage", 8);
        int fireTicks = config.getInt("fire-ticks", 60);
        float explosionPower = (float) config.getDouble("explosion-power", 1);
        int lifetimeTicks = config.getInt("entity-lifetime-ticks", 100);

        InfernalMobGhastlyEvent event = new InfernalMobGhastlyEvent(
                mob, target, ctx.getHandle(), ctx.getMobState().getProfile().getLevel(),
                spawnLocation, velocity, directDamage, fireTicks, explosionPower, lifetimeTicks);
        if (!ctx.fire(event) || event.getLifetimeTicks() == 0) return;

        Vector projectileVelocity = event.getVelocity();
        Fireball fb = event.getSpawnLocation().getWorld().spawn(event.getSpawnLocation(), Fireball.class, f -> {
            f.setShooter(mob);
            f.setDirection(projectileVelocity);
            f.setVelocity(projectileVelocity);
            f.setFireTicks(0);  // 熄灭火球本体，避免擦肩而过时点燃玩家
            f.setYield(event.getExplosionPower());  // ExplosionPower，击中时爆炸
            f.setIsIncendiary(false);  // 爆炸不生成方块火
            f.setMetadata("infernalmobs_damage", new org.bukkit.metadata.FixedMetadataValue(ctx.getPlugin(), event.getDirectDamage()));
            f.setMetadata("infernalmobs_fire_ticks", new org.bukkit.metadata.FixedMetadataValue(ctx.getPlugin(), event.getFireTicks()));
            f.setMetadata("infernalmobs_source", new org.bukkit.metadata.FixedMetadataValue(ctx.getPlugin(), mob.getUniqueId()));
            f.setMetadata("infernalmobs_ghastly_handle", new org.bukkit.metadata.FixedMetadataValue(ctx.getPlugin(), ctx.getHandle()));
            f.setMetadata("infernalmobs_ghastly_level", new org.bukkit.metadata.FixedMetadataValue(ctx.getPlugin(), event.getLevel()));
            f.setMetadata("infernalmobs_skill_id", new org.bukkit.metadata.FixedMetadataValue(ctx.getPlugin(), getId()));
        });
        scheduleProjectileLifetime(fb, ctx.getPlugin(), event.getLifetimeTicks());
    }

    private static void scheduleProjectileLifetime(org.bukkit.entity.Entity entity, org.bukkit.plugin.Plugin plugin, int maxTicks) {
        entity.getScheduler().runDelayed(plugin, task -> {
            if (entity.isValid()) entity.remove();
        }, null, maxTicks);
    }
}
