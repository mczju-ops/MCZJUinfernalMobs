package com.infernalmobs.skill.impl;

import com.infernalmobs.api.event.InfernalMobMorphEvent;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.factory.MobFactory;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

/**
 * 变形：受击/攻击时概率变成另一种生物。
 * morph 词条被禁用（由外部插件通过 API 设置）时，由 MorphSuppressListener 在事件层阻止触发。
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

        // morph 词条被 morph_controller 禁用时，已由 MorphSuppressListener 在事件层取消，此处不再重复判定

        double chance = config.getDouble("chance", 0.15);
        if (Math.random() >= chance) return;
        if (ctx.isWeakened() && Math.random() < 0.5) return;

        // 优先使用区域 morph 池；未配置则回退全局 morph-types
        List<EntityType> pool = ctx.getMobState() != null ? ctx.getMobState().getMorphTargetTypesOverride() : null;
        if (pool == null || pool.isEmpty()) pool = parseMorphTypes(config);
        EntityType current = entity.getType();
        EntityType target = pickTarget(pool, current);
        if (target == null) return;

        InfernalMobMorphEvent ev = new InfernalMobMorphEvent(entity, ctx.getTargetPlayer(), ctx.getHandle(), ctx.getMobState().getProfile().getLevel(), target);
        if (!ctx.fire(ev)) return;
        target = ev.getTargetType();

        double currentHealth = entity.getHealth();
        org.bukkit.Location soundLoc = entity.getLocation().clone();
        MobFactory factory = ctx.getMobFactory();
        if (factory == null) return;
        ctx.setTriggered(true);

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
