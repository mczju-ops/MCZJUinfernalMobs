package com.infernalmobs.service;

import com.infernalmobs.affix.Affix;
import com.infernalmobs.config.ConfigLoader;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.factory.MobFactory;
import com.infernalmobs.model.MobState;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** 负责攻击事件中的 PASSIVE、ACTIVE 和 DUAL 技能触发。 */
public final class AttackSkillService {

    private final JavaPlugin plugin;
    private final ConfigLoader config;
    private final SkillAttemptService skillAttemptService;

    public AttackSkillService(JavaPlugin plugin, ConfigLoader config,
                              SkillAttemptService skillAttemptService) {
        this.plugin = plugin;
        this.config = config;
        this.skillAttemptService = skillAttemptService;
    }

    /** 怪物攻击玩家时触发 ACTIVE 与 DUAL 技能。 */
    public void triggerMobAttackSkills(EntityDamageByEntityEvent event, LivingEntity damager,
                                       Player victim, MobState state, long currentTick,
                                       MobFactory mobFactory) {
        triggerSkills(event, damager, victim, state, currentTick, mobFactory, SkillType.ACTIVE, 100);
        triggerSkills(null, damager, victim, state, currentTick, mobFactory, SkillType.DUAL, 60);
    }

    /** 玩家攻击怪物时触发 PASSIVE 与 DUAL 技能。 */
    public void triggerPlayerAttackSkills(EntityDamageByEntityEvent event, LivingEntity victim,
                                          Player damager, MobState state, long currentTick,
                                          MobFactory mobFactory) {
        for (Affix affix : state.getProfile().getAffixes()) {
            SkillType type = affix.getSkill().getType();
            if (type != SkillType.PASSIVE && type != SkillType.DUAL) continue;
            SkillConfig skillConfig = config.getSkillConfig(affix.getSkillId());
            if (skillConfig == null) continue;
            int defaultCooldown = type == SkillType.DUAL ? 60 : 0;
            triggerOne(affix, skillConfig, victim, damager, event, state, currentTick,
                    mobFactory, defaultCooldown);
        }
    }

    private void triggerSkills(EntityDamageByEntityEvent event, LivingEntity entity, Player target,
                               MobState state, long currentTick, MobFactory mobFactory,
                               SkillType type, int defaultCooldown) {
        for (Affix affix : state.getProfile().getAffixes()) {
            if (affix.getSkill().getType() != type) continue;
            SkillConfig skillConfig = config.getSkillConfig(affix.getSkillId());
            if (skillConfig == null) continue;
            triggerOne(affix, skillConfig, entity, target, event, state, currentTick,
                    mobFactory, defaultCooldown);
        }
    }

    private void triggerOne(Affix affix, SkillConfig skillConfig, LivingEntity entity,
                            Player target, EntityDamageByEntityEvent triggerEvent,
                            MobState state, long currentTick, MobFactory mobFactory,
                            int defaultCooldown) {
        int cooldown = skillConfig.getInt("cooldown-ticks", defaultCooldown);
        if (cooldown > 0 && state.isOnCooldown(affix.getSkillId(), currentTick)) return;

        SkillContext context = new SkillContext(plugin, entity, state);
        context.setTargetPlayer(target);
        if (triggerEvent != null) context.setTriggerEvent(triggerEvent);
        context.setCurrentTick(currentTick);
        if (mobFactory != null) context.setMobFactory(mobFactory);
        if (!skillAttemptService.fire(affix, context, entity, target, state)) return;
        affix.getSkill().onTrigger(context, skillConfig);
        if (context.isTriggered()) context.commitCooldown(affix.getSkillId(), cooldown);
    }

}
