package com.infernalmobs.service;

import com.infernalmobs.api.InfernalMobHandle;
import com.infernalmobs.api.event.affix.triggered.InfernalMobThiefEvent;
import com.infernalmobs.config.ConfigLoader;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.controller.listener.ThiefResistanceListener;
import com.infernalmobs.model.MobState;
import com.infernalmobs.util.Keys;
import com.infernalmobs.util.SoundPlayback;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * thief 悦灵信使服务。当前同时保留临时测试入口，待真实战斗验证完成后再移除测试命名。
 */
public final class ThiefCourierTestService {

    private static final double SPEED = 0.45;
    private static final double ARRIVAL_DISTANCE = 0.8;
    private static final int ARRIVAL_PAUSE_TICKS = 10;
    private static final int MAX_LIFETIME_TICKS = 200;

    private final JavaPlugin plugin;
    private final ConfigLoader config;
    private final Map<UUID, TestCourier> couriers = new ConcurrentHashMap<>();
    private BukkitTask task;

    public ThiefCourierTestService(JavaPlugin plugin, ConfigLoader config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void start() {
        if (task != null && !task.isCancelled()) return;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    /** 在主人头顶生成测试悦灵，飞向玩家后再追踪主人返程。 */
    public boolean spawnFor(Player player, LivingEntity owner) {
        if (player == null || !player.isOnline() || owner == null || !owner.isValid() || owner.isDead()) {
            return false;
        }
        if (player.getWorld() != owner.getWorld()) return false;
        return spawnCourier(player, owner, null);
    }

    /** 正式词条入口；返回 true 表示悦灵已成功生成。 */
    public boolean launch(Player player, LivingEntity owner, MobState state,
                          InfernalMobHandle handle, long releasedTick, int cooldownTicks) {
        if (state == null || handle == null) return false;
        TriggerData triggerData = new TriggerData(owner, state, handle,
                state.getProfile().getLevel(), releasedTick, cooldownTicks);
        return spawnCourier(player, owner, triggerData);
    }

    private boolean spawnCourier(Player player, LivingEntity owner, TriggerData triggerData) {
        if (player == null || !player.isOnline() || owner == null || !owner.isValid() || owner.isDead()) {
            return false;
        }
        if (player.getWorld() != owner.getWorld()) return false;
        Location origin = owner.getLocation().clone().add(0, owner.getEyeHeight() + 0.75, 0);
        Allay allay = (Allay) origin.getWorld().spawnEntity(origin, org.bukkit.entity.EntityType.ALLAY);
        configure(allay);
        playSound(origin, "courier.spawn-sound");
        couriers.put(allay.getUniqueId(), new TestCourier(
                allay, player.getUniqueId(), owner.getUniqueId(), origin, triggerData));
        start();
        return true;
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (TestCourier courier : new ArrayList<>(couriers.values())) {
            cleanup(courier, true);
        }
        couriers.clear();
    }

    /** 由死亡监听器调用；清空原生掉落后只处理一次自定义携带物品。 */
    public void handleDeath(EntityDeathEvent event) {
        TestCourier courier = couriers.remove(event.getEntity().getUniqueId());
        if (courier == null) return;
        playSound(courier.lastLocation, "courier.death-sound");
        dropCarriedItem(courier, courier.lastLocation);
    }

    private void tick() {
        for (TestCourier courier : new ArrayList<>(couriers.values())) {
            if (!couriers.containsKey(courier.allay.getUniqueId())) continue;
            courier.ageTicks++;
            if (courier.ageTicks > MAX_LIFETIME_TICKS) {
                cleanup(courier, true);
                continue;
            }
            if (!courier.allay.isValid() || courier.allay.isDead()) {
                playSound(courier.lastLocation, "courier.death-sound");
                dropCarriedItem(courier, courier.lastLocation);
                couriers.remove(courier.allay.getUniqueId());
                continue;
            }

            if (courier.returning) {
                ReturnTarget returnTarget = resolveReturnTarget(courier);
                MoveResult result = moveTowards(courier, returnTarget.flightLocation(), null);
                if (result == MoveResult.REACHED || result == MoveResult.FAILED) {
                    finishReturn(courier, returnTarget.dropLocation());
                }
                continue;
            }

            Player target = plugin.getServer().getPlayer(courier.targetId);
            if (target == null || !target.isOnline() || target.isDead()
                    || target.getWorld() != courier.allay.getWorld()) {
                remove(courier, false);
                continue;
            }

            if (courier.pauseTicks > 0) {
                courier.pauseTicks--;
                continue;
            }

            Location targetLocation = target.getLocation().clone().add(0, 0.3, 0);
            MoveResult result = moveTowards(courier, targetLocation, target.getBoundingBox());
            if (result == MoveResult.HIT || result == MoveResult.REACHED) {
                if (transferMainHand(target, courier)) {
                    playSound(courier.lastLocation, "courier.steal-sound");
                }
                courier.returning = true;
                courier.pauseTicks = ARRIVAL_PAUSE_TICKS;
            } else if (result == MoveResult.FAILED) {
                remove(courier, false);
            }
        }
        if (couriers.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    /** 直接传送到逐 tick 计算的位置，避免测试阶段受到方块碰撞阻挡。 */
    private MoveResult moveTowards(TestCourier courier, Location target, BoundingBox hitBox) {
        Location current = courier.allay.getLocation();
        if (current.getWorld() != target.getWorld()) return MoveResult.FAILED;
        Vector offset = target.toVector().subtract(current.toVector());
        double distance = offset.length();
        BoundingBox expandedHitBox = hitBox == null ? null : hitBox.expand(0.3);
        if (expandedHitBox != null && expandedHitBox.overlaps(courier.allay.getBoundingBox())) {
            courier.lastLocation = current.clone();
            return MoveResult.HIT;
        }
        if (distance <= ARRIVAL_DISTANCE) {
            courier.lastLocation = current.clone();
            return MoveResult.REACHED;
        }
        Vector step = offset.normalize().multiply(Math.min(SPEED, distance));
        Location next = current.clone().add(step);
        next.setDirection(step);
        if (expandedHitBox != null
                && expandedHitBox.rayTrace(current.toVector(), step.clone().normalize(), step.length()) != null) {
            if (!courier.allay.teleport(target)) return MoveResult.FAILED;
            courier.lastLocation = target.clone();
            return MoveResult.HIT;
        }
        if (!courier.allay.teleport(next)) return MoveResult.FAILED;
        courier.lastLocation = next.clone();
        return MoveResult.MOVED;
    }

    private void configure(Allay allay) {
        allay.getPersistentDataContainer().set(Keys.THIEF_COURIER, PersistentDataType.BYTE, (byte) 1);
        allay.setInvisible(true);
        allay.setGlowing(true);
        allay.setSilent(true);
        allay.setAI(false);
        allay.setGravity(false);
        allay.setCollidable(false);
        allay.setCanPickupItems(false);
        allay.setCanDuplicate(false);
        allay.setPersistent(true);
        allay.getEquipment().setItemInMainHand(ItemStack.empty());
        allay.getEquipment().setItemInMainHandDropChance(0.0f);
    }

    /** 命中时才读取主手；正式触发会先广播缴械事件，再原子校验并转移物品。 */
    private boolean transferMainHand(Player player, TestCourier courier) {
        if (player.getGameMode() == GameMode.CREATIVE) return false;
        ItemStack current = player.getInventory().getItemInMainHand();
        if (current.getType().isAir() || ThiefResistanceListener.isResistant(current)) return false;

        ItemStack captured = current.clone();
        ItemStack carried = captured;
        TriggerData trigger = courier.triggerData;
        if (trigger != null) {
            ReturnTarget defaultReturn = resolveReturnTarget(courier);
            InfernalMobThiefEvent event = new InfernalMobThiefEvent(
                    trigger.releasedMob(), trigger.handle(), trigger.level(), player,
                    captured.clone(), defaultReturn.dropLocation().clone(), trigger.cooldownTicks());
            plugin.getServer().getPluginManager().callEvent(event);

            // 发射时已预占默认冷却；事件可以在命中时覆写最终冷却，0 表示立即解除。
            trigger.state().setCooldown("thief", trigger.releasedTick() + event.getCooldownTicks());
            if (event.isCancelled()) return false;

            ItemStack stillHeld = player.getInventory().getItemInMainHand();
            if (!stillHeld.equals(captured)) return false;
            carried = event.getItemStack().clone();
            if (carried.getType().isAir() || carried.getAmount() <= 0) return false;

            Location eventDrop = event.getDropLocation();
            if (!sameLocation(eventDrop, defaultReturn.dropLocation())) {
                courier.overrideDropLocation = eventDrop.clone();
            }
        }

        ejectUnexpectedHeldItem(courier, courier.lastLocation);
        player.getInventory().setItemInMainHand(ItemStack.empty());
        courier.carriedItem = carried;
        courier.allay.getEquipment().setItemInMainHand(carried.clone());
        courier.allay.getEquipment().setItemInMainHandDropChance(0.0f);
        return true;
    }

    private void playSound(Location location, String path) {
        if (location == null || location.getWorld() == null) return;
        SkillConfig skillConfig = config.getSkillConfig("thief");
        if (skillConfig != null) SoundPlayback.broadcast(location, skillConfig.getSound(path));
    }

    private void remove(TestCourier courier, boolean playDeathSound) {
        remove(courier, playDeathSound, false);
    }

    private void cleanup(TestCourier courier, boolean dropCarried) {
        remove(courier, false, dropCarried);
    }

    private void finishReturn(TestCourier courier, Location dropLocation) {
        couriers.remove(courier.allay.getUniqueId());
        dropCarriedItem(courier, dropLocation);
        if (courier.allay.isValid()) courier.allay.remove();
    }

    private ReturnTarget resolveReturnTarget(TestCourier courier) {
        if (courier.overrideDropLocation != null) {
            Location drop = courier.overrideDropLocation.clone();
            return new ReturnTarget(drop.clone().add(0, 0.8, 0), drop);
        }
        Entity entity = plugin.getServer().getEntity(courier.ownerId);
        if (entity instanceof LivingEntity owner && owner.isValid() && !owner.isDead()
                && owner.getWorld() == courier.allay.getWorld()) {
            Location ownerFeet = owner.getLocation().clone().add(0, 0.2, 0);
            Location flight = ownerFeet.clone().add(0, Math.min(owner.getEyeHeight(), 1.0), 0);
            return new ReturnTarget(flight, ownerFeet);
        }
        return new ReturnTarget(courier.origin, courier.origin);
    }

    private boolean sameLocation(Location first, Location second) {
        if (first == null || second == null || first.getWorld() != second.getWorld()) return false;
        return first.distanceSquared(second) < 0.000001;
    }

    private void remove(TestCourier courier, boolean playDeathSound, boolean dropCarried) {
        couriers.remove(courier.allay.getUniqueId());
        if (dropCarried) dropCarriedItem(courier, courier.lastLocation);
        if (playDeathSound) playSound(courier.lastLocation, "courier.death-sound");
        if (courier.allay.isValid()) courier.allay.remove();
    }

    private void dropCarriedItem(TestCourier courier, Location location) {
        ItemStack carried = courier.carriedItem;
        if (carried == null || carried.getType().isAir()) {
            carried = courier.allay.getEquipment().getItemInMainHand();
        }
        if (carried.getType().isAir() || location == null || location.getWorld() == null) return;
        courier.allay.getEquipment().setItemInMainHand(ItemStack.empty());
        location.getWorld().dropItemNaturally(location, carried.clone());
        courier.carriedItem = null;
    }

    /** 覆盖悦灵主手前先转移意外物品，避免无声删除或替换。 */
    private void ejectUnexpectedHeldItem(TestCourier courier, Location location) {
        if (courier.carriedItem != null) return;
        ItemStack held = courier.allay.getEquipment().getItemInMainHand();
        if (held.getType().isAir() || location == null || location.getWorld() == null) return;
        courier.allay.getEquipment().setItemInMainHand(ItemStack.empty());
        location.getWorld().dropItemNaturally(location, held.clone());
    }

    private enum MoveResult {
        MOVED,
        REACHED,
        HIT,
        FAILED
    }

    private record ReturnTarget(Location flightLocation, Location dropLocation) {}

    private record TriggerData(LivingEntity releasedMob, MobState state, InfernalMobHandle handle,
                               int level, long releasedTick, int cooldownTicks) {}

    private static final class TestCourier {
        private final Allay allay;
        private final UUID targetId;
        private final UUID ownerId;
        private final Location origin;
        private final TriggerData triggerData;
        private Location lastLocation;
        private Location overrideDropLocation;
        private ItemStack carriedItem;
        private boolean returning;
        private int pauseTicks;
        private int ageTicks;

        private TestCourier(Allay allay, UUID targetId, UUID ownerId, Location origin,
                            TriggerData triggerData) {
            this.allay = allay;
            this.targetId = targetId;
            this.ownerId = ownerId;
            this.origin = origin.clone();
            this.triggerData = triggerData;
            this.lastLocation = origin.clone();
        }
    }
}
