package com.infernalmobs.service;

import com.infernalmobs.affix.Affix;
import com.infernalmobs.api.InfernalMobHandle;
import com.infernalmobs.api.event.affix.InfernalAffixAttemptEvent;
import com.infernalmobs.model.MobState;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

/** 统一处理非 STAT 技能触发前的 Attempt 事件。 */
public final class SkillAttemptService {

    private final JavaPlugin plugin;

    public SkillAttemptService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 设置技能句柄并广播 Attempt 事件。
     *
     * @return 事件未取消时返回 true
     */
    public boolean fire(Affix affix, SkillContext context,
                        LivingEntity entity, LivingEntity target, MobState state) {
        if (affix.getSkill().getType() == SkillType.STAT || plugin == null) return true;
        InfernalMobHandle handle = new InfernalMobHandle(entity,
                state.getProfile().getLevel(), state.getProfile().getAffixIds(),
                state.getSuppressedAffixes());
        context.setHandle(handle);
        InfernalAffixAttemptEvent event = new InfernalAffixAttemptEvent(
                affix.getSkillId(), affix.getSkill().getType(), entity, target, handle,
                state.getProfile().getLevel());
        plugin.getServer().getPluginManager().callEvent(event);
        return !event.isCancelled();
    }
}
