package com.infernalmobs.service;

import com.infernalmobs.config.ConfigLoader;
import com.infernalmobs.factory.MobFactory;
import com.infernalmobs.model.MobState;
import com.infernalmobs.skill.impl.*;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * 战斗驱动服务。负责：
 * 1. 应用 StatMap 的数值（血量、攻击、速度）- STAT 在诞生时
 * 2. PASSIVE：玩家攻击怪物时触发（荆棘、再生等）
 * 3. ACTIVE：怪物攻击玩家时触发（火球、闪现等）
 * 4. DEATH：怪物死亡时触发（亡语技能）
 * 5. RANGE：玩家在怪物范围内时，有几率释放
 */
public class CombatService {

    private final JavaPlugin plugin;
    private final MobRuntimeRegistry mobRegistry = new MobRuntimeRegistry();
    private final MobStatService mobStatService = new MobStatService();
    private final RangeSkillService rangeSkillService;
    private final AttackSkillService attackSkillService;
    private final AttackDamageService attackDamageService;
    private final DeathSkillService deathSkillService;
    private final DamageReactionSkillService damageReactionSkillService;
    private final SpecialDamageService specialDamageService;
    private MobFactory mobFactory;
    private SkillService skillService;

    public CombatService(JavaPlugin plugin, ConfigLoader config) {
        this.plugin = plugin;
        this.specialDamageService = new SpecialDamageService(plugin);
        this.rangeSkillService = new RangeSkillService(plugin, config);
        this.attackSkillService = new AttackSkillService(plugin, config);
        this.attackDamageService = new AttackDamageService();
        this.deathSkillService = new DeathSkillService(plugin, config);
        this.damageReactionSkillService = new DamageReactionSkillService(plugin, config);
    }

    public void registerMob(UUID entityUuid, MobState state) {
        mobRegistry.register(entityUuid, state);
    }

    public void setSkillService(SkillService skillService) {
        this.skillService = skillService;
    }

    public SpecialDamageService getSpecialDamageService() {
        return specialDamageService;
    }

    /** 终止实体技能会话后再注销内存状态。 */
    public void unequipAndUnregister(LivingEntity entity, MobState state,
                                     SkillService.UnequipReason reason) {
        if (entity == null || state == null) return;
        SkillService currentSkillService = skillService;
        if (currentSkillService != null) {
            currentSkillService.unequip(entity, state, state.getProfile().getAffixes(), reason);
        }
        unregisterMob(entity.getUniqueId());
    }

    public void unregisterMob(UUID entityUuid) {
        mobRegistry.unregister(entityUuid);
    }

    public MobState getMobState(UUID entityUuid) {
        return mobRegistry.get(entityUuid);
    }

    /** 获取当前追踪的炒鸡怪数量 */
    public int getTrackedCount() {
        return mobRegistry.size();
    }

    /** 获取所有追踪中的炒鸡怪（UUID -> MobState），用于外部索引 */
    public Map<UUID, MobState> getTrackedMobs() {
        return mobRegistry.snapshot();
    }

    public void setMobFactory(MobFactory factory) {
        this.mobFactory = factory;
    }

    /** 忽略原版领头僵尸额外生命后的恢复上限。 */
    public static double zombieRecoveryCapWithoutLeaderBonus(LivingEntity entity, MobState state) {
        return MobStatService.zombieRecoveryCapWithoutLeaderBonus(entity, state);
    }

    /** 获取治疗可达到的生命值上限，同时考虑实体属性与领头僵尸加成限制。 */
    public static double healCeiling(LivingEntity entity, MobState state) {
        return MobStatService.healCeiling(entity, state);
    }

    /**
     * 怪物生成后调用，应用 StatMap 中的数值到实体。
     * 最大血量与当前血量均乘以等级倍数，保留原始血量比例：
     *   改造后最大血量 = 原最大血量 × 等级
     *   改造后当前血量 = 原当前血量 × 等级
     * 例：原 20/80 + Lv10 → 200/800（不会强制回满血）。
     */
    public void applyStats(LivingEntity entity, MobState mobState) {
        mobStatService.applyStats(entity, mobState);
    }

    /**
     * 怪物攻击玩家时：应用伤害加成，并触发 ACTIVE 与 DUAL 技能。
     */
    public void onMobAttack(EntityDamageByEntityEvent event, LivingEntity damager, Player victim, MobState mobState) {
        if (!attackDamageService.prepare(event, damager, victim, mobState)) return;
        attackSkillService.triggerMobAttackSkills(event, damager, victim, mobState,
                currentTick, mobFactory);
    }

    /**
     * 怪物受到任意伤害时，检测 1up 等技能。
     */
    public void onMobDamaged(EntityDamageEvent event, LivingEntity victim) {
        MobState state = mobRegistry.get(victim.getUniqueId());
        if (state == null) return;
        damageReactionSkillService.handle(event, victim, state);
    }

    /**
     * 玩家攻击怪物时，触发 PASSIVE 与 DUAL 技能。
     */
    public void onPlayerAttackMob(EntityDamageByEntityEvent event, LivingEntity victim, Player damager, MobState mobState) {
        attackSkillService.triggerPlayerAttackSkills(event, victim, damager, mobState,
                currentTick, mobFactory);
    }

    private volatile long currentTick = 0;

    /** 启动战斗 tick 任务；实体何时自然消失完全交给服务端原版规则。 */
    public void startTickTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                currentTick++;
                for (Map.Entry<UUID, MobState> e : mobRegistry.snapshot().entrySet()) {
                    LivingEntity entity = findEntity(e.getKey());
                    if (entity == null || !entity.isValid()) {
                        unregisterMob(e.getKey());
                        continue;
                    }
                    // 范围技能降频：每 20 tick（1 秒）检测一次，降低高频扫描开销
                    if (currentTick % 20 == 0) {
                        rangeSkillService.tick(entity, e.getValue(), currentTick, mobFactory);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 1L);
    }

    /** 关服时只释放内存引用；实体状态已在每次变化时同步到 PDC。 */
    public void shutdown() {
        mobRegistry.clear();
    }

    /**
     * 怪物死亡时触发 DEATH（亡语）技能。
     *
     * @param collectTo 非 null 时产出掉落类技能改为收集到此列表（聚合掉落事件用），否则直接掉落
     */
    public void onMobDeath(EntityDeathEvent event, LivingEntity entity, MobState mobState,
                           List<ItemStack> collectTo) {
        deathSkillService.trigger(event, entity, mobState, collectTo, currentTick, mobFactory);
    }

    /** 清除指定位置半径内的炒鸡怪，返回清除数量。 */
    public int clearMobsInRadius(Location center, double radius) {
        if (center == null || center.getWorld() == null || radius <= 0) return 0;
        double radiusSq = radius * radius;
        int count = 0;
        for (UUID uuid : mobRegistry.idsSnapshot()) {
            LivingEntity entity = findEntity(uuid);
            MobState state = mobRegistry.get(uuid);
            if (entity == null || !entity.isValid() || state == null) {
                unregisterMob(uuid);
                continue;
            }
            if (!entity.getWorld().equals(center.getWorld())) continue;
            if (center.distanceSquared(entity.getLocation()) > radiusSq) continue;
            unequipAndUnregister(entity, state, SkillService.UnequipReason.ADMIN_REMOVE);
            entity.remove();
            count++;
        }
        return count;
    }

    /**
     * 按 UUID 取实体。必须用 {@link org.bukkit.Server#getEntity(UUID)}，禁止每 tick 全服遍历生物（会随实体数爆炸）。
     */
    private LivingEntity findEntity(UUID uuid) {
        if (uuid == null) return null;
        Entity e = plugin.getServer().getEntity(uuid);
        if (!(e instanceof LivingEntity le)) return null;
        return le.isValid() ? le : null;
    }

}
