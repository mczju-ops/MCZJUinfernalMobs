package com.infernalmobs.skill.impl;

import com.infernalmobs.api.InfernalMobHandle;
import com.infernalmobs.api.event.affix.triggered.InfernalMobHardenedEvent;

import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.model.MobState;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import com.infernalmobs.util.SoundPlayback;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** 硬化：首次完成等级属性应用后，用当前生命值换取有限次数的伤害减免。 */
public final class StatHardenedSkill implements Skill {
    @Override public String getId() { return "hardened"; }
    @Override public SkillType getType() { return SkillType.STAT; }
    @Override public void onEquip(SkillContext ctx, SkillConfig config) {}
    @Override public void onUnequip(SkillContext ctx) {}

    public static void initialize(LivingEntity entity, MobState state, SkillConfig config) {
        if (config == null || state.getHardenedCharges() >= 0
                || state.isAffixSuppressed("hardened")
                || !config.isHolderAllowed(entity.getType())
                || !state.getProfile().getAffixIds().contains("hardened")) return;
        double ratio = config.getDouble("health-cost-ratio", 0.5);
        if (!Double.isFinite(ratio)) ratio = 0.5;
        ratio = Math.max(0.0, Math.min(1.0, ratio));
        double perCharge = config.getDouble("health-per-charge", 10.0);
        if (!Double.isFinite(perCharge) || perCharge <= 0.0) perCharge = 10.0;
        // 不允许转换直接杀死怪物，按实际扣除的生命计算次数。
        double cost = Math.min(entity.getHealth() * ratio, Math.max(0.0, entity.getHealth() - 0.1));
        entity.setHealth(entity.getHealth() - cost);
        state.setHardenedCharges((int) Math.ceil(cost / perCharge));
    }

    public static void absorb(JavaPlugin plugin, EntityDamageEvent event, LivingEntity entity, LivingEntity attacker,
                              MobState state, SkillConfig config) {
        if (config == null || state.getHardenedCharges() <= 0
                || state.isAffixSuppressed("hardened")
                || !config.isHolderAllowed(entity.getType())
                || !state.getProfile().getAffixIds().contains("hardened")) return;
        double threshold = config.getDouble("damage-threshold", 1.0);
        if (!Double.isFinite(threshold) || threshold < 0.0) threshold = 1.0;
        if (event.getFinalDamage() <= threshold) return;
        int remainingCharges = state.getHardenedCharges();
        InfernalMobHandle handle = new InfernalMobHandle(entity, state.getProfile().getLevel(),
                state.getProfile().getAffixIds(), state.getSuppressedAffixes());
        InfernalMobHardenedEvent triggeredEvent = new InfernalMobHardenedEvent(
                entity, attacker, handle, state.getProfile().getLevel(), event, remainingCharges, 1.0);
        plugin.getServer().getPluginManager().callEvent(triggeredEvent);
        state.setHardenedCharges(state.getHardenedCharges() - 1);
        if (triggeredEvent.isCancelled() || event.isCancelled()) return;
        event.setDamage(Math.min(event.getDamage(), triggeredEvent.getDamageLimit()));
        entity.getWorld().spawnParticle(Particle.END_ROD,
                entity.getLocation().add(0, entity.getHeight() * 0.5, 0),
                40, 0.2, 0.3, 0.2, 0.18);
        if (attacker instanceof Player player) {
            SoundPlayback.play(player, config.getSound("sound"));
        } else {
            SoundPlayback.broadcast(entity.getLocation(), config.getSound("sound"));
        }
    }
}
