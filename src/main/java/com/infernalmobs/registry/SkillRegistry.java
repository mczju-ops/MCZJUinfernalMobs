package com.infernalmobs.registry;

import com.infernalmobs.skill.Skill;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 技能注册表。所有技能在此统一注册，后续按范式添加新技能。
 */
public class SkillRegistry {

    private static final Map<String, Skill> SKILLS = new HashMap<>();

    static {
        registerAll();
    }

    /**
     * 在此处按范式注册所有技能。
     * 新增技能：1. 创建 impl 类实现 Skill
     *          2. 在此调用 register(...)
     */
    private static void registerAll() {
        register(new com.infernalmobs.skill.impl.PassivePoisonousSkill());
        register(new com.infernalmobs.skill.impl.PassiveBlindingSkill());
        register(new com.infernalmobs.skill.impl.PassiveWitheringSkill());
        register(new com.infernalmobs.skill.impl.PassiveQuicksandSkill());
        register(new com.infernalmobs.skill.impl.StatArmouredSkill());
        register(new com.infernalmobs.skill.impl.StatBullwarkSkill());
        register(new com.infernalmobs.skill.impl.Stat1upSkill());
        register(new com.infernalmobs.skill.impl.StatCloakedSkill());
        register(new com.infernalmobs.skill.impl.DualEnderSkill());
        register(new com.infernalmobs.skill.impl.RangeGhastlySkill());
        register(new com.infernalmobs.skill.impl.PassiveLifestealSkill());
        register(new com.infernalmobs.skill.impl.StatSprintSkill());
        register(new com.infernalmobs.skill.impl.PassiveSapperSkill());
        register(new com.infernalmobs.skill.impl.DualWebberSkill());
        register(new com.infernalmobs.skill.impl.PassiveMoltenSkill());
        register(new com.infernalmobs.skill.impl.DualArcherSkill());
        register(new com.infernalmobs.skill.impl.RangeNecromancerSkill());
        register(new com.infernalmobs.skill.impl.ActiveFireworkSkill());
        register(new com.infernalmobs.skill.impl.DeathGhostSkill());
        register(new com.infernalmobs.skill.impl.DeathDyeSkill());
        register(new com.infernalmobs.skill.impl.PassiveConfusingSkill());
        register(new com.infernalmobs.skill.impl.DualThiefSkill());
        register(new com.infernalmobs.skill.impl.RangeTosserSkill());
        register(new com.infernalmobs.skill.impl.DualStormSkill());
        register(new com.infernalmobs.skill.impl.PassiveVengeanceSkill());
        register(new com.infernalmobs.skill.impl.DualWeaknessSkill());
        register(new com.infernalmobs.skill.impl.ActiveBerserkSkill());
        register(new com.infernalmobs.skill.impl.PassiveMamaSkill());
        register(new com.infernalmobs.skill.impl.RangeGravitySkill());
        register(new com.infernalmobs.skill.impl.StatMountedSkill());
        register(new com.infernalmobs.skill.impl.DualMorphSkill());
        register(new com.infernalmobs.skill.impl.DualRefrigerateSkill());
        register(new com.infernalmobs.skill.impl.PassiveRustSkill());
        register(new com.infernalmobs.skill.impl.PassiveVexSummonerSkill());
        register(new com.infernalmobs.skill.impl.PassiveWardenWrathSkill());
        register(new com.infernalmobs.skill.impl.PassiveSwapSkill());
    }

    public static void register(Skill skill) {
        SKILLS.put(skill.getId(), skill);
    }

    public static Skill get(String id) {
        return SKILLS.get(id);
    }

    public static Collection<Skill> getAll() {
        return Collections.unmodifiableCollection(SKILLS.values());
    }

    public static boolean has(String id) {
        return SKILLS.containsKey(id);
    }
}
