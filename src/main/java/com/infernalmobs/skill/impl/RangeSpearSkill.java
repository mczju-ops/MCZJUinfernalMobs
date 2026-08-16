package com.infernalmobs.skill.impl;

import com.infernalmobs.api.event.affix.effect.InfernalMobSpearHitEvent;
import com.infernalmobs.api.event.affix.triggered.InfernalMobSpearEvent;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 蓄力后获得速度效果，以长矛强化追逐玩家。
 * 仅对 enabled-holders 白名单中的怪物生效，不在白名单内则跳过。
 * 蓄力期间主手换矛、粒子环绕，强化追逐时由原版 AI 寻路并拖粒子尾迹；
 * 只有冲锋阶段对锁定玩家造成的真实近战攻击才算 spear 命中。
 */
public class RangeSpearSkill implements Skill {

    private final Map<UUID, ActiveSpear> activeSpears = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return "spear";
    }

    @Override
    public SkillType getType() {
        return SkillType.RANGE;
    }

    @Override
    public void onEquip(SkillContext ctx, SkillConfig config) {}

    @Override
    public void onUnequip(SkillContext ctx) {
        LivingEntity mob = ctx.getEntity();
        if (mob == null) return;
        ActiveSpear active = activeSpears.get(mob.getUniqueId());
        if (active != null) finishSession(active, false);
    }

    @Override
    public void onTrigger(SkillContext ctx, SkillConfig config) {
        LivingEntity mob = ctx.getEntity();
        Player target = ctx.getTargetPlayer();
        if (mob == null || !mob.isValid() || target == null || !target.isOnline()) return;
        if (target.getGameMode() == GameMode.CREATIVE || target.getGameMode() == GameMode.SPECTATOR) return;
        if (activeSpears.containsKey(mob.getUniqueId())) return;

        Set<EntityType> holders = parseEntityTypeSet(config, "enabled-holders");
        if (!holders.isEmpty() && !holders.contains(mob.getType())) return;

        EntityEquipment equip = mob.getEquipment();
        if (equip == null) return;

        Vector horizontalOffset = target.getLocation().toVector().subtract(mob.getLocation().toVector());
        horizontalOffset.setY(0);
        if (horizontalOffset.lengthSquared() < 0.01) return;

        int chargeTicks = Math.max(1, config.getInt("charge-ticks", 48));
        int lungeTicks = Math.max(1, config.getInt("lunge-ticks", 30));
        int speedAmplifier = Math.max(0, config.getInt("lunge-speed-amplifier", 4));
        int sharpnessLevel = Math.max(0, config.getInt("sharpness-level", 5));
        ItemStack spearItem = createSpearItem(config.getString("item", "NETHERITE_SPEAR"), sharpnessLevel);

        InfernalMobSpearEvent event = new InfernalMobSpearEvent(
                mob, target, ctx.getHandle(), ctx.getMobState().getProfile().getLevel(),
                chargeTicks, lungeTicks, speedAmplifier, spearItem);
        if (!ctx.fire(event)) return;

        chargeTicks = Math.max(1, event.getChargeTicks());
        lungeTicks = Math.max(1, event.getLungeTicks());
        speedAmplifier = Math.max(0, event.getLungeSpeedAmplifier());
        if (event.getSpearItem() != null && !event.getSpearItem().getType().isAir()) {
            spearItem = event.getSpearItem();
        }

        ItemStack savedHand = equip.getItemInMainHand();
        float savedHandDropChance = equip.getItemInMainHandDropChance();
        ActiveSpear active = new ActiveSpear(ctx, mob, target, equip, savedHand,
                savedHandDropChance, chargeTicks, lungeTicks, speedAmplifier);
        if (activeSpears.putIfAbsent(mob.getUniqueId(), active) != null) return;

        equip.setItemInMainHand(spearItem);
        equip.setItemInMainHandDropChance(0f);

        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_RAVAGER_STUNNED, 1.0f, 2.0f);

        BukkitRunnable task = new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (!mob.isValid() || mob.isDead() || !target.isOnline() || target.isDead()
                        || target.getWorld() != mob.getWorld()
                        || target.getGameMode() == GameMode.CREATIVE
                        || target.getGameMode() == GameMode.SPECTATOR) {
                    finishSession(active, false);
                    return;
                }

                if (tick < active.chargeTicks) {
                    mob.setVelocity(new Vector(0, mob.getVelocity().getY(), 0));
                    if (tick % 2 == 0) {
                        double angle = tick * 0.4;
                        Location ringLoc = mob.getLocation().add(
                                Math.cos(angle) * 1.2, mob.getHeight() * 0.6, Math.sin(angle) * 1.2);
                        mob.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, ringLoc, 1, 0, 0, 0, 0);
                    }
                    if (tick == active.chargeTicks / 2) {
                        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_RAVAGER_STUNNED, 1.0f, 2.0f);
                    }
                    tick++;
                    return;
                }

                if (active.phase == SpearPhase.CHARGING) {
                    active.previousSpeedEffect = mob.getPotionEffect(PotionEffectType.SPEED);
                    active.spearSpeedEffect = new PotionEffect(
                            PotionEffectType.SPEED, active.lungeTicks, active.speedAmplifier,
                            false, false, true);
                    active.speedEffectApplied = mob.addPotionEffect(active.spearSpeedEffect);
                    active.phase = SpearPhase.LUNGING;
                    mob.getWorld().playSound(mob.getLocation(), Sound.ITEM_SPEAR_LUNGE_3, 1.0f, 2.0f);
                }

                Vector facing = mob.getLocation().getDirection().setY(0);
                if (facing.lengthSquared() < 0.01) {
                    facing = target.getLocation().toVector().subtract(mob.getLocation().toVector()).setY(0);
                }
                if (facing.lengthSquared() < 0.01) facing = new Vector(0, 0, 1);
                else facing.normalize();
                Location trailLoc = mob.getLocation().add(
                        facing.clone().multiply(-0.6).setY(mob.getHeight() * 0.5));
                mob.getWorld().spawnParticle(Particle.CRIT, trailLoc, 2, 0.15, 0.1, 0.15, 0.02);

                tick++;
                active.lungeElapsedTicks = Math.max(0, tick - active.chargeTicks);
                if (tick >= active.chargeTicks + active.lungeTicks) {
                    finishSession(active, false);
                }
            }
        };
        active.task = task;
        task.runTaskTimer(ctx.getPlugin(), 0L, 1L);
    }

    /**
     * 将真实的怪物近战伤害关联到活动中的 spear 冲锋。
     * 返回 true 表示该攻击消费了本次 spear 会话；原怪物是否本来就持矛不参与判定。
     */
    public boolean handleMeleeHit(EntityDamageByEntityEvent damageEvent, LivingEntity mob, Player victim) {
        if (damageEvent == null || mob == null || victim == null || damageEvent.isCancelled()) return false;
        if (damageEvent.getDamager() != mob || damageEvent.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return false;
        }

        ActiveSpear active = activeSpears.get(mob.getUniqueId());
        if (active == null || active.phase != SpearPhase.LUNGING) return false;
        if (!active.targetUuid.equals(victim.getUniqueId())) return false;
        if (!active.hitConsumed.compareAndSet(false, true)) return false;

        InfernalMobSpearHitEvent hitEvent = new InfernalMobSpearHitEvent(
                mob, victim, active.ctx.getHandle(), active.ctx.getMobState().getProfile().getLevel(),
                Math.max(0.0, damageEvent.getDamage()));
        if (active.ctx.fire(hitEvent)) {
            damageEvent.setDamage(Math.max(0.0, hitEvent.getDamage()));
        } else {
            damageEvent.setCancelled(true);
        }

        // 先同步消费会话，下一 tick 再恢复武器，避免影响当前原版攻击的物品结算。
        finishSession(active, true);
        return true;
    }

    private void finishSession(ActiveSpear active, boolean deferCleanup) {
        if (active == null || !active.finished.compareAndSet(false, true)) return;
        activeSpears.remove(active.mob.getUniqueId(), active);
        if (active.task != null) active.task.cancel();

        if (deferCleanup && active.ctx.getPlugin().isEnabled()) {
            active.ctx.getPlugin().getServer().getScheduler().runTask(
                    active.ctx.getPlugin(), () -> cleanupSession(active));
        } else {
            cleanupSession(active);
        }
    }

    private void cleanupSession(ActiveSpear active) {
        active.equipment.setItemInMainHand(active.savedHand);
        active.equipment.setItemInMainHandDropChance(active.savedHandDropChance);

        PotionEffect currentSpeed = active.mob.getPotionEffect(PotionEffectType.SPEED);
        if (active.speedEffectApplied && isSameSpearSpeed(currentSpeed, active.spearSpeedEffect)) {
            active.mob.removePotionEffect(PotionEffectType.SPEED);
            restorePreviousSpeed(active);
        }

        if (active.mob.isValid() && !active.mob.isDead()) {
            active.mob.setVelocity(new Vector(0, active.mob.getVelocity().getY(), 0));
        }
    }

    private boolean isSameSpearSpeed(PotionEffect current, PotionEffect spear) {
        return current != null && spear != null
                && current.getAmplifier() == spear.getAmplifier()
                && current.isAmbient() == spear.isAmbient()
                && current.hasParticles() == spear.hasParticles()
                && current.hasIcon() == spear.hasIcon();
    }

    private void restorePreviousSpeed(ActiveSpear active) {
        PotionEffect previous = active.previousSpeedEffect;
        if (previous == null) return;
        int duration = previous.getDuration();
        if (duration >= 0) {
            duration -= active.lungeElapsedTicks;
            if (duration <= 0) return;
        }
        active.mob.addPotionEffect(new PotionEffect(previous.getType(), duration,
                previous.getAmplifier(), previous.isAmbient(), previous.hasParticles(), previous.hasIcon()));
    }

    private ItemStack createSpearItem(String itemName, int sharpnessLevel) {
        try {
            Material material = Material.valueOf(itemName.trim().toUpperCase());
            if (material.isItem()) {
                ItemStack item = new ItemStack(material);
                if (sharpnessLevel > 0) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        meta.addEnchant(Enchantment.SHARPNESS, sharpnessLevel, true);
                        item.setItemMeta(meta);
                    }
                }
                return item;
            }
        } catch (IllegalArgumentException | NullPointerException ignored) {
        }
        return new ItemStack(Material.NETHERITE_SPEAR);
    }

    private Set<EntityType> parseEntityTypeSet(SkillConfig config, String... keys) {
        Set<EntityType> out = new HashSet<>();
        if (config == null || keys == null) return out;
        for (String key : keys) {
            if (key == null || key.isBlank()) continue;
            List<String> raw = config.getStringList(key);
            if (raw == null || raw.isEmpty()) continue;
            for (String s : raw) {
                if (s == null || s.isBlank()) continue;
                try {
                    out.add(EntityType.valueOf(s.trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return out;
    }

    private enum SpearPhase {
        CHARGING,
        LUNGING
    }

    private static final class ActiveSpear {
        private final SkillContext ctx;
        private final LivingEntity mob;
        private final UUID targetUuid;
        private final EntityEquipment equipment;
        private final ItemStack savedHand;
        private final float savedHandDropChance;
        private final int chargeTicks;
        private final int lungeTicks;
        private final int speedAmplifier;
        private final AtomicBoolean hitConsumed = new AtomicBoolean();
        private final AtomicBoolean finished = new AtomicBoolean();
        private volatile SpearPhase phase = SpearPhase.CHARGING;
        private volatile BukkitRunnable task;
        private PotionEffect previousSpeedEffect;
        private PotionEffect spearSpeedEffect;
        private boolean speedEffectApplied;
        private int lungeElapsedTicks;

        private ActiveSpear(SkillContext ctx, LivingEntity mob, Player target,
                            EntityEquipment equipment, ItemStack savedHand, float savedHandDropChance,
                            int chargeTicks, int lungeTicks, int speedAmplifier) {
            this.ctx = ctx;
            this.mob = mob;
            this.targetUuid = target.getUniqueId();
            this.equipment = equipment;
            this.savedHand = savedHand;
            this.savedHandDropChance = savedHandDropChance;
            this.chargeTicks = chargeTicks;
            this.lungeTicks = lungeTicks;
            this.speedAmplifier = speedAmplifier;
        }
    }

}
