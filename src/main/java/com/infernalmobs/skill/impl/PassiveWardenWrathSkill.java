package com.infernalmobs.skill.impl;

import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
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

        double damage = config.getDouble("damage", 10) * decayMultiplier;
        if (damage > 0.01) player.damage(damage, ctx.getEntity());

        Vector dir = player.getLocation().toVector().subtract(ctx.getEntity().getLocation().toVector()).normalize();
        double knockbackH = config.getDouble("knockback-horizontal", 2.5) * decayMultiplier;
        double knockbackV = config.getDouble("knockback-vertical", 0.5) * decayMultiplier;
        Vector kb = dir.multiply(knockbackH).setY(knockbackV);
        player.setVelocity(player.getVelocity().add(kb));

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
}
