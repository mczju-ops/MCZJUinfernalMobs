package com.infernalmobs.skill;

import com.infernalmobs.model.MobState;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 技能执行时的上下文，封装实体、状态、事件等信息。
 */
public class SkillContext {

    private final JavaPlugin plugin;
    private final LivingEntity entity;
    private final MobState mobState;
    private Event triggerEvent;
    private Player targetPlayer;
    /** 魔法王套装削弱：true 时技能按各自削弱方案生效 */
    private boolean weakened = false;
    /** 当前战斗 tick（由 CombatService 驱动，用于与冷却/持续时间对齐） */
    private long currentTick = 0;

    public SkillContext(JavaPlugin plugin, LivingEntity entity, MobState mobState) {
        this.plugin = plugin;
        this.entity = entity;
        this.mobState = mobState;
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    public LivingEntity getEntity() {
        return entity;
    }

    public MobState getMobState() {
        return mobState;
    }

    public Event getTriggerEvent() {
        return triggerEvent;
    }

    public void setTriggerEvent(Event triggerEvent) {
        this.triggerEvent = triggerEvent;
    }

    public Player getTargetPlayer() {
        return targetPlayer;
    }

    public void setTargetPlayer(Player targetPlayer) {
        this.targetPlayer = targetPlayer;
    }

    private Object mobFactory;

    public void setMobFactory(Object mobFactory) {
        this.mobFactory = mobFactory;
    }

    @SuppressWarnings("unchecked")
    public <T> T getMobFactory() {
        return (T) mobFactory;
    }

    public boolean isWeakened() {
        return weakened;
    }

    public void setWeakened(boolean weakened) {
        this.weakened = weakened;
    }

    public long getCurrentTick() {
        return currentTick;
    }

    public void setCurrentTick(long currentTick) {
        this.currentTick = currentTick;
    }
}
