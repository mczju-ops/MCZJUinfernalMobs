package com.infernalmobs.skill.impl;

import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.factory.MobFactory;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 骑乘：怪物诞生时骑乘一只坐骑（参考原版 InfernalMobs）。
 * 支持 mount-types 列表随机一种；支持任意可生成实体类型；支持 INFERNAL 骑乘炒鸡怪。
 */
public class StatMountedSkill implements Skill {

    /** 作为“炒鸡怪坐骑”时随机使用的实体类型（可骑乘的 LivingEntity） */
    private static final EntityType[] INFERNAL_MOUNT_ENTITY_TYPES = {
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER, EntityType.SPIDER,
            EntityType.CAVE_SPIDER, EntityType.WITCH, EntityType.DROWNED, EntityType.STRAY,
            EntityType.HUSK, EntityType.PIGLIN, EntityType.ENDERMAN
    };

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
        LivingEntity rider = ctx.getEntity();
        if (rider == null || !rider.isValid()) return;

        List<String> mountTypes = config.getStringList("mount-types");
        if (mountTypes == null || mountTypes.isEmpty()) {
            String single = config.getString("mount-type", "HORSE");
            mountTypes = Collections.singletonList(single);
        }

        String chosen = mountTypes.get(ThreadLocalRandom.current().nextInt(mountTypes.size())).trim().toUpperCase();
        if (chosen.isEmpty()) return;

        Location loc = rider.getLocation();

        if ("INFERNAL".equals(chosen)) {
            // 骑乘炒鸡怪：随机一种怪物类型，炒鸡怪化后作为坐骑
            MobFactory factory = ctx.getMobFactory();
            if (factory == null) return;
            EntityType mountType = INFERNAL_MOUNT_ENTITY_TYPES[ThreadLocalRandom.current().nextInt(INFERNAL_MOUNT_ENTITY_TYPES.length)];
            if (!mountType.isSpawnable() || !LivingEntity.class.isAssignableFrom(mountType.getEntityClass())) return;
            LivingEntity mountEntity = (LivingEntity) rider.getWorld().spawnEntity(loc, mountType);
            int levelMin = config.getInt("level-min", 1);
            int levelMax = Math.max(config.getInt("level-max", 10), levelMin);
            int level = levelMin + ThreadLocalRandom.current().nextInt(levelMax - levelMin + 1);
            factory.mechanizeWithLevel(mountEntity, loc, level);
            mountEntity.addPassenger(rider);
            return;
        }

        EntityType type;
        try {
            type = EntityType.valueOf(chosen);
        } catch (IllegalArgumentException e) {
            return;
        }
        if (!type.isSpawnable()) return;

        Entity mount = rider.getWorld().spawnEntity(loc, type);
        mount.addPassenger(rider);
    }

    @Override
    public void onUnequip(SkillContext ctx) {}
}
