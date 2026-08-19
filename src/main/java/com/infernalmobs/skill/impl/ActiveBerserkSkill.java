package com.infernalmobs.skill.impl;

import com.infernalmobs.api.event.affix.triggered.InfernalMobBerserkEvent;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * 狂暴：攻击时自伤并增加本次攻击伤害。
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
        if (!(ctx.getTriggerEvent() instanceof EntityDamageByEntityEvent damageEvent)) return;

        double selfDamage = config.getDouble("self-damage", 1);
        double bonusDamage = config.getDouble("bonus-damage", 5);

        InfernalMobBerserkEvent event = new InfernalMobBerserkEvent(
                mob, victim, ctx.getHandle(), ctx.getMobState().getProfile().getLevel(),
                selfDamage, bonusDamage);
        if (!ctx.fire(event)) return;
        mob.setHealth(Math.max(0, mob.getHealth() - event.getSelfDamage()));
        if (event.getBonusDamage() > 0.0) {
            damageEvent.setDamage(damageEvent.getDamage() + event.getBonusDamage());
        }

        String soundKey = config.getString("sound", "ENTITY_PHANTOM_BITE");
        try {
            Sound sound = Sound.valueOf(soundKey.toUpperCase().replace(".", "_"));
            mob.getWorld().playSound(mob.getLocation(), sound, 0.5f, 0.7f);
        } catch (IllegalArgumentException ignored) {}
    }
}
