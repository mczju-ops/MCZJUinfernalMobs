package com.infernalmobs.skill;

import com.infernalmobs.api.InfernalMobHandle;
import com.infernalmobs.model.MobState;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    /** 词条触发事件修改后的参数覆盖（key = config.yml 参数名，如 duration-ticks）。 */
    private final Map<String, Object> paramOverrides = new LinkedHashMap<>();

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

    /** 本次 onTrigger 是否真正触发了技能效果（由技能在通过概率判定后标记，用于“成功后冷却”） */
    private boolean triggered = false;

    public boolean isTriggered() {
        return triggered;
    }

    public void setTriggered(boolean triggered) {
        this.triggered = triggered;
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
     * 触发一个事件，返回 false 表示该事件被取消（Cancellable）。
     * 供技能在“真正触发”时 call 各自的 Post 事件。
     */
    public boolean fire(Event event) {
        if (event == null) return true;
        getPlugin().getServer().getPluginManager().callEvent(event);
        return !(event instanceof Cancellable c && c.isCancelled());
    }

    // === 参数覆盖（由 InfernalAffixPreRollEvent 修改后写入）===

    /** 整体替换参数覆盖（事件触发后由 CombatService 调用）。 */
    public void setParamOverrides(Map<String, Object> overrides) {
        paramOverrides.clear();
        if (overrides != null) paramOverrides.putAll(overrides);
    }

    /** 读取参数覆盖；不存在时返回 null。 */
    public Object getParam(String key) {
        return paramOverrides.get(key);
    }

    public boolean hasParam(String key) {
        return paramOverrides.containsKey(key);
    }

    /** 读取 int 覆盖参数；无覆盖时返回默认值。 */
    public int getIntParam(String key, int def) {
        Object v = paramOverrides.get(key);
        if (v instanceof Number n) return n.intValue();
        return def;
    }

    /** 读取 double 覆盖参数；无覆盖时返回默认值。 */
    public double getDoubleParam(String key, double def) {
        Object v = paramOverrides.get(key);
        if (v instanceof Number n) return n.doubleValue();
        return def;
    }

    // === 死亡掉落收集（InfernalMobDropEvent 聚合用）===

    /**
     * 死亡掉落的收集目标列表。非 null 时，产出掉落类技能（如 dye）改为加入此列表而非直接掉落到世界，
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
