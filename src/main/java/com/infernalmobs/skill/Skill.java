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
     * 被动/主动技能在特定时机触发时调用。
     * 数值类技能通常不实现此方法。
     */
    default void onTrigger(SkillContext ctx, SkillConfig config) {
        // 默认空实现
    }
}
