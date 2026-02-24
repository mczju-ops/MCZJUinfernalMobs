package com.infernalmobs.skill;

/**
 * 技能类型枚举。
 */
public enum SkillType {
    /** 主动技能：怪物对玩家造成伤害时触发 */
    ACTIVE,

    /** 被动技能：玩家对怪物造成伤害时触发 */
    PASSIVE,

    /** 数值技能：怪物诞生时触发，仅修改基础属性（血量、攻击、速度等） */
    STAT,

    /** 亡语技能：怪物死亡时触发 */
    DEATH,

    /** 范围技能：玩家处于怪物技能范围内时，有几率释放 */
    RANGE,

    /** 双向技能：受击与攻击时均可触发 */
    DUAL
}
