package com.infernalmobs.api.event;

import com.infernalmobs.api.InfernalMobHandle;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * 炒鸡怪被玩家击杀事件（<b>不可取消</b>）。
 *
 * <p>在实体死亡处理中、确认击杀者为玩家后同步触发，供自定义进度 / 成就类插件监听，
 * 例如「击杀一只同时拥有 xx 与 yy 词条的炒鸡怪」「在深渊世界击杀 10 级炒鸡怪」等。
 * 通过 {@link #getHandle()} 可读取等级与词条列表（{@link InfernalMobHandle#getAffixIds()}）。
 */
public class InfernalMobKillEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final LivingEntity entity;
    private final InfernalMobHandle handle;
    private final Player killer;
    private final int level;
    private final Location location;

    public InfernalMobKillEvent(LivingEntity entity, InfernalMobHandle handle,
                                Player killer, int level, Location location) {
        this.entity = entity;
        this.handle = handle;
        this.killer = killer;
        this.level = level;
        this.location = location;
    }

    /** 被击杀的炒鸡怪实体（死亡事件处理期间仍可访问）。 */
    @NotNull
    public LivingEntity getEntity() {
        return entity;
    }

    /** 门面句柄（只读：等级 / 词条），词条列表见 {@link InfernalMobHandle#getAffixIds()}。 */
    @NotNull
    public InfernalMobHandle getHandle() {
        return handle;
    }

    /** 击杀玩家。 */
    @NotNull
    public Player getKiller() {
        return killer;
    }

    /** 炒鸡怪等级。 */
    public int getLevel() {
        return level;
    }

    /** 死亡位置。 */
    @NotNull
    public Location getLocation() {
        return location;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
