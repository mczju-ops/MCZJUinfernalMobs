package com.infernalmobs.api.event;

import com.infernalmobs.api.InfernalMobHandle;
import com.infernalmobs.skill.SkillType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 词条触发事件：在某个词条技能即将真正生效前触发（chance / cooldown 判定通过之后）。
 *
 * <p>外部插件（如 MagicItems）通过监听本事件实现词条免疫 / 削弱 / 数值修改：
 * <ul>
 *   <li>{@link #setCancelled(boolean)} = 免疫：本次词条触发被整体跳过；</li>
 *   <li>{@link #setParam(String, Object)} = 改数值：修改参数袋后，已接入覆盖读取的技能在应用效果时使用新值。</li>
 * </ul>
 *
 * <p>参数袋初始值为该词条技能在 config.yml 中的配置（skills.&lt;id&gt; 下的键），
 * 监听器可先 {@link #getParam(String)} 读取当前值再修改。
 */
public class InfernalAffixTriggerEvent extends Event implements Cancellable {

    // === 常用参数 key（与 config.yml 中 skills.<id> 的键名保持一致）===
    public static final String PARAM_DURATION_TICKS = "duration-ticks";
    public static final String PARAM_AMPLIFIER = "amplifier";
    public static final String PARAM_CHANCE = "chance";
    public static final String PARAM_COOLDOWN_TICKS = "cooldown-ticks";
    public static final String PARAM_RANGE = "range";
    public static final String PARAM_DAMAGE = "damage";
    public static final String PARAM_FORCE = "force";
    public static final String PARAM_UPWARD = "upward";
    public static final String PARAM_VELOCITY = "velocity";
    public static final String PARAM_FIRE_TICKS = "fire-ticks";

    private static final HandlerList HANDLERS = new HandlerList();

    private final String affixId;
    private final SkillType skillType;
    private final LivingEntity mob;
    private final LivingEntity target;
    private final InfernalMobHandle handle;
    private final int level;
    private final Map<String, Object> parameters = new LinkedHashMap<>();
    private boolean cancelled = false;

    public InfernalAffixTriggerEvent(String affixId, SkillType skillType, LivingEntity mob,
                                     LivingEntity target, InfernalMobHandle handle, int level) {
        this.affixId = affixId;
        this.skillType = skillType;
        this.mob = mob;
        this.target = target;
        this.handle = handle;
        this.level = level;
    }

    /** 触发的词条 skillId（如 "gravity"）。 */
    @NotNull
    public String getAffixId() {
        return affixId;
    }

    /** 词条技能类型。 */
    @NotNull
    public SkillType getSkillType() {
        return skillType;
    }

    /** 触发技能的炒鸡怪实体。 */
    @NotNull
    public LivingEntity getMob() {
        return mob;
    }

    /** 效果作用目标（玩家等）；死亡类技能可能是击杀者，可能为 null。 */
    @Nullable
    public LivingEntity getTarget() {
        return target;
    }

    /** 炒鸡怪门面句柄（只读）。 */
    @NotNull
    public InfernalMobHandle getHandle() {
        return handle;
    }

    /** 炒鸡怪等级。 */
    public int getLevel() {
        return level;
    }

    // === 参数袋 ===

    @Nullable
    public Object getParam(@NotNull String key) {
        return parameters.get(key);
    }

    /** 带默认值的参数读取；值为 null 或不存在时返回默认值。 */
    @SuppressWarnings("unchecked")
    public <T> T getParam(@NotNull String key, @NotNull T def) {
        Object v = parameters.get(key);
        return v != null ? (T) v : def;
    }

    public boolean hasParam(@NotNull String key) {
        return parameters.containsKey(key);
    }

    /** 覆盖/修改参数。值需与该词条技能在 config.yml 中的参数类型一致（int / double / String 等）。 */
    public void setParam(@NotNull String key, @Nullable Object value) {
        parameters.put(key, value);
    }

    /** 参数袋只读视图。 */
    @NotNull
    public Map<String, Object> getParams() {
        return Collections.unmodifiableMap(parameters);
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
