package com.infernalmobs.skill.impl;

import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * 弓手：攻击或受击时，概率射出多支箭飞向玩家（参考 InfernalMobs）。
 */
public class DualArcherSkill implements Skill {

    @Override
    public String getId() {
        return "archer";
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

        double chance = config.getDouble("chance", 0.5);
        if (Math.random() >= chance) return;

        int count = config.getInt("arrow-count", 3);
        count = Math.max(1, Math.min(count, 8));
        float speed = (float) config.getDouble("speed", 1.0);
        float arrowSpread = (float) config.getDouble("spread-config", 6.0);

        LivingEntity mob = ctx.getEntity();
        Location loc1 = target.getLocation();
        Location loc2 = mob.getLocation().clone();

        if (!isSmall(mob)) loc2.add(0, 1, 0);
        loc2.setX(loc2.getBlockX() + 0.5);
        loc2.setY(loc2.getBlockY() + 0.5);
        loc2.setZ(loc2.getBlockZ() + 0.5);

        Vector toTarget = new Vector(loc1.getX() - loc2.getX(), loc1.getY() + 1 - loc2.getY(), loc1.getZ() - loc2.getZ());
        if (toTarget.lengthSquared() < 0.01) return;
        toTarget.normalize();

        double dirSpread = config.getDouble("spread", 0.1);

        for (int i = 0; i < count; i++) {
            Vector dir = toTarget.clone();
            dir.add(new Vector((Math.random() - 0.5) * dirSpread, (Math.random() - 0.5) * dirSpread, (Math.random() - 0.5) * dirSpread));
            if (dir.lengthSquared() > 0.01) dir.normalize();
            else dir = toTarget.clone();

            Arrow arr = mob.getWorld().spawnArrow(loc2, dir, speed, arrowSpread);
            arr.setShooter(mob);
        }

        String soundKey = config.getString("sound", "ENTITY_ARROW_SHOOT");
        try {
            Sound s = Sound.valueOf(soundKey.toUpperCase().replace(".", "_"));
            mob.getWorld().playSound(loc2, s, 1f, 1f);
        } catch (IllegalArgumentException ignored) {}
    }

    private boolean isSmall(Entity e) {
        return switch (e.getType().name()) {
            case "BAT", "CHICKEN", "COD", "PARROT", "PUFFERFISH", "RABBIT", "SALMON", "SILVERFISH",
                 "TROPICAL_FISH", "BEE", "ENDERMITE", "VEX" -> true;
            default -> false;
        };
    }
}
