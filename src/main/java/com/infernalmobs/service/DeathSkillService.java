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
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/** 负责 DEATH 词条的亡语触发和掉落聚合上下文。 */
public final class DeathSkillService {

    private final JavaPlugin plugin;
    private final ConfigLoader config;
    private final SkillAttemptService skillAttemptService;

    public DeathSkillService(JavaPlugin plugin, ConfigLoader config,
                             SkillAttemptService skillAttemptService) {
        this.plugin = plugin;
        this.config = config;
        this.skillAttemptService = skillAttemptService;
    }

    /**
     * 触发实体的 DEATH 词条。
     *
     * @param collectTo 非 null 时，掉落类技能将产出收集到该列表
     */
    public void trigger(EntityDeathEvent event, LivingEntity entity, MobState state,
                        List<ItemStack> collectTo, long currentTick, MobFactory mobFactory) {
        Player killer = entity.getKiller();
        for (Affix affix : state.getProfile().getAffixes()) {
            if (affix.getSkill().getType() != SkillType.DEATH) continue;
            SkillConfig skillConfig = config.getSkillConfig(affix.getSkillId());
            if (skillConfig == null) continue;

            SkillContext context = new SkillContext(plugin, entity, state);
            context.setTargetPlayer(killer);
            context.setTriggerEvent(event);
            context.setCurrentTick(currentTick);
            if (mobFactory != null) context.setMobFactory(mobFactory);
            context.setCollectTo(collectTo);
            if (!skillAttemptService.fire(affix, context, entity, killer, state)) continue;
            affix.getSkill().onTrigger(context, skillConfig);
        }
    }

}
