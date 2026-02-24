package com.infernalmobs.service;

import com.infernalmobs.affix.Affix;
import com.infernalmobs.config.ConfigLoader;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.factory.MobFactory;
import com.infernalmobs.model.MobState;
import com.infernalmobs.skill.SkillContext;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * 技能装配服务。将词条绑定到怪物，调用各技能的 onEquip。
 */
public class SkillService {

    private final JavaPlugin plugin;
    private final ConfigLoader config;

    public SkillService(JavaPlugin plugin, ConfigLoader config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void equip(LivingEntity entity, MobState mobState, List<Affix> affixes) {
        equip(entity, mobState, affixes, null);
    }

    /** 装配技能，可选传入 MobFactory（供 mounted 等需要生成炒鸡怪的技能使用）。 */
    public void equip(LivingEntity entity, MobState mobState, List<Affix> affixes, MobFactory mobFactory) {
        for (Affix affix : affixes) {
            SkillConfig skillConfig = config.getSkillConfig(affix.getSkillId());
            if (skillConfig == null) continue;

            SkillContext ctx = new SkillContext(plugin, entity, mobState);
            if (mobFactory != null) ctx.setMobFactory(mobFactory);
            affix.getSkill().onEquip(ctx, skillConfig);
        }
    }

    public void unequip(LivingEntity entity, MobState mobState, List<Affix> affixes) {
        for (Affix affix : affixes) {
            SkillContext ctx = new SkillContext(plugin, entity, mobState);
            affix.getSkill().onUnequip(ctx);
        }
    }
}
