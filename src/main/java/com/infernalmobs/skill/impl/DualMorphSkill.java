package com.infernalmobs.skill.impl;

import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.factory.MobFactory;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

/**
 * 变形：受击/攻击时概率变成另一种生物。
 * 玩家手持 morph_controller（magicItemId）可消耗一次，禁用触发本次变身的怪物 morph 词条。
 */
public class DualMorphSkill implements Skill {

    private static final EntityType[] MORPH_TYPES = {
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER,
            EntityType.SPIDER, EntityType.CAVE_SPIDER, EntityType.WITCH,
            EntityType.HUSK, EntityType.STRAY, EntityType.DROWNED,
            EntityType.ENDERMAN
    };

    @Override
    public String getId() {
        return "morph";
    }

    @Override
    public SkillType getType() {
        return SkillType.DUAL;
    }

    @Override
    public void onEquip(SkillContext ctx, SkillConfig config) {}

    @Override
    public void onUnequip(SkillContext ctx) {}

    @Override
    public void onTrigger(SkillContext ctx, SkillConfig config) {
        LivingEntity entity = ctx.getEntity();
        if (entity == null || !entity.isValid()) return;

        // 若词条已被 morph_controller 禁用，直接跳过
        if (ctx.getMobState() != null && ctx.getMobState().isAffixSuppressed("morph")) return;

        double chance = config.getDouble("chance", 0.15);
        if (Math.random() >= chance) return;
        if (ctx.isWeakened() && Math.random() < 0.5) return;

        // 优先使用区域 morph 池；未配置则回退全局 morph-types
        List<EntityType> pool = ctx.getMobState() != null ? ctx.getMobState().getMorphTargetTypesOverride() : null;
        if (pool == null || pool.isEmpty()) pool = parseMorphTypes(config);
        EntityType current = entity.getType();
        EntityType target = pickTarget(pool, current);
        if (target == null) return;

        double currentHealth = entity.getHealth();
        org.bukkit.Location soundLoc = entity.getLocation().clone();
        MobFactory factory = ctx.getMobFactory();
        if (factory == null) return;

        String soundKey = config.getString("sound", "BLOCK_ENDER_CHEST_OPEN");
        float soundVolume = (float) config.getDouble("sound-volume", 0.6);
        float soundPitch = (float) config.getDouble("sound-pitch", 0.7);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!entity.isValid()) return;
                factory.morphEntity(entity, ctx.getMobState(), target, currentHealth);
                if (soundLoc.getWorld() != null) {
                    try {
                        org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundKey.toUpperCase().replace(".", "_"));
                        soundLoc.getWorld().playSound(soundLoc, sound, soundVolume, soundPitch);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }.runTask(ctx.getPlugin());
    }

    // ── morph_controller 道具检测 ──────────────────────────────────────

    // ── 禁用粒子动画（供 MagicItemListener 右键触发调用）─────────────────

    /**
     * 四面八方的不祥粒子从大半径汇聚冲向怪物中心，最后在中心爆散收尾。
     * 供 MagicItemListener（右键触发）共用。
     */
    public static void spawnSuppressParticles(LivingEntity entity, org.bukkit.plugin.java.JavaPlugin plugin, String particleKey) {
        if (entity == null || !entity.isValid()) return;
        Location snapLoc = entity.getLocation().clone();
        if (snapLoc.getWorld() == null) return;

        if (particleKey == null || particleKey.isBlank()) particleKey = "TRIAL_OMEN";
        Particle particle;
        try {
            particle = Particle.valueOf(particleKey.toUpperCase().replace(".", "_"));
        } catch (IllegalArgumentException ignored) {
            particle = Particle.TRIAL_OMEN;
        }
        final Particle fp = particle;

        final int TOTAL = 20;           // 汇聚帧数
        final double MAX_RADIUS = 2.8;  // 起始半径
        final double CENTER_Y = 0.9;    // 怪物躯干中心高度
        final int PER_TICK = 10;        // 每帧粒子数

        // 预先生成均匀球面方向（黄金角螺旋分布，PER_TICK 个方向固定）
        final double[][] dirs = new double[PER_TICK][3];
        for (int i = 0; i < PER_TICK; i++) {
            double phi = Math.acos(1 - 2.0 * (i + 0.5) / PER_TICK);
            double theta = Math.PI * (1 + Math.sqrt(5)) * i;
            dirs[i][0] = Math.sin(phi) * Math.cos(theta);
            dirs[i][1] = Math.cos(phi);
            dirs[i][2] = Math.sin(phi) * Math.sin(theta);
        }

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (tick > TOTAL) { cancel(); return; }
                Location base = entity.isValid() ? entity.getLocation() : snapLoc;
                if (base.getWorld() == null) { cancel(); return; }

                double cx = base.getX();
                double cy = base.getY() + CENTER_Y;
                double cz = base.getZ();

                if (tick < TOTAL) {
                    // 半径从 MAX_RADIUS 线性缩到 0
                    double r = MAX_RADIUS * (1.0 - (double) tick / TOTAL);
                    for (double[] d : dirs) {
                        base.getWorld().spawnParticle(fp,
                                cx + d[0] * r,
                                cy + d[1] * r * 0.6,  // 垂直方向压扁一点，更贴近怪物体型
                                cz + d[2] * r,
                                1, 0, 0, 0, 0);
                    }
                } else {
                    // 最后一帧：中心爆散
                    base.getWorld().spawnParticle(fp, cx, cy, cz,
                            30, 0.3, 0.3, 0.3, 0.05);
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ── 工具方法 ──────────────────────────────────────────────────────

    private List<EntityType> parseMorphTypes(SkillConfig config) {
        List<String> raw = config.getStringList("morph-types");
        if (raw == null || raw.isEmpty()) return List.of(MORPH_TYPES);
        List<EntityType> out = new ArrayList<>();
        for (String s : raw) {
            try {
                EntityType t = EntityType.valueOf(s.toUpperCase());
                if (t.isSpawnable() && LivingEntity.class.isAssignableFrom(t.getEntityClass())) out.add(t);
            } catch (IllegalArgumentException ignored) {}
        }
        return out.isEmpty() ? List.of(MORPH_TYPES) : out;
    }

    private EntityType pickTarget(List<EntityType> pool, EntityType exclude) {
        List<EntityType> candidates = pool.stream().filter(t -> t != exclude).toList();
        if (candidates.isEmpty()) return null;
        return candidates.get((int) (Math.random() * candidates.size()));
    }
}
