package com.infernalmobs.skill.impl;

import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * 狂暴：攻击时扣 1 血并对玩家造成额外伤害。
 */
public class ActiveBerserkSkill implements Skill {

    @Override
    public String getId() {
        return "berserk";
    }

    @Override
    public SkillType getType() {
        return SkillType.ACTIVE;
    }

    @Override
    public void onEquip(SkillContext ctx, SkillConfig config) {}

    @Override
    public void onUnequip(SkillContext ctx) {}

    @Override
    public void onTrigger(SkillContext ctx, SkillConfig config) {
        LivingEntity mob = ctx.getEntity();
        Player victim = ctx.getTargetPlayer();
        if (victim == null || !victim.isOnline()) return;

        double selfDamage = config.getDouble("self-damage", 1);
        double extraDamage = config.getDouble("extra-damage", 5);

        mob.setHealth(Math.max(0, mob.getHealth() - selfDamage));
        victim.damage(extraDamage, mob);

        try {
            mob.getWorld().playSound(mob.getLocation(), org.bukkit.Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.5f, 0.7f);
        } catch (IllegalArgumentException ignored) {}
    }
}
