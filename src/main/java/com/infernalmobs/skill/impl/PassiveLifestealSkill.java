package com.infernalmobs.skill.impl;

import com.infernalmobs.api.event.affix.triggered.InfernalMobLifestealEvent;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.service.CombatService;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 吸血：受击后按固定 20 tick 周期持续回血。
 * 同一怪物重复触发时刷新剩余次数与治疗量，不叠加任务或重置下一次治疗时点。
 */
public class PassiveLifestealSkill implements Skill {

    private static final int HEAL_PERIOD_TICKS = 20;

    private final Map<UUID, ActiveLifesteal> activeLifesteals = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return "lifesteal";
    }

    @Override
    public SkillType getType() {
        return SkillType.PASSIVE;
    }

    @Override
    public void onEquip(SkillContext ctx, SkillConfig config) {}

    @Override
    public void onUnequip(SkillContext ctx) {
        stop(ctx.getEntity().getUniqueId());
    }

    @Override
    public void onTrigger(SkillContext ctx, SkillConfig config) {
        if (ctx.isWeakened() && Math.random() < 0.5) return;

        int durationTicks = config.getInt("duration-ticks", 80);
        double healPerSecond = config.getDouble("heal-per-second", 1.0);
        InfernalMobLifestealEvent event = new InfernalMobLifestealEvent(
                ctx.getEntity(), ctx.getTargetPlayer(), ctx.getHandle(),
                ctx.getMobState().getProfile().getLevel(), durationTicks, healPerSecond);
        if (!ctx.fire(event)) return;

        refresh(ctx, event.getDurationTicks(), event.getHealPerSecond());
    }

    private void refresh(SkillContext ctx, int durationTicks, double healPerSecond) {
        UUID uuid = ctx.getEntity().getUniqueId();
        int healCount = durationTicks / HEAL_PERIOD_TICKS;
        if (healCount <= 0 || healPerSecond <= 0.0) {
            stop(uuid);
            return;
        }

        ActiveLifesteal active = new ActiveLifesteal(healCount, healPerSecond);
        ActiveLifesteal existing = activeLifesteals.putIfAbsent(uuid, active);
        if (existing != null) {
            existing.refresh(healCount, healPerSecond);
            return;
        }

        ScheduledTask task = ctx.getEntity().getScheduler().runAtFixedRate(
                ctx.getPlugin(),
                scheduledTask -> tick(ctx, uuid, active, scheduledTask),
                () -> activeLifesteals.remove(uuid, active),
                HEAL_PERIOD_TICKS,
                HEAL_PERIOD_TICKS);
        if (task == null) {
            activeLifesteals.remove(uuid, active);
            return;
        }

        active.task = task;
        if (activeLifesteals.get(uuid) != active) task.cancel();
    }

    private void tick(SkillContext ctx, UUID uuid, ActiveLifesteal active, ScheduledTask task) {
        if (activeLifesteals.get(uuid) != active) {
            task.cancel();
            return;
        }
        if (!ctx.getEntity().isValid() || ctx.getEntity().isDead()) {
            activeLifesteals.remove(uuid, active);
            task.cancel();
            return;
        }

        double healAmount = active.consumeHeal();
        if (healAmount < 0.0) {
            activeLifesteals.remove(uuid, active);
            task.cancel();
            return;
        }

        double ceiling = CombatService.healCeiling(ctx.getEntity(), ctx.getMobState());
        if (ctx.getEntity().getHealth() < ceiling) {
            ctx.getEntity().setHealth(Math.min(ceiling, ctx.getEntity().getHealth() + healAmount));
        }

        if (active.isFinished()) {
            activeLifesteals.remove(uuid, active);
            task.cancel();
        }
    }

    private void stop(UUID uuid) {
        ActiveLifesteal active = activeLifesteals.remove(uuid);
        if (active != null && active.task != null) active.task.cancel();
    }

    private static final class ActiveLifesteal {
        private int remainingHeals;
        private double healPerSecond;
        private volatile ScheduledTask task;

        private ActiveLifesteal(int remainingHeals, double healPerSecond) {
            refresh(remainingHeals, healPerSecond);
        }

        private synchronized void refresh(int remainingHeals, double healPerSecond) {
            this.remainingHeals = remainingHeals;
            this.healPerSecond = healPerSecond;
        }

        private synchronized double consumeHeal() {
            if (remainingHeals <= 0) return -1.0;
            remainingHeals--;
            return healPerSecond;
        }

        private synchronized boolean isFinished() {
            return remainingHeals <= 0;
        }
    }
}
