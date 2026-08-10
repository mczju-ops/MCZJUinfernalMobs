package com.infernalmobs.api.event;

import com.infernalmobs.api.InfernalMobHandle;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * 炒鸡怪生成事件：在某实体被炒鸡化时触发，时机在「等级/词条已计算」之后、
 * 「技能装配 / 血量数值 / 命名 / 注册」之前。
 *
 * <p>外部插件可：
 * <ul>
 *   <li>{@link #setCancelled(boolean)} — 阻止本次炒鸡化（实体保持普通怪）；</li>
 *   <li>{@link #getHandle()} — 修改等级 / 词条 / 显示名，装配与数值会按修改后生效。</li>
 * </ul>
 */
public class InfernalMobSpawnEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final LivingEntity entity;
    private final InfernalMobHandle handle;
    private final Location location;
    private final int level;
    private boolean cancelled = false;

    public InfernalMobSpawnEvent(LivingEntity entity, InfernalMobHandle handle, Location location, int level) {
        this.entity = entity;
        this.handle = handle;
        this.location = location;
        this.level = level;
    }

    /** 被炒鸡化的实体。 */
    @NotNull
    public LivingEntity getEntity() {
        return entity;
    }

    /** 门面句柄（可编辑等级 / 词条 / 显示名）。 */
    @NotNull
    public InfernalMobHandle getHandle() {
        return handle;
    }

    /** 生成位置。 */
    @NotNull
    public Location getLocation() {
        return location;
    }

    /** 计算出的初始等级（监听器可能已通过 handle 修改）。 */
    public int getLevel() {
        return level;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
