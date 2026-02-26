package com.infernalmobs.skill.impl;

import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.particle.ParticleEffect;
import com.infernalmobs.particle.ParticleSource;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * 咆哮：受击时概率释放类似监守者的声波攻击。
 * 监守者发射声波音效，冲击波粒子效果。
 */
public class PassiveWardenWrathSkill implements Skill {

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

        double chance = config.getDouble("chance", 0.25);
        if (Math.random() >= chance) return;

        double damage = config.getDouble("damage", 10);
        player.damage(damage, ctx.getEntity());

        Vector dir = player.getLocation().toVector().subtract(ctx.getEntity().getLocation().toVector()).normalize();
        double knockbackH = config.getDouble("knockback-horizontal", 2.5);
        double knockbackV = config.getDouble("knockback-vertical", 0.5);
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
        } catch (IllegalArgumentException ignored) {}

        if (config.getBoolean("particle-beam", true)) {
            int beamDensity = config.getInt("particle-beam-density", 24);
            ParticleEffect.create()
                    .source(ParticleSource.line(mobLoc, playerLoc))
                    .particle(Particle.SONIC_BOOM)
                    .density(beamDensity)
                    .count(1)
                    .play(null);
        }
        if (config.getBoolean("particle-rings", true)) {
            double r1 = config.getDouble("particle-ring-radius-1", 0.6);
            double r2 = config.getDouble("particle-ring-radius-2", 1.2);
            double r3 = config.getDouble("particle-ring-radius-3", 1.8);
            int ringDensity = config.getInt("particle-ring-density", 16);
            ParticleEffect.create()
                    .source(ParticleSource.concentricRings(playerLoc, r1, r2, r3))
                    .particle(Particle.SONIC_BOOM)
                    .density(ringDensity)
                    .count(1)
                    .play(null);
            ParticleEffect.create()
                    .source(ParticleSource.concentricRings(playerLoc, r1, r2, r3))
                    .particle(Particle.DRAGON_BREATH)
                    .density(ringDensity)
                    .offset(0.02, 0.02, 0.02)
                    .extra(0.02)
                    .count(1)
                    .play(null);
        }
    }
}
