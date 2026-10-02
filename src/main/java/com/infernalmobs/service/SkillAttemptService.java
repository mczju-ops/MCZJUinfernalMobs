package com.infernalmobs.service;

import com.infernalmobs.affix.Affix;
import com.infernalmobs.api.InfernalMobHandle;
import com.infernalmobs.api.event.affix.InfernalAffixAttemptEvent;
import com.infernalmobs.config.ConfigLoader;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.model.MobState;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

/** 统一处理非 STAT 技能触发前的 Attempt 事件。 */
public final class SkillAttemptService {

    private final JavaPlugin plugin;
    private final ConfigLoader config;

    public SkillAttemptService(JavaPlugin plugin, ConfigLoader config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * 设置技能句柄并广播 Attempt 事件。
     * 核心禁用状态在事件广播后统一检查，外部插件仍可观察 Attempt 事件，
     * 但不能通过恢复事件取消状态绕过实体自身的禁用词条。
     *
     * @return 事件未取消且词条未被实体状态禁用时返回 true
     */
    public boolean fire(Affix affix, SkillContext context,
                        LivingEntity entity, LivingEntity target, MobState state) {
        if (affix.getSkill().getType() == SkillType.STAT || plugin == null) return true;
        SkillConfig skillConfig = config.getSkillConfig(affix.getSkillId());
        if (skillConfig != null && !skillConfig.isHolderAllowed(entity.getType())) return false;
        InfernalMobHandle handle = new InfernalMobHandle(entity,
                state.getProfile().getLevel(), state.getProfile().getAffixIds(),
                state.getSuppressedAffixes());
        context.setHandle(handle);
        InfernalAffixAttemptEvent event = new InfernalAffixAttemptEvent(
                affix.getSkillId(), affix.getSkill().getType(), entity, target, handle,
                state.getProfile().getLevel());
        plugin.getServer().getPluginManager().callEvent(event);
        return !event.isCancelled() && !state.isAffixSuppressed(affix.getSkillId());
    }
}
