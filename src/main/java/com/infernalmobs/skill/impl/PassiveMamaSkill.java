package com.infernalmobs.skill.impl;

import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.factory.MobFactory;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 母体：受击时概率生成多只同类型炒鸡怪。
 * - 数量按档位：初级(1-3级)1只、中级(4-6)2只、高级(7-9)3只、炒鸡(10+)4只。
 * - 子怪等级按档位区间；无 baby 形态的怪用体型缩放 0.5 模拟“小只”。
 */
public class PassiveMamaSkill implements Skill {

    private static final int[] TIER_COUNT = { 1, 2, 3, 4 };
    private static final int[][] TIER_LEVEL_RANGE = { { 1, 3 }, { 2, 5 }, { 3, 7 }, { 4, 10 } };

    /** 根据等级得到档位 1~4：初级/中级/高级/炒鸡。 */
    private static int getTier(int level) {
        if (level <= 3) return 1;
        if (level <= 6) return 2;
        if (level <= 9) return 3;
        return 4;
    }

    /** Warden、凋灵等不触发 mama，避免失衡。 */
    private static final Set<EntityType> DISALLOWED_FOR_MAMA = Set.of(EntityType.WARDEN, EntityType.WITHER);

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
        if (DISALLOWED_FOR_MAMA.contains(parent.getType())) {
            debugLog(ctx, "跳过: " + parent.getType() + " 不支持 mama");
            return;
        }

        Set<EntityType> allowed = parseAllowedTypes(config);
        if (!allowed.isEmpty() && !allowed.contains(parent.getType())) {
            debugLog(ctx, "跳过: 类型 " + parent.getType() + " 不在 mama 允许列表");
            return;
        }

        double chance = config.getDouble("chance", 0.15);
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            debugLog(ctx, "跳过: 概率未通过 (chance=" + chance + ")");
            return;
        }
        debugLog(ctx, "概率通过，准备生成子怪");

        MobFactory factory = ctx.getMobFactory();
        if (factory == null) {
            debugLog(ctx, "跳过: MobFactory 为 null");
            return;
        }

        int parentLevel = ctx.getMobState().getProfile().getLevel();
        int tier = getTier(parentLevel);
        boolean countByTier = config.getBoolean("count-by-tier", true);
        int count = countByTier ? TIER_COUNT[tier - 1] : config.getInt("count", 4);
        count = Math.max(1, count);

        int levelMin = config.getInt("level-min", -1);
        int levelMax = config.getInt("level-max", -1);
        int[] levelRange;
        if (levelMin >= 0 && levelMax >= levelMin) {
            levelRange = new int[] { levelMin, levelMax };
        } else {
            int[] def = TIER_LEVEL_RANGE[tier - 1];
            ConfigurationSection tierSec = config.getSection() != null ? config.getSection().getConfigurationSection("tier-level-ranges") : null;
            if (tierSec != null && tierSec.contains(String.valueOf(tier))) {
                ConfigurationSection t = tierSec.getConfigurationSection(String.valueOf(tier));
                if (t != null) {
                    int min = t.getInt("min", def[0]);
                    int max = t.getInt("max", def[1]);
                    levelRange = new int[] { min, Math.max(min, max) };
                } else {
                    levelRange = def;
                }
            } else {
                levelRange = def;
            }
        }

        boolean baby = config.getBoolean("baby", true);
        double noBabyScale = config.getDouble("no-baby-scale", 0.5);

        Location loc = parent.getLocation().clone();
        EntityType parentType = parent.getType();
        debugLog(ctx, "已调度下一 tick 生成 count=" + count + " 档位=" + tier + " 等级区间 " + levelRange[0] + "-" + levelRange[1] + " baby=" + baby + " noBabyScale=" + noBabyScale);

        final int effCount = count;
        final int effMin = levelRange[0];
        final int effMax = Math.max(levelRange[0], levelRange[1]);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (loc.getWorld() == null) {
                    debugLog(ctx, "延迟任务: world 为 null，取消生成");
                    return;
                }
                debugLog(ctx, "延迟任务执行: 开始生成 " + effCount + " 只 " + parentType + " 于 " + loc);
                for (int i = 0; i < effCount; i++) {
                    LivingEntity child = (LivingEntity) loc.getWorld().spawnEntity(loc, parentType);
                    boolean needScale = false;
                    if (baby) {
                        if (child instanceof Ageable ageable) {
                            ageable.setBaby();
                        } else {
                            needScale = true;
                        }
                    }
                    int childLevel = effMin + ThreadLocalRandom.current().nextInt(effMax - effMin + 1);
                    factory.mechanizeWithExcludedAffixes(child, loc, childLevel, List.of("mama"));
                    if (needScale) applyScale(ctx, child, noBabyScale);
                }
                try {
                    loc.getWorld().playSound(loc, org.bukkit.Sound.ENTITY_ZOMBIE_INFECT, 0.8f, 0.8f);
                } catch (IllegalArgumentException ignored) {}
                debugLog(ctx, "生成完成，共 " + effCount + " 只");
            }
        }.runTask(ctx.getPlugin());
    }

    /** 和 /attribute <实体> minecraft:generic.scale base set <值> 等价；API 里常量名为 Attribute.SCALE。 */
    private static void applyScale(SkillContext ctx, LivingEntity entity, double scale) {
        if (scale <= 0.01 || scale > 10.0) return;
        var attr = entity.getAttribute(Attribute.SCALE);
        if (attr != null) {
            attr.setBaseValue(scale);
        } else {
            debugLog(ctx, "applyScale: 实体无 SCALE 属性，实体=" + entity.getType());
        }
    }

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
