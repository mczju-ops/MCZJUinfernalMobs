package com.infernalmobs.service;

import com.infernalmobs.affix.Affix;
import com.infernalmobs.api.InfernalMobHandle;
import com.infernalmobs.api.event.affix.InfernalAffixAttemptEvent;
import com.infernalmobs.api.event.affix.triggered.InfernalMob1upEvent;
import com.infernalmobs.config.ConfigLoader;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.factory.MobFactory;
import com.infernalmobs.model.MobState;
import com.infernalmobs.model.StatMap;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import com.infernalmobs.skill.impl.*;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;
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
    private final ConfigLoader config;
    private final MobRuntimeRegistry mobRegistry = new MobRuntimeRegistry();
    private final MobStatService mobStatService = new MobStatService();
    private final SpecialDamageService specialDamageService;
    private MobFactory mobFactory;
    private SkillService skillService;

    public CombatService(JavaPlugin plugin, ConfigLoader config) {
        this.plugin = plugin;
        this.config = config;
        this.specialDamageService = new SpecialDamageService(plugin);
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
        if (event.getCause() == EntityDamageEvent.DamageCause.THORNS) return;

        double damageBonus = mobState.getStatMap().get(StatMap.DAMAGE_BONUS);
        if (damageBonus > 0) {
            event.setDamage(DamageModifier.BASE, event.getDamage(DamageModifier.BASE) + damageBonus);
        }
        for (Affix affix : mobState.getProfile().getAffixes()) {
            if (affix.getSkill() instanceof RangeSpearSkill spear
                    && spear.handleMeleeHit(event, damager, victim)) {
                break;
            }
        }
        if (event.isCancelled()) return;
        triggerActiveSkills(event, damager, victim, mobState);
        triggerDualSkills(damager, victim, mobState);
    }

    /**
     * 怪物受到任意伤害时，检测 1up 等技能。
     */
    public void onMobDamaged(EntityDamageEvent event, LivingEntity victim) {
        MobState state = mobRegistry.get(victim.getUniqueId());
        if (state == null) return;

        for (Affix affix : state.getProfile().getAffixes()) {
            if (!"1up".equals(affix.getSkillId())) continue;
            if (state.hasUsedOneTime("1up")) continue;

            SkillConfig sc = config.getSkillConfig("1up");
            if (sc == null) continue;

            double threshold = sc.getDouble("hp-threshold", 8);
            double healthAfter = victim.getHealth() - event.getFinalDamage();
            if (healthAfter > threshold) continue;   // 还在阈值以上，不触发
            if (healthAfter <= 0) continue;          // 致命一击，不拦截，让怪直接死亡

            if (!(affix.getSkill() instanceof Stat1upSkill skill)) continue;
            double recoveryAmount = skill.calculateRecoveryAmount(victim, state);
            if (!state.useOneTimeIfNotUsed("1up")) continue;

            // 1up 真正触发事件：外部可取消本次保命
            LivingEntity damager = event instanceof EntityDamageByEntityEvent e2 && e2.getDamager() instanceof LivingEntity le ? le : null;
            InfernalMobHandle handle = new InfernalMobHandle(victim, state.getProfile().getLevel(), state.getProfile().getAffixIds(), state.getSuppressedAffixes());
            InfernalMob1upEvent e = new InfernalMob1upEvent(victim, damager, handle, state.getProfile().getLevel(), recoveryAmount);
            plugin.getServer().getPluginManager().callEvent(e);
            if (e.isCancelled()) break;

            event.setDamage(0.0);
            skill.trigger(victim, sc, state, e.getRecoveryAmount());
            break;
        }
    }

    /**
     * 玩家攻击怪物时，触发 PASSIVE 与 DUAL 技能。
     */
    public void onPlayerAttackMob(EntityDamageByEntityEvent event, LivingEntity victim, Player damager, MobState mobState) {
        for (Affix affix : mobState.getProfile().getAffixes()) {
            if (affix.getSkill().getType() != SkillType.PASSIVE && affix.getSkill().getType() != SkillType.DUAL) continue;
            SkillConfig sc = config.getSkillConfig(affix.getSkillId());
            if (sc == null) continue;
            int cooldownTicks = sc.getInt("cooldown-ticks", affix.getSkill().getType() == SkillType.DUAL ? 60 : 0);
            if (cooldownTicks > 0 && mobState.isOnCooldown(affix.getSkillId(), currentTick)) continue;
            SkillContext ctx = new SkillContext(plugin, victim, mobState);
            ctx.setTargetPlayer(damager);
            ctx.setTriggerEvent(event);
            ctx.setCurrentTick(currentTick);
            if (mobFactory != null) ctx.setMobFactory(mobFactory);
            if (!fireAffixAttemptEvent(affix, ctx, victim, damager, mobState)) continue;
            affix.getSkill().onTrigger(ctx, sc);
            if (ctx.isTriggered()) ctx.commitCooldown(affix.getSkillId(), cooldownTicks);
        }
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
                        tickRangeSkills(entity, e.getValue());
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 1L);
    }

    /** 关服时只释放内存引用；实体状态已在每次变化时同步到 PDC。 */
    public void shutdown() {
        mobRegistry.clear();
    }

    /** 范围技能：玩家在范围内时按概率触发 */
    private void tickRangeSkills(LivingEntity entity, MobState state) {
        for (Affix affix : state.getProfile().getAffixes()) {
            if (affix.getSkill().getType() != SkillType.RANGE) continue;
            SkillConfig sc = config.getSkillConfig(affix.getSkillId());
            if (sc == null) continue;

            double range = sc.getDouble("range", 8);
            Player target = findNearestPlayer(entity, range);
            if (target == null) continue;

            int cooldown = sc.getInt("cooldown-ticks", 100);
            if (cooldown > 0 && state.isOnCooldown(affix.getSkillId(), currentTick)) continue;

            // ghastly 与 necromancer 共享投射物冷却，错开释放
            if ("ghastly".equals(affix.getSkillId()) || "necromancer".equals(affix.getSkillId())) {
                long lastProj = state.getBuff(RangeNecromancerSkill.PROJECTILE_BUFF);
                if (lastProj > 0 && currentTick - lastProj < 40) continue;
            }

            SkillContext ctx = new SkillContext(plugin, entity, state);
            ctx.setTargetPlayer(target);
            ctx.setCurrentTick(currentTick);
            if (mobFactory != null) ctx.setMobFactory(mobFactory);
            if (!fireAffixAttemptEvent(affix, ctx, entity, target, state)) continue;

            double chance = sc.getDouble("chance", 0.02);
            if (Math.random() >= chance) continue;

            affix.getSkill().onTrigger(ctx, sc);
            if (ctx.isTriggered()) {
                ctx.commitCooldown(affix.getSkillId(), cooldown);
                if ("ghastly".equals(affix.getSkillId()) || "necromancer".equals(affix.getSkillId())) {
                    state.setBuff(RangeNecromancerSkill.PROJECTILE_BUFF, currentTick);
                }
            }
        }
    }

    /**
     * 怪物对玩家造成伤害时触发 ACTIVE 技能。
     */
    private void triggerActiveSkills(EntityDamageByEntityEvent event, LivingEntity damager,
                                     Player victim, MobState state) {
        for (Affix affix : state.getProfile().getAffixes()) {
            if (affix.getSkill().getType() != SkillType.ACTIVE) continue;
            SkillConfig sc = config.getSkillConfig(affix.getSkillId());
            if (sc == null) continue;
            int cooldown = sc.getInt("cooldown-ticks", 100);
            if (cooldown > 0 && state.isOnCooldown(affix.getSkillId(), currentTick)) continue;
            SkillContext ctx = new SkillContext(plugin, damager, state);
            ctx.setTargetPlayer(victim);
            ctx.setTriggerEvent(event);
            ctx.setCurrentTick(currentTick);
            if (mobFactory != null) ctx.setMobFactory(mobFactory);
            if (!fireAffixAttemptEvent(affix, ctx, damager, victim, state)) continue;
            affix.getSkill().onTrigger(ctx, sc);
            if (ctx.isTriggered()) ctx.commitCooldown(affix.getSkillId(), cooldown);
        }
    }

    /** DUAL 技能：怪物攻击玩家时触发 */
    private void triggerDualSkills(LivingEntity damager, Player victim, MobState state) {
        for (Affix affix : state.getProfile().getAffixes()) {
            if (affix.getSkill().getType() != SkillType.DUAL) continue;
            SkillConfig sc = config.getSkillConfig(affix.getSkillId());
            if (sc == null) continue;
            int cooldown = sc.getInt("cooldown-ticks", 60);
            if (cooldown > 0 && state.isOnCooldown(affix.getSkillId(), currentTick)) continue;
            SkillContext ctx = new SkillContext(plugin, damager, state);
            ctx.setTargetPlayer(victim);
            ctx.setCurrentTick(currentTick);
            if (mobFactory != null) ctx.setMobFactory(mobFactory);
            if (!fireAffixAttemptEvent(affix, ctx, damager, victim, state)) continue;
            affix.getSkill().onTrigger(ctx, sc);
            if (ctx.isTriggered()) ctx.commitCooldown(affix.getSkillId(), cooldown);
        }
    }

    /**
     * 怪物死亡时触发 DEATH（亡语）技能。
     *
     * @param collectTo 非 null 时产出掉落类技能改为收集到此列表（聚合掉落事件用），否则直接掉落
     */
    public void onMobDeath(EntityDeathEvent event, LivingEntity entity, MobState mobState,
                           List<ItemStack> collectTo) {
        Player killer = entity.getKiller();
        for (Affix affix : mobState.getProfile().getAffixes()) {
            if (affix.getSkill().getType() != SkillType.DEATH) continue;
            SkillConfig sc = config.getSkillConfig(affix.getSkillId());
            if (sc == null) continue;
            SkillContext ctx = new SkillContext(plugin, entity, mobState);
            ctx.setTargetPlayer(killer);
            ctx.setTriggerEvent(event);
            ctx.setCurrentTick(currentTick);
            if (mobFactory != null) ctx.setMobFactory(mobFactory);
            ctx.setCollectTo(collectTo);
            if (!fireAffixAttemptEvent(affix, ctx, entity, killer, mobState)) continue;
            affix.getSkill().onTrigger(ctx, sc);
        }
    }

    /**
     * 在非 STAT 词条进入技能条件与概率判定前广播 {@link InfernalAffixAttemptEvent}。
     * 调用本方法前应先完成冷却、目标等内部资格检查；取消后不继续判定，也不产生新冷却。
     */
    private boolean fireAffixAttemptEvent(Affix affix, SkillContext ctx,
                                          LivingEntity mob, LivingEntity target, MobState state) {
        if (affix.getSkill().getType() == SkillType.STAT) return true;
        if (plugin == null) return true;
        InfernalMobHandle handle = new InfernalMobHandle(mob,
                state.getProfile().getLevel(), state.getProfile().getAffixIds(),
                state.getSuppressedAffixes());
        ctx.setHandle(handle);
        InfernalAffixAttemptEvent event = new InfernalAffixAttemptEvent(
                affix.getSkillId(), affix.getSkill().getType(), mob, target, handle,
                state.getProfile().getLevel());
        plugin.getServer().getPluginManager().callEvent(event);
        return !event.isCancelled();
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


    private Player findNearestPlayer(LivingEntity entity, double range) {
        Player nearest = null;
        double minSq = range * range;
        for (Player p : entity.getWorld().getPlayers()) {
            if (!p.isOnline() || p.getGameMode() == GameMode.SPECTATOR) continue;
            double dSq = p.getLocation().distanceSquared(entity.getLocation());
            if (dSq < minSq) {
                minSq = dSq;
                nearest = p;
            }
        }
        return nearest;
    }
}
