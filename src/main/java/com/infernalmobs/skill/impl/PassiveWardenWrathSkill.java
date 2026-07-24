package com.infernalmobs.skill.impl;

import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import com.infernalmobs.util.DisplacementImmunityHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

/**
 * 咆哮：受击时概率释放类似监守者的声波攻击。
 * 监守者发射声波音效 + 从怪物指向玩家的 SONIC_BOOM 射线。
 */
public class PassiveWardenWrathSkill implements Skill {

    private static void debugLog(SkillContext ctx, String msg) {
        if (ctx.getPlugin() instanceof com.infernalmobs.InfernalMobsPlugin p && p.getConfigLoader().isDebug()) {
            p.getLogger().info("[InfernalMobs:debug:wardenwrath] " + msg);
        }
    }

    @Override
    public String getId() {
        return "wardenwrath";
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

        debugLog(ctx, "onTrigger entity=" + ctx.getEntity().getType() + " target=" + player.getName());

        double chance = config.getDouble("chance", 0.25);
        if (Math.random() >= chance) {
            debugLog(ctx, "跳过: 概率未通过 (chance=" + chance + ")");
            return;
        }

        double distance = ctx.getEntity().getLocation().distance(player.getLocation());
        double maxRange = config.getDouble("max-range", 15);
        if (distance > maxRange) {
            debugLog(ctx, "跳过: 距离超限 distance=" + String.format("%.1f", distance) + " maxRange=" + maxRange);
            return;
        }

        double minMultiplier = config.getDouble("decay-min-multiplier", 0.2);
        double decayMultiplier = 1.0 - (distance / maxRange) * (1.0 - minMultiplier);
        debugLog(ctx, "生效 distance=" + String.format("%.1f", distance) + " decay=" + String.format("%.2f", decayMultiplier));

        boolean displacementImmune = DisplacementImmunityHelper.isImmuneAndCleanup(player, ctx.getCurrentTick());
        debugLog(ctx, "位移免疫判定 tick=" + ctx.getCurrentTick() + " immune=" + displacementImmune
                + " velBefore=" + formatVec(player.getVelocity()));
        double damage = config.getDouble("damage", 10) * decayMultiplier;
        if (damage > 0.01) {
            if (displacementImmune) {
                // 免疫位移时避免携带攻击者来源，减少原版受击方向击退
                player.damage(damage);
                debugLog(ctx, "位移免疫: 以无来源伤害结算 damage=" + String.format("%.2f", damage));
            } else {
                player.damage(damage, ctx.getEntity());
                debugLog(ctx, "普通结算: 以攻击者来源伤害 damage=" + String.format("%.2f", damage));
            }
        }

        if (displacementImmune) {
            debugLog(ctx, "跳过击退: 目标位移免疫生效");
        } else {
            Vector dir = ctx.getEntity().getLocation().toVector().subtract(player.getLocation().toVector());
            if (dir.lengthSquared() < 1.0e-6) {
                // 重叠时兜底使用怪物朝向，避免零向量导致无击退
                dir = ctx.getEntity().getLocation().getDirection().setY(0);
            }
            if (dir.lengthSquared() > 1.0e-6) {
                dir.normalize();
                double knockbackStrength = config.getDouble("knockback-horizontal", 2.5) * decayMultiplier;
                player.knockback(knockbackStrength, dir.getX(), dir.getZ());
                debugLog(ctx, "已应用原版击退 strength=" + String.format("%.2f", knockbackStrength));
            } else {
                debugLog(ctx, "跳过击退: 无有效方向向量");
            }
        }
        debugLog(ctx, "触发结束 velAfter=" + formatVec(player.getVelocity()));
        Plugin plugin = ctx.getPlugin();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                debugLog(ctx, "下一tick vel=" + formatVec(player.getVelocity()) + " loc=" + formatLoc(player.getLocation()));
            }
        });

        Location mobLoc = ctx.getEntity().getEyeLocation();
        Location playerLoc = player.getLocation().add(0, player.getHeight() * 0.5, 0);
        boolean soundAtPlayer = config.getBoolean("sound-at-player", true);
        float soundVolume = (float) config.getDouble("sound-volume", 1.8);

        String soundKey = config.getString("sound", "ENTITY_WARDEN_SONIC_BOOM");
        try {
            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundKey.toUpperCase().replace(".", "_"));
            Location soundAt = soundAtPlayer ? playerLoc : mobLoc;
            soundAt.getWorld().playSound(soundAt, sound, soundVolume, 1f);
            debugLog(ctx, "音效已播放 at=" + (soundAtPlayer ? "player" : "mob"));
        } catch (IllegalArgumentException e) {
            debugLog(ctx, "音效失败: " + soundKey + " " + e.getMessage());
        }

        boolean particleEnabled = config.getBoolean("particle-sonic-boom", true);
        debugLog(ctx, "particle-sonic-boom=" + particleEnabled);
        if (particleEnabled) {
            int density = config.getInt("particle-ray-density", 20);
            debugLog(ctx, "射线 mobLoc=" + formatLoc(mobLoc) + " playerLoc=" + formatLoc(playerLoc) + " density=" + density);
            double dx = (playerLoc.getX() - mobLoc.getX()) / density;
            double dy = (playerLoc.getY() - mobLoc.getY()) / density;
            double dz = (playerLoc.getZ() - mobLoc.getZ()) / density;
            int count = 0;
            for (int i = 0; i <= density; i++) {
                Location at = mobLoc.clone().add(dx * i, dy * i, dz * i);
                mobLoc.getWorld().spawnParticle(Particle.SONIC_BOOM, at, 1, 0, 0, 0, 0);
                count++;
            }
            debugLog(ctx, "已生成 SONIC_BOOM 粒子 count=" + count + " world=" + mobLoc.getWorld().getName());
        }
    }

    private static String formatLoc(Location loc) {
        return loc == null ? "null" : String.format("(%.1f,%.1f,%.1f)", loc.getX(), loc.getY(), loc.getZ());
    }

    private static String formatVec(Vector vec) {
        return vec == null ? "null" : String.format("(%.3f,%.3f,%.3f)", vec.getX(), vec.getY(), vec.getZ());
    }
}
