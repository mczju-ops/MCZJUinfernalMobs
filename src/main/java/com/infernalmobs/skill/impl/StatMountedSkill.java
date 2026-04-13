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
 * 由三个列表控制：
 * - enabled-riders：哪些实体类型可触发 mounted
 * - infernal-mounts：可生成且会被炒鸡化的坐骑候选
 * - enabled-mounts：可生成但不会被炒鸡化的普通坐骑候选
 * 两个坐骑列表合并为随机池，选中后按来源决定是否炒鸡化。
 * 炒鸡坐骑的等级在生成位置按区域/全局 level 规则计算（与天然炒鸡怪一致），不受 skills.mounted 的 level-min/max 约束。
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

        // infernal-mounts：炒鸡坐骑候选；enabled-mounts：普通坐骑候选
        Set<EntityType> infernalMounts = parseEntityTypeSet(config, "infernal-mounts", "infernalMounts");
        Set<EntityType> normalMounts  = parseEntityTypeSet(config, "enabled-mounts",  "enabledMounts");

        List<EntityType> infernalPool = infernalMounts.stream()
                .filter(EntityType::isSpawnable)
                .filter(t -> t.getEntityClass() != null && LivingEntity.class.isAssignableFrom(t.getEntityClass()))
                .toList();
        List<EntityType> normalPool = normalMounts.stream()
                .filter(t -> !infernalMounts.contains(t))
                .filter(EntityType::isSpawnable)
                .filter(t -> t.getEntityClass() != null && LivingEntity.class.isAssignableFrom(t.getEntityClass()))
                .toList();

        List<EntityType> mountPool = new ArrayList<>();
        mountPool.addAll(infernalPool);
        mountPool.addAll(normalPool);

        debugLog(ctx, "候选坐骑池大小=" + mountPool.size()
                + " (炒鸡=" + infernalPool.size() + " 普通=" + normalPool.size() + ")");
        if (mountPool.isEmpty()) {
            debugLog(ctx, "跳过：候选坐骑池为空");
            return;
        }

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
                Location loc = rider.getLocation().add(0, 1.25, 0);

                List<EntityType> attempts = new ArrayList<>(mountPool);
                Collections.shuffle(attempts, ThreadLocalRandom.current());
                for (EntityType type : attempts) {
                    if (type == null) continue;
                    Entity mount = rider.getWorld().spawnEntity(loc, type);
                    if (infernalPool.contains(type)) {
                        maybeMechanizeMount(ctx, config, mount, loc);
                    }

                    boolean mounted = mount.addPassenger(rider);
                    debugLog(ctx, "尝试坐骑 type=" + type + " infernal=" + infernalPool.contains(type) + " addPassenger=" + mounted);
                    if (mounted) {
                        debugLog(ctx, "挂载成功 rider=" + rider.getType() + " mount=" + type);
                        return;
                    }

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

        // 与骑手同坐标系：坐骑等级按区域/全局 level 规则 roll，不再用技能内 level-min/max（避免超出区域限制）
        int level = factory.computeLevelAt(loc);
        factory.mechanizeWithLevelForced(mountEntity, loc, level);
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
