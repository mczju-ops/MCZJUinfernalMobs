package com.infernalmobs.skill.impl;

import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vex;

/**
 * 唤魔：受击时概率召唤两只恼鬼，附近最多存在四只。
 * 准备召唤恼鬼音效变调播放。
 */
public class PassiveVexSummonerSkill implements Skill {

    @Override
    public String getId() {
        return "vexsummoner";
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
        LivingEntity mob = ctx.getEntity();
        if (mob == null || !mob.isValid()) return;
        Player player = ctx.getTargetPlayer();
        if (player == null || !player.isOnline()) return;

        double chance = config.getDouble("chance", 0.25);
        if (Math.random() >= chance) return;
        if (ctx.isWeakened() && Math.random() < 0.5) return;  // 削弱: 概率减小50%

        int maxNearby = config.getInt("max-nearby", 4);
        double countRange = config.getDouble("count-range", 16);
        long vexCount = mob.getWorld().getNearbyEntities(mob.getLocation(), countRange, countRange, countRange)
                .stream()
                .filter(e -> e instanceof Vex)
                .count();
        if (vexCount >= maxNearby) return;

        int summonCount = config.getInt("summon-count", 2);
        int toSummon = Math.min(summonCount, maxNearby - (int) vexCount);
        if (toSummon <= 0) return;

        Location loc = mob.getLocation();
        String soundKey = config.getString("sound", "ENTITY_EVOKER_PREPARE_SUMMON");
        float pitch = (float) config.getDouble("sound-pitch", 0.8);
        try {
            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundKey.toUpperCase().replace(".", "_"));
            mob.getWorld().playSound(loc, sound, 0.8f, pitch);
        } catch (IllegalArgumentException ignored) {}

        for (int i = 0; i < toSummon; i++) {
            Vex vex = (Vex) mob.getWorld().spawnEntity(loc, EntityType.VEX);
            vex.setTarget(player);
        }
    }
}
