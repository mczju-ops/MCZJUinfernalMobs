package com.infernalmobs.skill;

import com.infernalmobs.config.SkillConfig;

/**
 * 技能接口。
 * 所有技能（主动 / 被动 / 数值）均实现此接口。
 * 新增技能时：在 SkillRegistry 中注册，并实现对应逻辑。
 */
public interface Skill {

    /**
     * 技能唯一 ID，与 config.yml 中 skills.xxx 对应。
     */
    String getId();

    /**
     * 技能类型。
     */
    SkillType getType();

    /**
     * 装配时调用（怪物生成并绑定词条后）。
     * 数值类技能在此处修改 MobState 的 StatMap；
     * 被动/主动技能可注册监听器等。
     */
    void onEquip(SkillContext ctx, SkillConfig config);

    /**
     * 卸下时调用（怪物死亡或清理时）。
     */
    void onUnequip(SkillContext ctx);

    /**
     * 非 STAT 技能在 Attempt 事件未被取消后调用。
     * 实现应在此完成技能条件与概率判定；确认成功触发后，先通过
     * {@link SkillContext#fire(org.bukkit.event.Event)} 广播对应的 Triggered 事件，再应用效果。
     * 数值类技能通常不实现此方法。
     */
    default void onTrigger(SkillContext ctx, SkillConfig config) {
        // 默认空实现
    }
}
