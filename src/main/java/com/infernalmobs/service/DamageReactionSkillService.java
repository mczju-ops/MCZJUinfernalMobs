package com.infernalmobs.service;

import com.infernalmobs.api.InfernalMobHandle;
import com.infernalmobs.api.event.affix.triggered.InfernalMob1upEvent;
import com.infernalmobs.config.ConfigLoader;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.model.MobState;
import com.infernalmobs.skill.impl.Stat1upSkill;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** 负责实体受伤后的反应型技能，例如 1up 保命。 */
public final class DamageReactionSkillService {

    private final JavaPlugin plugin;
    private final ConfigLoader config;

    public DamageReactionSkillService(JavaPlugin plugin, ConfigLoader config) {
        this.plugin = plugin;
        this.config = config;
    }

    /** 检查并处理 1up；找不到符合条件的词条时保持原伤害。 */
    public void handle(EntityDamageEvent event, LivingEntity victim, MobState state) {
        for (var affix : state.getProfile().getAffixes()) {
            if (!"1up".equals(affix.getSkillId())) continue;
            if (state.hasUsedOneTime("1up")) continue;

            SkillConfig skillConfig = config.getSkillConfig("1up");
            if (skillConfig == null || !(affix.getSkill() instanceof Stat1upSkill skill)) continue;

            double threshold = skillConfig.getDouble("hp-threshold", 8);
            double healthAfter = victim.getHealth() - event.getFinalDamage();
            if (healthAfter > threshold || healthAfter <= 0) continue;

            double recoveryAmount = skill.calculateRecoveryAmount(victim, state);
            if (!state.useOneTimeIfNotUsed("1up")) continue;

            LivingEntity damager = event instanceof EntityDamageByEntityEvent damageByEntity
                    && damageByEntity.getDamager() instanceof LivingEntity living ? living : null;
            InfernalMobHandle handle = new InfernalMobHandle(victim,
                    state.getProfile().getLevel(), state.getProfile().getAffixIds(),
                    state.getSuppressedAffixes());
            InfernalMob1upEvent triggeredEvent = new InfernalMob1upEvent(
                    victim, damager, handle, state.getProfile().getLevel(), recoveryAmount);
            plugin.getServer().getPluginManager().callEvent(triggeredEvent);
            if (triggeredEvent.isCancelled()) break;

            event.setDamage(0.0);
            skill.trigger(victim, skillConfig, state, triggeredEvent.getRecoveryAmount());
            break;
        }
    }
}
