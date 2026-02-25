package com.infernalmobs.service;

import com.infernalmobs.affix.Affix;
import com.infernalmobs.config.ConfigLoader;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.model.MobState;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
    private final Map<UUID, MobState> mobStates = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastActiveTick = new ConcurrentHashMap<>();
    private com.infernalmobs.factory.MobFactory mobFactory;
    private BukkitRunnable cleanupTask;

    public CombatService(JavaPlugin plugin, ConfigLoader config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void registerMob(UUID entityUuid, MobState state) {
        mobStates.put(entityUuid, state);
        lastActiveTick.put(entityUuid, currentTick);
    }

    public void unregisterMob(UUID entityUuid) {
        mobStates.remove(entityUuid);
        lastActiveTick.remove(entityUuid);
    }

    public MobState getMobState(UUID entityUuid) {
        return mobStates.get(entityUuid);
    }

    /** 获取当前追踪的炒鸡怪数量 */
    public int getTrackedCount() {
        return mobStates.size();
    }

    /** 获取所有追踪中的炒鸡怪（UUID -> MobState），用于外部索引 */
    public Map<UUID, MobState> getTrackedMobs() {
        return new HashMap<>(mobStates);
    }

    public void setMobFactory(com.infernalmobs.factory.MobFactory factory) {
        this.mobFactory = factory;
    }

    /**
     * 怪物生成后调用，应用 StatMap 中的数值到实体。
     * 血量与等级成正比：N 级 = 原血量 × N，再叠加技能加成。
     */
    public void applyStats(LivingEntity entity, MobState mobState) {
        var attr = entity.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) {
            double base = attr.getBaseValue();
            int level = Math.max(1, mobState.getProfile().getLevel());
            base *= level;  // 等级倍数
            double hpBonus = mobState.getStatMap().get(com.infernalmobs.model.StatMap.HP_BONUS);
            base += hpBonus;
            attr.setBaseValue(base);
            entity.setHealth(entity.getMaxHealth());
        }

        double speedBonus = mobState.getStatMap().get(com.infernalmobs.model.StatMap.SPEED_MULTIPLIER);
        if (speedBonus != 0 && entity.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
            double base = entity.getAttribute(Attribute.MOVEMENT_SPEED).getBaseValue();
            entity.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(base * (1 + speedBonus));
        }
    }

    /**
     * 怪物攻击玩家时：应用伤害加成，并触发 ACTIVE 与 DUAL 技能。
     */
    public void onMobAttack(EntityDamageByEntityEvent event, LivingEntity damager, Player victim, MobState mobState) {
        double damageBonus = mobState.getStatMap().get(com.infernalmobs.model.StatMap.DAMAGE_BONUS);
        if (damageBonus > 0) {
            event.setDamage(event.getDamage() + damageBonus);
        }
        triggerActiveSkills(damager, victim, mobState);
        triggerDualSkills(damager, victim, mobState);
    }

    /**
     * 怪物受到任意伤害时，检测 1up 等技能。
     */
    public void onMobDamaged(EntityDamageEvent event, LivingEntity victim) {
        MobState state = mobStates.get(victim.getUniqueId());
        if (state == null) return;

        for (Affix affix : state.getProfile().getAffixes()) {
            if (!"1up".equals(affix.getSkillId())) continue;
            if (state.hasUsedOneTime("1up")) continue;

            com.infernalmobs.config.SkillConfig sc = config.getSkillConfig("1up");
            if (sc == null) continue;

            double threshold = sc.getDouble("hp-threshold", 8);
            double healthAfter = victim.getHealth() - event.getFinalDamage();
            if (healthAfter > threshold) continue;

            if (!state.useOneTimeIfNotUsed("1up")) continue;

            event.setDamage(0);
            if (affix.getSkill() instanceof com.infernalmobs.skill.impl.Stat1upSkill skill) {
                skill.trigger(victim, sc);
            }
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
            if (cooldownTicks > 0) {
                if (mobState.isOnCooldown(affix.getSkillId(), currentTick)) continue;
                mobState.setCooldown(affix.getSkillId(), currentTick + cooldownTicks);
            }
            SkillContext ctx = new SkillContext(plugin, victim, mobState);
            ctx.setTargetPlayer(damager);
            ctx.setTriggerEvent(event);
            if (mobFactory != null) ctx.setMobFactory(mobFactory);
            affix.getSkill().onTrigger(ctx, sc);

            // lifesteal: 受击后设置回血 buff
            if ("lifesteal".equals(affix.getSkillId()) && affix.getSkill() instanceof com.infernalmobs.skill.impl.PassiveLifestealSkill ls) {
                int duration = sc.getInt("duration-ticks", 80);  // 4s
                ls.setLifestealBuff(ctx, currentTick + duration);
            }
        }
    }

    /**
     * 处理火球命中时的自定义伤害。（使用 ProjectileHitEvent，Bukkit 1.21 无 FireballHitEvent）
     */
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!event.getEntity().hasMetadata("infernalmobs_damage")) return;
        List<MetadataValue> meta = event.getEntity().getMetadata("infernalmobs_damage");
        if (meta.isEmpty()) return;
        double damage = meta.get(0).asDouble();
        int fireTicks = 0;
        if (event.getEntity().hasMetadata("infernalmobs_fire_ticks")) {
            List<MetadataValue> fireMeta = event.getEntity().getMetadata("infernalmobs_fire_ticks");
            if (!fireMeta.isEmpty()) fireTicks = fireMeta.get(0).asInt();
        }
        if (event.getHitEntity() instanceof LivingEntity hit) {
            hit.damage(damage, event.getEntity());
            if (fireTicks > 0) hit.setFireTicks(Math.max(hit.getFireTicks(), fireTicks));
        }
        event.setCancelled(true);
    }

    /**
     * 烟花爆炸伤害归因到释放技能的怪物，使死亡信息等显示正确来源。
     */
    public void handleFireworkDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Firework fw) || !fw.hasMetadata("infernalmobs_firework_source")) return;
        List<MetadataValue> meta = fw.getMetadata("infernalmobs_firework_source");
        if (meta.isEmpty()) return;
        Object val = meta.get(0).value();
        if (!(val instanceof UUID mobUuid)) return;
        LivingEntity mob = findEntity(mobUuid);
        if (mob == null || !mob.isValid()) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        event.setCancelled(true);
        victim.damage(event.getDamage(), mob);
    }

    private volatile long currentTick = 0;

    /**
     * 启动 tick 任务（用于 ACTIVE 技能 CD 计数等），以及可选的定期不活跃清理任务。
     */
    public void startTickTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                currentTick++;
                for (Map.Entry<UUID, MobState> e : mobStates.entrySet()) {
                    LivingEntity entity = findEntity(e.getKey());
                    if (entity == null || !entity.isValid()) {
                        mobStates.remove(e.getKey());
                        lastActiveTick.remove(e.getKey());
                        continue;
                    }
                    tickRangeSkills(entity, e.getValue());
                    tickLifesteal(entity, e.getValue());
                    tickSprint(entity, e.getValue());
                }
            }
        }.runTaskTimer(plugin, 20L, 1L);

        var reg = config.getMobRegistryConfig();
        if (reg != null && reg.cleanupEnabled() && cleanupTask == null) {
            long intervalTicks = Math.max(20, reg.cleanupIntervalSeconds() * 20L);
            cleanupTask = new BukkitRunnable() {
                @Override
                public void run() {
                    runCleanupCycle(reg.inactiveRadius(), reg.inactiveSeconds());
                }
            };
            cleanupTask.runTaskTimer(plugin, intervalTicks, intervalTicks);
        }
    }

    /** 清理周期：移除无效实体；无玩家附近则更新 lastActiveTick，超过 inactiveSeconds 无玩家则清除。 */
    private void runCleanupCycle(double inactiveRadius, int inactiveSeconds) {
        long inactiveTicks = inactiveSeconds * 20L;
        for (UUID uuid : new ArrayList<>(mobStates.keySet())) {
            LivingEntity entity = findEntity(uuid);
            if (entity == null || !entity.isValid()) {
                unregisterMob(uuid);
                continue;
            }
            Player nearby = findNearestPlayer(entity, inactiveRadius);
            if (nearby != null) {
                lastActiveTick.put(uuid, currentTick);
                continue;
            }
            long last = lastActiveTick.getOrDefault(uuid, currentTick);
            if (currentTick - last >= inactiveTicks) {
                entity.remove();
                unregisterMob(uuid);
            }
        }
    }

    /** 关服时调用：取消清理任务，并根据配置决定是否清除所有炒鸡怪 */
    public void cleanupOnShutdown() {
        if (cleanupTask != null) cleanupTask.cancel();
        var reg = config.getMobRegistryConfig();
        if (reg == null || !reg.killOnShutdown()) return;
        for (UUID uuid : new ArrayList<>(mobStates.keySet())) {
            LivingEntity entity = findEntity(uuid);
            if (entity != null && entity.isValid()) entity.remove();
            mobStates.remove(uuid);
            lastActiveTick.remove(uuid);
        }
    }

    /** lifesteal: 4s 内每秒回 1 血 */
    private void tickLifesteal(LivingEntity entity, MobState state) {
        long until = state.getBuff(com.infernalmobs.skill.impl.PassiveLifestealSkill.BUFF_KEY);
        if (until == 0 || currentTick >= until) return;

        if (currentTick % 20 != 0) return;  // 每秒一次

        var sc = config.getSkillConfig("lifesteal");
        double amount = sc != null ? sc.getDouble("heal-per-second", 1) : 1;
        var attr = entity.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        entity.setHealth(Math.min(attr.getValue(), entity.getHealth() + amount));
    }

    /** sprint: 附近有玩家时施加速度效果，仅在效果快消失时重新施加以降低开销 */
    private void tickSprint(LivingEntity entity, MobState state) {
        if (state.getProfile().getAffixes().stream().noneMatch(a -> "sprint".equals(a.getSkillId()))) return;

        long until = state.getBuff(com.infernalmobs.skill.impl.StatSprintSkill.BUFF_KEY);
        if (until > 0 && currentTick < until - 5) return;  // 效果仍有效，留 5 tick 余量再续

        var sc = config.getSkillConfig("sprint");
        if (sc == null) return;

        double range = sc.getDouble("range", 16);
        Player p = findNearestPlayer(entity, range);
        if (p == null) return;

        int amplifier = sc.getInt("amplifier", 1);
        int durationTicks = 40;
        entity.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.SPEED, durationTicks, amplifier, false, true));
        state.setBuff(com.infernalmobs.skill.impl.StatSprintSkill.BUFF_KEY, currentTick + durationTicks);
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
            if (state.isOnCooldown(affix.getSkillId(), currentTick)) continue;

            // ghastly 与 necromancer 共享投射物冷却，错开释放
            if ("ghastly".equals(affix.getSkillId()) || "necromancer".equals(affix.getSkillId())) {
                long lastProj = state.getBuff(com.infernalmobs.skill.impl.RangeNecromancerSkill.PROJECTILE_BUFF);
                if (lastProj > 0 && currentTick - lastProj < 40) continue;
            }

            double chance = sc.getDouble("chance", 0.02);
            if (Math.random() >= chance) continue;

            state.setCooldown(affix.getSkillId(), currentTick + cooldown);
            if ("ghastly".equals(affix.getSkillId()) || "necromancer".equals(affix.getSkillId())) {
                state.setBuff(com.infernalmobs.skill.impl.RangeNecromancerSkill.PROJECTILE_BUFF, currentTick);
            }
            SkillContext ctx = new SkillContext(plugin, entity, state);
            ctx.setTargetPlayer(target);
            if (mobFactory != null) ctx.setMobFactory(mobFactory);
            affix.getSkill().onTrigger(ctx, sc);
        }
    }

    /**
     * 怪物对玩家造成伤害时触发 ACTIVE 技能。
     */
    private void triggerActiveSkills(LivingEntity damager, Player victim, MobState state) {
        for (Affix affix : state.getProfile().getAffixes()) {
            if (affix.getSkill().getType() != SkillType.ACTIVE) continue;
            SkillConfig sc = config.getSkillConfig(affix.getSkillId());
            if (sc == null) continue;
            int cooldown = sc.getInt("cooldown-ticks", 100);
            if (state.isOnCooldown(affix.getSkillId(), currentTick)) continue;
            state.setCooldown(affix.getSkillId(), currentTick + cooldown);
            SkillContext ctx = new SkillContext(plugin, damager, state);
            ctx.setTargetPlayer(victim);
            if (mobFactory != null) ctx.setMobFactory(mobFactory);
            affix.getSkill().onTrigger(ctx, sc);
        }
    }

    /** DUAL 技能：怪物攻击玩家时触发 */
    private void triggerDualSkills(LivingEntity damager, Player victim, MobState state) {
        for (Affix affix : state.getProfile().getAffixes()) {
            if (affix.getSkill().getType() != SkillType.DUAL) continue;
            SkillConfig sc = config.getSkillConfig(affix.getSkillId());
            if (sc == null) continue;
            int cooldown = sc.getInt("cooldown-ticks", 60);
            if (state.isOnCooldown(affix.getSkillId(), currentTick)) continue;
            state.setCooldown(affix.getSkillId(), currentTick + cooldown);
            SkillContext ctx = new SkillContext(plugin, damager, state);
            ctx.setTargetPlayer(victim);
            if (mobFactory != null) ctx.setMobFactory(mobFactory);
            affix.getSkill().onTrigger(ctx, sc);
        }
    }

    /**
     * 怪物死亡时触发 DEATH（亡语）技能。
     */
    public void onMobDeath(EntityDeathEvent event, LivingEntity entity, MobState mobState) {
        Player killer = entity.getKiller();
        for (Affix affix : mobState.getProfile().getAffixes()) {
            if (affix.getSkill().getType() != SkillType.DEATH) continue;
            SkillConfig sc = config.getSkillConfig(affix.getSkillId());
            if (sc == null) continue;
            SkillContext ctx = new SkillContext(plugin, entity, mobState);
            ctx.setTargetPlayer(killer);
            ctx.setTriggerEvent(event);
            if (mobFactory != null) ctx.setMobFactory(mobFactory);
            affix.getSkill().onTrigger(ctx, sc);
        }
    }

    /** 清除指定位置半径内的炒鸡怪，返回清除数量。 */
    public int clearMobsInRadius(Location center, double radius) {
        if (center == null || center.getWorld() == null || radius <= 0) return 0;
        double radiusSq = radius * radius;
        int count = 0;
        for (UUID uuid : new ArrayList<>(mobStates.keySet())) {
            LivingEntity entity = findEntity(uuid);
            if (entity == null || !entity.isValid()) {
                unregisterMob(uuid);
                continue;
            }
            if (!entity.getWorld().equals(center.getWorld())) continue;
            if (center.distanceSquared(entity.getLocation()) > radiusSq) continue;
            entity.remove();
            unregisterMob(uuid);
            count++;
        }
        return count;
    }

    private LivingEntity findEntity(UUID uuid) {
        for (org.bukkit.World w : plugin.getServer().getWorlds()) {
            for (org.bukkit.entity.LivingEntity le : w.getLivingEntities()) {
                if (le.getUniqueId().equals(uuid)) return le;
            }
        }
        return null;
    }


    private Player findNearestPlayer(LivingEntity entity, double range) {
        Player nearest = null;
        double minSq = range * range;
        for (Player p : entity.getWorld().getPlayers()) {
            if (!p.isOnline() || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            double dSq = p.getLocation().distanceSquared(entity.getLocation());
            if (dSq < minSq) {
                minSq = dSq;
                nearest = p;
            }
        }
        return nearest;
    }
}
