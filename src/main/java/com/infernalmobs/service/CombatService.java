package com.infernalmobs.service;

import com.infernalmobs.affix.Affix;
import com.infernalmobs.config.ConfigLoader;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.model.MobState;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataType;
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
    /** 炒鸡怪捡起的物品（用于 replace-vanilla-drops 时恢复到死亡掉落） */
    private final Map<UUID, List<ItemStack>> pickedUpItems = new ConcurrentHashMap<>();
    private com.infernalmobs.factory.MobFactory mobFactory;
    private MagicKingArmorService magicKingArmorService;
    private BukkitRunnable cleanupTask;

    public CombatService(JavaPlugin plugin, ConfigLoader config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void setMagicKingArmorService(MagicKingArmorService service) {
        this.magicKingArmorService = service;
    }

    public void registerMob(UUID entityUuid, MobState state) {
        mobStates.put(entityUuid, state);
        lastActiveTick.put(entityUuid, currentTick);
    }

    public void unregisterMob(UUID entityUuid) {
        mobStates.remove(entityUuid);
        lastActiveTick.remove(entityUuid);
        pickedUpItems.remove(entityUuid);
    }

    public MobState getMobState(UUID entityUuid) {
        return mobStates.get(entityUuid);
    }

    /** 记录炒鸡怪捡起的物品（克隆存储，避免后续元数据/堆叠变化影响）。 */
    public void recordPickedUpItem(UUID entityUuid, ItemStack item) {
        if (entityUuid == null || item == null || item.getType().isAir() || item.getAmount() <= 0) return;
        pickedUpItems.computeIfAbsent(entityUuid, k -> new ArrayList<>()).add(item.clone());
    }

    /** 消费并返回该炒鸡怪捡起的所有物品。 */
    public List<ItemStack> consumePickedUpItems(UUID entityUuid) {
        if (entityUuid == null) return List.of();
        List<ItemStack> items = pickedUpItems.remove(entityUuid);
        if (items == null || items.isEmpty()) return List.of();
        return new ArrayList<>(items);
    }

    /**
     * replace-vanilla-drops 时补回玩家被捡走的物品：仅当前仍穿/拿在身上的栏位，
     * 且能从拾取记录中匹配到的才掉落；曾捡起后又扔掉的条目不会进入掉落（记录会清空）。
     */
    public List<ItemStack> releasePickedUpItemsStillEquipped(LivingEntity entity) {
        UUID uuid = entity.getUniqueId();
        EntityEquipment eq = entity.getEquipment();
        List<ItemStack> drops = new ArrayList<>();
        if (eq != null) {
            for (EquipmentSlot slot : PICKUP_TRACKED_EQUIPMENT_SLOTS) {
                ItemStack inSlot = getItemInEquipmentSlot(eq, slot);
                if (inSlot == null || inSlot.getType().isAir() || inSlot.getAmount() <= 0) continue;
                ItemStack matched = consumeMatchedPickedUpItem(uuid, inSlot);
                if (matched != null && !matched.getType().isAir() && matched.getAmount() > 0) {
                    drops.add(matched);
                }
            }
        }
        pickedUpItems.remove(uuid);
        return drops;
    }

    private static final EquipmentSlot[] PICKUP_TRACKED_EQUIPMENT_SLOTS = {
            EquipmentSlot.HAND, EquipmentSlot.OFF_HAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private static ItemStack getItemInEquipmentSlot(EntityEquipment eq, EquipmentSlot slot) {
        return switch (slot) {
            case HAND -> eq.getItemInMainHand();
            case OFF_HAND -> eq.getItemInOffHand();
            case HEAD -> eq.getHelmet();
            case CHEST -> eq.getChestplate();
            case LEGS -> eq.getLeggings();
            case FEET -> eq.getBoots();
            default -> null;
        };
    }

    /**
     * 怪物将物品丢到地上时调用：从拾取记录中按相似物品扣减数量（与 {@link #consumeMatchedPickedUpItem} 同一套匹配规则）。
     */
    public void unrecordDroppedPickedUpItem(UUID entityUuid, ItemStack dropped) {
        if (entityUuid == null || dropped == null || dropped.getType().isAir() || dropped.getAmount() <= 0) return;
        removeMatchedPickedUpAmount(entityUuid, dropped);
    }

    /**
     * 按“相似物品”从拾取记录中消费数量，并返回可掉落的克隆物品（amount 为实际可消费数量）。
     * 用于变身时仅掉落后天拾取（非自带）的装备。
     */
    public ItemStack consumeMatchedPickedUpItem(UUID entityUuid, ItemStack equipped) {
        if (entityUuid == null || equipped == null || equipped.getType().isAir() || equipped.getAmount() <= 0) return null;
        int consumed = removeMatchedPickedUpAmount(entityUuid, equipped);
        if (consumed <= 0) return null;
        ItemStack out = equipped.clone();
        out.setAmount(consumed);
        return out;
    }

    /** 从拾取记录中扣减与 reference 相似的堆叠数量，返回实际扣减数量。 */
    private int removeMatchedPickedUpAmount(UUID entityUuid, ItemStack reference) {
        List<ItemStack> items = pickedUpItems.get(entityUuid);
        if (items == null || items.isEmpty()) return 0;

        int needed = reference.getAmount();
        int consumed = 0;
        for (int i = 0; i < items.size() && needed > 0; i++) {
            ItemStack tracked = items.get(i);
            if (tracked == null || tracked.getType().isAir() || tracked.getAmount() <= 0) continue;
            if (!tracked.isSimilar(reference)) continue;

            int use = Math.min(needed, tracked.getAmount());
            needed -= use;
            consumed += use;

            int left = tracked.getAmount() - use;
            if (left <= 0) {
                items.set(i, null);
            } else {
                tracked.setAmount(left);
            }
        }
        items.removeIf(Objects::isNull);
        if (items.isEmpty()) pickedUpItems.remove(entityUuid);
        return consumed;
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
     * 僵尸系炒鸡怪回血上限：按「原版基础 20 血 × 炒鸡等级」封顶。
     * 头领僵尸等会抬高 MAX_HEALTH，若回血可回到 attr 满血会远超普通 20× 等级；再生/瞬间治疗等统一按此假装仍是普通僵尸血量池。
     *
     * @return 非僵尸系返回 {@link Double#POSITIVE_INFINITY} 表示不额外限制
     */
    public static double zombieFamilyHealCap(LivingEntity entity, MobState state) {
        if (entity == null || state == null) return Double.POSITIVE_INFINITY;
        int lv = Math.max(1, state.getProfile().getLevel());
        EntityType t = entity.getType();
        return switch (t) {
            case ZOMBIE, ZOMBIE_VILLAGER, HUSK, DROWNED, BOGGED -> 20.0 * lv;
            default -> Double.POSITIVE_INFINITY;
        };
    }

    private static double healCeiling(LivingEntity entity, MobState state) {
        var attr = entity.getAttribute(Attribute.MAX_HEALTH);
        double attrMax = attr != null ? attr.getValue() : entity.getMaxHealth();
        double paperMax = entity.getMaxHealth();
        double zCap = zombieFamilyHealCap(entity, state);
        if (!Double.isInfinite(zCap)) {
            return Math.min(Math.min(attrMax, zCap), paperMax);
        }
        return Math.min(attrMax, paperMax);
    }

    /**
     * 怪物生成后调用，应用 StatMap 中的数值到实体。
     * 最大血量与当前血量均乘以等级倍数，保留原始血量比例：
     *   改造后最大血量 = 原最大血量 × 等级
     *   改造后当前血量 = 原当前血量 × 等级
     * 例：原 20/80 + Lv10 → 200/800（不会强制回满血）。
     */
    public void applyStats(LivingEntity entity, MobState mobState) {
        var attr = entity.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) {
            int level = Math.max(1, mobState.getProfile().getLevel());
            double baseMax = attr.getBaseValue();
            double baseCurrent = entity.getHealth();

            double newMax = baseMax * level
                    + mobState.getStatMap().get(com.infernalmobs.model.StatMap.HP_BONUS);
            attr.setBaseValue(newMax);

            // 当前血量等比缩放。
            // Paper 对 setHealth() 的实际上限是 entity.getMaxHealth()（属性值被 MC 原生截断为 1024），
            // 因此必须以 getMaxHealth() 为上限，否则超 1024 会抛 IllegalArgumentException。
            double effectiveCap = entity.getMaxHealth();
            double newCurrent = baseCurrent * level;
            entity.setHealth(Math.max(0.1, Math.min(effectiveCap, newCurrent)));
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
            if (magicKingArmorService != null && event instanceof EntityDamageByEntityEvent edbe) {
                org.bukkit.entity.Entity damager = edbe.getDamager();
                if (damager instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof org.bukkit.entity.Player p) damager = p;
                if (damager instanceof Player p && magicKingArmorService.isWeakened(p, "1up")) threshold = threshold / 2.0;
            }
            double healthAfter = victim.getHealth() - event.getFinalDamage();
            if (healthAfter > threshold) continue;   // 还在阈值以上，不触发
            if (healthAfter <= 0) continue;          // 致命一击，不拦截，让怪直接死亡

            if (!state.useOneTimeIfNotUsed("1up")) continue;

            event.setDamage(0);
            if (affix.getSkill() instanceof com.infernalmobs.skill.impl.Stat1upSkill skill) {
                skill.trigger(victim, sc, state);
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
            ctx.setCurrentTick(currentTick);
            if (mobFactory != null) ctx.setMobFactory(mobFactory);
            if (magicKingArmorService != null) ctx.setWeakened(magicKingArmorService.isWeakened(damager, affix.getSkillId()));
            affix.getSkill().onTrigger(ctx, sc);

            // lifesteal: 受击后设置回血 buff（削弱时 50% 概率不触发）
            if ("lifesteal".equals(affix.getSkillId()) && affix.getSkill() instanceof com.infernalmobs.skill.impl.PassiveLifestealSkill ls) {
                if (ctx.isWeakened() && Math.random() < 0.5) { /* 削弱：50% 不触发 */ }
                else {
                    int duration = sc.getInt("duration-ticks", 80);
                    ls.setLifestealBuff(ctx, currentTick + duration);
                }
            }
        }
    }

    /**
     * 玩家右键使用 morph_controller 时调用：禁用目标炒鸡怪的 morph 词条并刷新头顶名。
     * 返回 true 表示成功禁用（已禁用 / 无该词条 / 非炒鸡怪 → false）。
     */
    public boolean suppressMorphAffix(LivingEntity entity) {
        MobState state = getMobState(entity.getUniqueId());
        if (state == null) return false;
        boolean hasMorph = state.getProfile().getAffixes().stream()
                .anyMatch(a -> "morph".equalsIgnoreCase(a.getSkillId()));
        if (!hasMorph || state.isAffixSuppressed("morph")) return false;
        state.suppressAffix("morph");
        if (mobFactory != null) mobFactory.refreshDisplayName(entity, state);
        return true;
    }

    /**
     * 处理火球命中：命中实体/方块都允许爆炸，仅对直接命中的实体补火。擦肩而过不点燃由火球 setFireTicks(0) 保证。
     */
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!event.getEntity().hasMetadata("infernalmobs_damage")) return;
        int fireTicks = 0;
        if (event.getEntity().hasMetadata("infernalmobs_fire_ticks")) {
            List<MetadataValue> fireMeta = event.getEntity().getMetadata("infernalmobs_fire_ticks");
            if (!fireMeta.isEmpty()) fireTicks = fireMeta.get(0).asInt();
        }
        if (event.getHitEntity() instanceof LivingEntity hit) {
            if (fireTicks > 0) hit.setFireTicks(Math.max(hit.getFireTicks(), fireTicks));
        }
        // 不 cancel，命中实体或方块都按 ExplosionPower 爆炸
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
                        unregisterMob(e.getKey());
                        continue;
                    }
                    // 范围技能降频：每 20 tick（1 秒）检测一次，降低高频扫描开销
                    if (currentTick % 20 == 0) {
                        tickRangeSkills(entity, e.getValue());
                    }
                    tickLifesteal(entity, e.getValue());
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

    private static final String IM_LEVEL = "im_level";

    /** 清理周期：移除无效实体；无玩家附近则更新 lastActiveTick，超过 inactiveSeconds 无玩家则清除；清除孤立 im_level 标签实体。 */
    private void runCleanupCycle(double inactiveRadius, int inactiveSeconds) {
        removeOrphanedImLevelEntities();

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

    /** lifesteal: 4s 内每秒回血 */
    private void tickLifesteal(LivingEntity entity, MobState state) {
        long until = state.getBuff(com.infernalmobs.skill.impl.PassiveLifestealSkill.BUFF_KEY);
        if (until == 0 || currentTick >= until) return;

        if (currentTick % 20 != 0) return;  // 每秒一次

        var sc = config.getSkillConfig("lifesteal");
        double amount = sc != null ? sc.getDouble("heal-per-second", 1) : 1;
        var attr = entity.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        double ceiling = healCeiling(entity, state);
        entity.setHealth(Math.min(ceiling, entity.getHealth() + amount));
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
            ctx.setCurrentTick(currentTick);
            if (mobFactory != null) ctx.setMobFactory(mobFactory);
            if (magicKingArmorService != null) ctx.setWeakened(magicKingArmorService.isWeakened(target, affix.getSkillId()));
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
            ctx.setCurrentTick(currentTick);
            if (mobFactory != null) ctx.setMobFactory(mobFactory);
            if (magicKingArmorService != null) ctx.setWeakened(magicKingArmorService.isWeakened(victim, affix.getSkillId()));
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
            ctx.setCurrentTick(currentTick);
            if (mobFactory != null) ctx.setMobFactory(mobFactory);
            if (magicKingArmorService != null) ctx.setWeakened(magicKingArmorService.isWeakened(victim, affix.getSkillId()));
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
            ctx.setCurrentTick(currentTick);
            if (mobFactory != null) ctx.setMobFactory(mobFactory);
            affix.getSkill().onTrigger(ctx, sc);
        }
    }

    /**
     * 扫描 enabled-worlds 中所有生物：有 im_level 的 PDC 但不是炒鸡怪（未注册）的则移除。
     * 返回移除的实体数量。可由指令 /im cleantags 或定期清理调用。
     */
    public int removeOrphanedImLevelEntities() {
        List<String> worlds = config.getEnabledWorlds();
        if (worlds == null || worlds.isEmpty()) return 0;

        NamespacedKey key = new NamespacedKey(plugin, IM_LEVEL);
        int count = 0;
        for (String name : worlds) {
            org.bukkit.World w = plugin.getServer().getWorld(name);
            if (w == null) continue;
            for (LivingEntity entity : new ArrayList<>(w.getLivingEntities())) {
                if (!entity.isValid() || entity instanceof Player) continue;
                if (!entity.getPersistentDataContainer().has(key, PersistentDataType.INTEGER)) continue;
                if (mobStates.containsKey(entity.getUniqueId())) continue;  // 是炒鸡怪，跳过
                entity.remove();
                count++;
            }
        }
        return count;
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
