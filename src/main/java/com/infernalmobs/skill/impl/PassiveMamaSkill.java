package com.infernalmobs.skill.impl;

import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.factory.MobFactory;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.Location;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 母体：受击时概率生成多只同类型炒鸡怪，默认为幼体。
 * 参考原版：仅当怪物类型在配置的 allowed-types 列表中时才触发（默认仅允许牛、羊、猪、鸡、狼、僵尸等可“生崽”类型）。
 */
public class PassiveMamaSkill implements Skill {

    private static void debugLog(SkillContext ctx, String msg) {
        if (ctx.getPlugin() instanceof com.infernalmobs.InfernalMobsPlugin p && p.getConfigLoader().isDebug()) {
            p.getLogger().info("[InfernalMobs:debug:mama] " + msg);
        }
    }

    @Override
    public String getId() {
        return "mama";
    }

    @Override
    public SkillType getType() {
        return SkillType.PASSIVE;
    }

    @Override
    public void onEquip(SkillContext ctx, SkillConfig config) {}

    @Override
    public void onUnequip(SkillContext ctx) {}

    @Override
    public void onTrigger(SkillContext ctx, SkillConfig config) {
        LivingEntity parent = ctx.getEntity();
        debugLog(ctx, "onTrigger 进入 entity=" + (parent != null ? parent.getType() + "@" + parent.getUniqueId() : "null"));
        if (parent == null || !parent.isValid()) {
            debugLog(ctx, "跳过: parent 无效");
            return;
        }

        Set<EntityType> allowed = parseAllowedTypes(config);
        if (!allowed.isEmpty() && !allowed.contains(parent.getType())) {
            debugLog(ctx, "跳过: 类型 " + parent.getType() + " 不在 mama 允许列表");
            return;
        }

        double chance = config.getDouble("chance", 0.15);
        if (Math.random() >= chance) {
            debugLog(ctx, "跳过: 概率未通过 (chance=" + chance + ")");
            return;
        }
        debugLog(ctx, "概率通过，准备生成子怪");

        MobFactory factory = ctx.getMobFactory();
        if (factory == null) {
            debugLog(ctx, "跳过: MobFactory 为 null");
            return;
        }

        int count = config.getInt("count", 4);
        boolean baby = config.getBoolean("baby", true);
        int levelMin = config.getInt("level-min", 1);
        int levelMax = config.getInt("level-max", 10);
        final int effectiveLevelMin = levelMin;
        final int effectiveLevelMax = Math.max(levelMax, levelMin);

        Location loc = parent.getLocation().clone();
        org.bukkit.entity.EntityType parentType = parent.getType();
        debugLog(ctx, "已调度下一 tick 生成 count=" + count + " 等级区间 " + effectiveLevelMin + "-" + effectiveLevelMax + " baby=" + baby + "（子怪不继承属性，按等级重新 roll 词条）");

        // 延后到下一 tick 生成，避免在伤害事件中生成实体导致不生效
        new BukkitRunnable() {
            @Override
            public void run() {
                if (loc.getWorld() == null) {
                    debugLog(ctx, "延迟任务: world 为 null，取消生成");
                    return;
                }
                debugLog(ctx, "延迟任务执行: 开始生成 " + count + " 只 " + parentType + " 于 " + loc);
                for (int i = 0; i < count; i++) {
                    LivingEntity child = (LivingEntity) loc.getWorld().spawnEntity(loc, parentType);
                    if (baby && child instanceof Ageable ageable) {
                        ageable.setBaby();
                    }
                    int childLevel = effectiveLevelMin + ThreadLocalRandom.current().nextInt(effectiveLevelMax - effectiveLevelMin + 1);
                    factory.mechanizeWithLevel(child, loc, childLevel);
                }
                try {
                    loc.getWorld().playSound(loc, org.bukkit.Sound.ENTITY_ZOMBIE_INFECT, 0.8f, 0.8f);
                } catch (IllegalArgumentException ignored) {}
                debugLog(ctx, "生成完成，共 " + count + " 只");
            }
        }.runTask(ctx.getPlugin());
    }

    /** 解析配置的 allowed-types，空列表表示不限制类型。 */
    private Set<EntityType> parseAllowedTypes(SkillConfig config) {
        List<String> raw = config.getStringList("allowed-types");
        if (raw == null || raw.isEmpty()) return Set.of();
        Set<EntityType> set = new HashSet<>();
        for (String s : raw) {
            try {
                set.add(EntityType.valueOf(s.trim().toUpperCase()));
            } catch (IllegalArgumentException ignored) {}
        }
        return set;
    }
}
