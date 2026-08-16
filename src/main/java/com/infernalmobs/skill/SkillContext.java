package com.infernalmobs.skill;

import com.infernalmobs.api.InfernalMobHandle;
import com.infernalmobs.api.event.affix.InfernalAffixTriggeredEvent;
import com.infernalmobs.model.MobState;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * 技能执行时的上下文，封装实体、状态、事件等信息。
 */
public class SkillContext {

    private final JavaPlugin plugin;
    private final LivingEntity entity;
    private final MobState mobState;
    private Event triggerEvent;
    private Player targetPlayer;
    /** 魔法王套装削弱：true 时技能按各自削弱方案生效（保留字段；MagicKingArmorService 已移除，外部改由事件接管） */
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

    /** 本次执行是否已经广播专用 Triggered 事件；事件被取消也仍视为成功触发。 */
    private boolean triggered = false;

    public boolean isTriggered() {
        return triggered;
    }

    /** 炒鸡怪门面句柄（由 CombatService 在触发事件前写入，供技能触发 Post 事件复用）。 */
    private InfernalMobHandle handle;

    public InfernalMobHandle getHandle() {
        return handle;
    }

    public void setHandle(InfernalMobHandle handle) {
        this.handle = handle;
    }

    /** 惰性构造 handle（onEquip 阶段尚无 handle 时，从 mobState 构建）。 */
    public InfernalMobHandle getOrCreateHandle() {
        if (handle == null && mobState != null && entity != null) {
            handle = new InfernalMobHandle(entity,
                    mobState.getProfile().getLevel(),
                    mobState.getProfile().getAffixIds(),
                    mobState.getSuppressedAffixes());
        }
        return handle;
    }

    /**
     * 广播事件，返回 false 表示该事件被取消（Cancellable）。
     * 广播 {@link InfernalAffixTriggeredEvent} 时自动记录本次词条已经成功触发；
     * 该标记不受取消状态影响，供调用方按统一契约提交冷却。
     */
    public boolean fire(Event event) {
        if (event == null) return true;
        if (event instanceof InfernalAffixTriggeredEvent) triggered = true;
        getPlugin().getServer().getPluginManager().callEvent(event);
        return !(event instanceof Cancellable c && c.isCancelled());
    }

    /**
     * 从本次触发 tick 起提交词条冷却。非正冷却不写入状态。
     * 延迟触发的技能也通过此入口提交，避免各技能自行拼接冷却截止时间。
     */
    public void commitCooldown(String skillId, int cooldownTicks) {
        if (mobState == null || skillId == null || cooldownTicks <= 0) return;
        mobState.setCooldown(skillId, currentTick + cooldownTicks);
    }

    // === 死亡掉落收集（InfernalMobDropEvent 聚合用）===

    /**
     * 死亡掉落的收集目标列表。非 null 时，产出掉落类技能改为加入此列表而非直接掉落到世界，
     * 由 LootService 聚合后统一触发掉落事件再落世界。
     */
    private List<ItemStack> collectTo;

    public List<ItemStack> getCollectTo() {
        return collectTo;
    }

    public void setCollectTo(List<ItemStack> collectTo) {
        this.collectTo = collectTo;
    }
}
