package com.infernalmobs.skill.impl;

import com.infernalmobs.api.event.affix.triggered.InfernalMobMorphEvent;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.factory.MobFactory;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import com.infernalmobs.config.SoundConfig;
import com.infernalmobs.util.SoundPlayback;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 变形：受击/攻击时概率变成另一种生物。
 * morph 词条被禁用（由外部插件通过 API 设置）时，由 MorphSuppressListener 在事件层阻止触发。
 */
public class DualMorphSkill implements Skill {

    /** 每只实体至多保留一个尚未执行的变形任务，避免实体失效后仍执行旧请求。 */
    private final Map<UUID, BukkitTask> pendingMorphs = new ConcurrentHashMap<>();

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
    public void onUnequip(SkillContext ctx) {
        if (ctx == null || ctx.getEntity() == null) return;
        BukkitTask task = pendingMorphs.remove(ctx.getEntity().getUniqueId());
        if (task != null) task.cancel();
    }

    @Override
    public void onTrigger(SkillContext ctx, SkillConfig config) {
        LivingEntity entity = ctx.getEntity();
        if (entity == null || !entity.isValid()) return;

        // morph 词条被 morph_controller 禁用时，已由 MorphSuppressListener 在事件层取消，此处不再重复判定

        double chance = config.getDouble("chance", 0.15);
        if (Math.random() >= chance) return;
        if (ctx.isWeakened() && Math.random() < 0.5) return;

        List<EntityType> pool = ctx.getMobState() != null
                ? ctx.getMobState().getMorphTargetTypes()
                : List.of();
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
        SoundConfig sound = config.getSound("sound");

        EntityType finalTarget = target;
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                pendingMorphs.remove(entity.getUniqueId());
                if (!entity.isValid()) return;
                factory.morphEntity(entity, ctx.getMobState(), finalTarget, currentHealth);
                SoundPlayback.broadcast(soundLoc, sound);
            }
        }.runTask(ctx.getPlugin());
        BukkitTask previous = pendingMorphs.put(entity.getUniqueId(), task);
        if (previous != null) previous.cancel();
    }


    // ── 工具方法 ──────────────────────────────────────────────────────

    private EntityType pickTarget(List<EntityType> pool, EntityType exclude) {
        List<EntityType> candidates = pool.stream().filter(t -> t != exclude).toList();
        if (candidates.isEmpty()) return null;
        return candidates.get((int) (Math.random() * candidates.size()));
    }
}
