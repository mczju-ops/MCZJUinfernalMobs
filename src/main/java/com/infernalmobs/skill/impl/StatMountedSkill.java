package com.infernalmobs.skill.impl;

import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.factory.MobFactory;
import com.infernalmobs.InfernalMobsPlugin;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.ArrayList;
import java.util.Collections;

/**
 * 骑乘：怪物诞生时骑乘一只坐骑（参考原版 InfernalMobs）。
 * 由两个白名单控制：
 * - enabled-riders：哪些实体类型可触发 mounted
 * - enabled-mounts：可生成且会被炒鸡化的坐骑类型池
 */
public class StatMountedSkill implements Skill {

    private static void debugLog(SkillContext ctx, String msg) {
        if (ctx == null || ctx.getPlugin() == null) return;
        if (ctx.getPlugin() instanceof InfernalMobsPlugin p && p.getConfigLoader().isDebug()) {
            p.getLogger().info("[InfernalMobs:debug:mounted] " + msg);
        }
    }

    @Override
    public String getId() {
        return "mounted";
    }

    @Override
    public SkillType getType() {
        return SkillType.STAT;
    }

    @Override
    public void onEquip(SkillContext ctx, SkillConfig config) {
        if (ctx == null) {
            return;
        }
        debugLog(ctx, "进入 onEquip（入口）");

        LivingEntity rider = ctx.getEntity();
        if (rider == null) {
            debugLog(ctx, "提前返回：rider == null");
            return;
        }
        // 自然刷怪阶段，onEquip 可能早于实体完全有效化；这里不要因为 !isValid 立即放弃。
        // 真正可执行性放到下一 tick 的延迟任务中再判断。
        if (!rider.isValid()) {
            debugLog(ctx, "注意：当前 tick rider 暂未 valid，延迟到下一 tick 再判定 type=" + rider.getType() + " dead=" + rider.isDead());
        }
        debugLog(ctx, "触发 onEquip rider=" + rider.getType() + "@" + rider.getUniqueId());

        Set<EntityType> enabledRiders = parseEntityTypeSet(config, "enabled-riders", "enabledRiders");
        if (!enabledRiders.isEmpty() && !enabledRiders.contains(rider.getType())) {
            debugLog(ctx, "跳过：rider 不在 enabled-riders 白名单内 rider=" + rider.getType());
            return;
        }

        Set<EntityType> enabledMounts = parseEntityTypeSet(config, "enabled-mounts", "enabledMounts");
        // 先从白名单里筛出“可生成且是 LivingEntity”的有效坐骑候选，再进行随机。
        List<EntityType> mountPool = enabledMounts.stream()
                .filter(EntityType::isSpawnable)
                .filter(t -> t.getEntityClass() != null && LivingEntity.class.isAssignableFrom(t.getEntityClass()))
                .toList();
        debugLog(ctx, "候选坐骑池大小=" + mountPool.size() + " 原始白名单数量=" + enabledMounts.size());
        if (mountPool.isEmpty()) {
            debugLog(ctx, "跳过：候选坐骑池为空");
            return;
        }

        // 对自然刷怪，onEquip 可能发生在 CreatureSpawnEvent 事件栈内；
        // 延迟一 tick 再生成/挂载坐骑，避免时机过早导致挂载失败。
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!rider.isValid() || rider.isDead() || rider.getWorld() == null) {
                    debugLog(ctx, "延迟任务取消：rider 已无效/死亡/无世界");
                    return;
                }
                if (rider.getVehicle() != null) {
                    debugLog(ctx, "延迟任务跳过：rider 已有载具 vehicle=" + rider.getVehicle().getType());
                    return;
                }
                // 抬高生成点，降低坐骑/骑手卡墙窒息概率。
                Location loc = rider.getLocation().add(0, 1.25, 0);

                // 重要：部分实体类型在特定版本/场景下 addPassenger 可能失败。
                // 这里对候选池做一次随机洗牌，逐个尝试，直到成功挂载或耗尽候选。
                List<EntityType> attempts = new ArrayList<>(mountPool);
                Collections.shuffle(attempts, ThreadLocalRandom.current());
                for (EntityType type : attempts) {
                    if (type == null) continue;
                    Entity mount = rider.getWorld().spawnEntity(loc, type);
                    // enabled-mounts 决定“生成什么坐骑”，也决定“哪些坐骑会被炒鸡化”
                    maybeMechanizeMount(ctx, config, mount, loc);

                    boolean mounted = mount.addPassenger(rider);
                    debugLog(ctx, "尝试坐骑 type=" + type + " addPassenger=" + mounted);
                    if (mounted) {
                        debugLog(ctx, "挂载成功 rider=" + rider.getType() + " mount=" + type);
                        return;
                    }

                    // 挂载失败：清理本次尝试，继续换下一个候选
                    mount.remove();
                }
                debugLog(ctx, "所有候选坐骑尝试失败 rider=" + rider.getType());
            }
        }.runTask(ctx.getPlugin());
    }

    @Override
    public void onUnequip(SkillContext ctx) {}

    private void maybeMechanizeMount(SkillContext ctx, SkillConfig config, Entity mount, Location loc) {
        if (!(mount instanceof LivingEntity mountEntity)) return;

        MobFactory factory = ctx.getMobFactory();
        if (factory == null) return;

        int levelMin = config.getInt("level-min", 1);
        int levelMax = Math.max(config.getInt("level-max", 10), levelMin);
        int level = levelMin + ThreadLocalRandom.current().nextInt(levelMax - levelMin + 1);
        factory.mechanizeWithLevel(mountEntity, loc, level);
    }

    private Set<EntityType> parseEntityTypeSet(SkillConfig config, String... keys) {
        Set<EntityType> out = new HashSet<>();
        if (config == null || keys == null) return out;
        for (String key : keys) {
            if (key == null || key.isBlank()) continue;
            List<String> raw = config.getStringList(key);
            if (raw == null || raw.isEmpty()) continue;
            for (String s : raw) {
                if (s == null || s.isBlank()) continue;
                try {
                    EntityType type = EntityType.valueOf(s.trim().toUpperCase(Locale.ROOT));
                    out.add(type);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return out;
    }
}
