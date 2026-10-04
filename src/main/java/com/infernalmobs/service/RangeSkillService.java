package com.infernalmobs.service;

import com.infernalmobs.affix.Affix;
import com.infernalmobs.config.ConfigLoader;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.factory.MobFactory;
import com.infernalmobs.model.MobState;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import com.infernalmobs.skill.impl.RangeNecromancerSkill;
import org.bukkit.GameMode;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** 负责 RANGE 词条的周期扫描、目标选择和触发。 */
public final class RangeSkillService {

    private final JavaPlugin plugin;
    private final ConfigLoader config;
    private final SkillAttemptService skillAttemptService;

    public RangeSkillService(JavaPlugin plugin, ConfigLoader config,
                             SkillAttemptService skillAttemptService) {
        this.plugin = plugin;
        this.config = config;
        this.skillAttemptService = skillAttemptService;
    }

    /** 每次调用检查一个实体的所有范围技能；调用频率由 CombatService 控制。 */
    public void tick(LivingEntity entity, MobState state, long currentTick, MobFactory mobFactory) {
        for (Affix affix : state.getProfile().getAffixes()) {
            if (affix.getSkill().getType() != SkillType.RANGE) continue;
            SkillConfig skillConfig = config.getSkillConfig(affix.getSkillId());
            if (skillConfig == null) continue;

            Player target = findNearestPlayer(entity, skillConfig.getDouble("range", 8));
            if (target == null) continue;

            int cooldown = skillConfig.getInt("cooldown-ticks", 100);
            if (cooldown > 0 && state.isOnCooldown(affix.getSkillId(), currentTick)) continue;

            if (isProjectileSkill(affix.getSkillId())) {
                long lastProjectile = state.getBuff(RangeNecromancerSkill.PROJECTILE_BUFF);
                if (lastProjectile > 0 && currentTick - lastProjectile < 40) continue;
            }

            SkillContext context = new SkillContext(plugin, entity, state);
            context.setTargetPlayer(target);
            context.setCurrentTick(currentTick);
            if (mobFactory != null) context.setMobFactory(mobFactory);
            if (!skillAttemptService.fire(affix, context, entity, target, state)) continue;
            if (Math.random() >= skillConfig.getDouble("chance", 0.02)) continue;

            affix.getSkill().onTrigger(context, skillConfig);
            if (!context.isTriggered()) continue;
            context.commitCooldown(affix.getSkillId(), cooldown);
            if (isProjectileSkill(affix.getSkillId())) {
                state.setBuff(RangeNecromancerSkill.PROJECTILE_BUFF, currentTick);
            }
        }
    }

    private boolean isProjectileSkill(String skillId) {
        return "ghastly".equals(skillId) || "necromancer".equals(skillId);
    }

    private Player findNearestPlayer(LivingEntity entity, double range) {
        Player nearest = null;
        double minDistanceSquared = range * range;
        for (Player player : entity.getWorld().getPlayers()) {
            if (!player.isOnline() || player.getGameMode() == GameMode.SPECTATOR) continue;
            double distanceSquared = player.getLocation().distanceSquared(entity.getLocation());
            if (distanceSquared >= minDistanceSquared) continue;
            minDistanceSquared = distanceSquared;
            nearest = player;
        }
        return nearest;
    }
}
