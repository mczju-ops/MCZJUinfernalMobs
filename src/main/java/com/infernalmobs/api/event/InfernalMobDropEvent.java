package com.infernalmobs.api.event;

import com.infernalmobs.api.InfernalMobHandle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 炒鸡怪死亡掉落事件：在插件产出的所有掉落（等级池加权 / special / 保底 / dye 特殊掉落）聚合后、
 * 落世界之前触发。原版掉落（event.getDrops()）不在此事件中。
 *
 * <p>外部插件可：
 * <ul>
 *   <li>{@link #getDrops()} — 向掉落表追加 / 删除 / 替换物品（如炒鸡渔夫追加「深海碎片」）；</li>
 *   <li>{@link #setCancelled(boolean)} — 取消本次插件掉落（原版掉落不受影响）。</li>
 * </ul>
 */
public class InfernalMobDropEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final LivingEntity entity;
    private final InfernalMobHandle handle;
    private final int level;
    private final Player killer;
    private final List<ItemStack> drops;
    private boolean cancelled = false;

    public InfernalMobDropEvent(LivingEntity entity, InfernalMobHandle handle, int level,
                                Player killer, List<ItemStack> drops) {
        this.entity = entity;
        this.handle = handle;
        this.level = level;
        this.killer = killer;
        this.drops = drops;
    }

    /** 死亡的炒鸡怪实体。 */
    @NotNull
    public LivingEntity getEntity() {
        return entity;
    }

    /** 门面句柄（只读：等级 / 词条）。 */
    @NotNull
    public InfernalMobHandle getHandle() {
        return handle;
    }

    /** 炒鸡怪等级。 */
    public int getLevel() {
        return level;
    }

    /** 击杀玩家（环境杀等场景可能为 null）。 */
    @Nullable
    public Player getKiller() {
        return killer;
    }

    /** 聚合后的插件掉落表（可变，追加 / 删除 / 替换物品）。 */
    @NotNull
    public List<ItemStack> getDrops() {
        return drops;
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
