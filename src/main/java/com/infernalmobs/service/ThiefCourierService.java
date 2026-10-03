package com.infernalmobs.service;

import com.infernalmobs.api.InfernalMobHandle;
import com.infernalmobs.api.event.affix.triggered.InfernalMobThiefEvent;
import com.infernalmobs.config.ConfigLoader;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.controller.listener.ThiefResistanceListener;
import com.infernalmobs.model.MobState;
import com.infernalmobs.util.Keys;
import com.infernalmobs.util.MiniMessageHelper;
import com.infernalmobs.util.SoundPlayback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * thief 悦灵信使服务。
 */
public final class ThiefCourierService {

    private static final double ARRIVAL_DISTANCE = 0.8;
    private static final double ACCELERATION_TICKS = 6.0;
    private static final double START_SPEED_RATIO = 0.15;
    private static final double RETURN_START_SPEED_RATIO = 0.25;
    private static final double ENDPOINT_SMOOTHING = 0.25;

    private static final String DARK_RED_TEAM_NAME = "thief_courier_team";

    private final JavaPlugin plugin;
    private final ConfigLoader config;
    private final Map<UUID, Courier> couriers = new ConcurrentHashMap<>();
    private BukkitTask task;

    private final Scoreboard scoreboard;

    public ThiefCourierService(JavaPlugin plugin, ConfigLoader config) {
        this.plugin = plugin;
        this.config = config;
        this.scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
    }

    public void start() {
        if (task != null && !task.isCancelled()) return;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
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
        CourierSettings settings = readSettings();
        Location origin = owner.getLocation().clone().add(0, owner.getEyeHeight() + 0.75, 0);
        RegionAccessor region = origin.getWorld();
        Allay allay = region.spawn(origin, Allay.class, entity -> configure(entity, settings));
        if (!allay.isValid()) return false;
        getDarkRedTeam().addEntity(allay);
        spawnSpawnParticles(origin);
        couriers.put(allay.getUniqueId(), new Courier(
                allay, player.getUniqueId(), owner.getUniqueId(), origin, triggerData, settings));
        playSound(player, "sound");
        start();
        return true;
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Courier courier : new ArrayList<>(couriers.values())) {
            removeSilently(courier, true);
        }
        couriers.clear();
    }

    /** 由死亡监听器调用；清空原生掉落后只处理一次自定义携带物品。 */
    public void handleDeath(EntityDeathEvent event) {
        Courier courier = couriers.remove(event.getEntity().getUniqueId());
        if (courier == null) return;
        Location deathLocation = event.getEntity().getLocation();
        broadcastSound(deathLocation, "courier.death-sound");
        dropCarriedItem(courier, deathLocation);
    }

    private void tick() {
        for (Courier courier : new ArrayList<>(couriers.values())) {
            if (!couriers.containsKey(courier.allay.getUniqueId())) continue;
            if (courier.allay.isDead()) {
                broadcastSound(courier.lastLocation, "courier.death-sound");
                dropCarriedItem(courier, courier.lastLocation);
                couriers.remove(courier.allay.getUniqueId());
                continue;
            }
            if (!courier.allay.isValid()) {
                removeNaturally(courier, true);
                continue;
            }

            if (courier.phase == FlightPhase.DESPAWN_HOLD) {
                if (courier.holdTicksRemaining > 0) {
                    courier.holdTicksRemaining--;
                    continue;
                }
                removeNaturally(courier, false);
                continue;
            }

            courier.ageTicks++;
            if (courier.ageTicks > courier.settings.maxLifetimeTicks()) {
                removeNaturally(courier, true);
                continue;
            }

            if (courier.phase == FlightPhase.SPAWN_HOLD) {
                Player target = resolveTarget(courier);
                if (target == null) {
                    removeNaturally(courier, false);
                    continue;
                }
                if (courier.holdTicksRemaining > 0) {
                    courier.holdTicksRemaining--;
                    continue;
                }
                courier.phase = FlightPhase.OUTBOUND;
                beginCurve(courier, outboundTarget(courier, target),
                        courier.settings.outboundSpeed(), START_SPEED_RATIO);
            }

            if (courier.phase == FlightPhase.RETURN_DELAY) {
                if (courier.holdTicksRemaining > 0) {
                    courier.holdTicksRemaining--;
                    continue;
                }
                courier.phase = FlightPhase.RETURNING;
                ReturnTarget returnTarget = resolveReturnTarget(courier);
                beginCurve(courier, returnTarget.flightLocation(), courier.settings.returnSpeed(),
                        RETURN_START_SPEED_RATIO);
            }

            if (courier.phase == FlightPhase.RETURNING) {
                ReturnTarget returnTarget = resolveReturnTarget(courier);
                MoveResult result = moveTowards(courier, returnTarget.flightLocation(), null,
                        courier.settings.returnSpeed());
                if (result == MoveResult.REACHED) {
                    finishReturn(courier, returnTarget.dropLocation());
                } else if (result == MoveResult.FAILED) {
                    removeNaturally(courier, true);
                }
                continue;
            }

            Player target = resolveTarget(courier);
            if (target == null) {
                removeNaturally(courier, false);
                continue;
            }

            Location targetLocation = outboundTarget(courier, target);
            MoveResult result = moveTowards(courier, targetLocation, target.getBoundingBox(),
                    courier.settings.outboundSpeed());
            if (result == MoveResult.HIT || result == MoveResult.REACHED) {
                boolean stolen = transferMainHand(target, courier);
                if (stolen) {
                    playSound(target, "courier.steal-sound");
                } else {
                    spawnStealFailedParticles(courier.allay);
                    playSound(target, "courier.steal-failed-sound");
                }
                if (stolen) {
                    courier.phase = FlightPhase.RETURNING;
                    ReturnTarget returnTarget = resolveReturnTarget(courier);
                    beginCurve(courier, returnTarget.flightLocation(), courier.settings.returnSpeed(),
                            RETURN_START_SPEED_RATIO);
                } else {
                    courier.phase = FlightPhase.RETURN_DELAY;
                    courier.holdTicksRemaining = ThreadLocalRandom.current().nextInt(10, 21);
                }
            } else if (result == MoveResult.FAILED) {
                removeNaturally(courier, false);
            }
        }
        if (couriers.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    private Player resolveTarget(Courier courier) {
        Player target = plugin.getServer().getPlayer(courier.targetId);
        if (target == null || !target.isOnline() || target.isDead()
                || target.getWorld() != courier.allay.getWorld()) return null;
        return target;
    }

    /** 沿动态贝塞尔曲线传送，并根据剩余距离自动加速或刹车。 */
    private MoveResult moveTowards(Courier courier, Location target, BoundingBox hitBox,
                                   double cruiseSpeed) {
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

        updateSmoothedEndpoint(courier, target);
        double acceleration = cruiseSpeed / ACCELERATION_TICKS;
        double brakingDistance = Math.max(0.0, distance - ARRIVAL_DISTANCE);
        double brakingLimit = Math.sqrt(2.0 * acceleration * brakingDistance);
        double minimumSpeed = cruiseSpeed * 0.12;
        double desiredSpeed = Math.max(minimumSpeed, Math.min(cruiseSpeed, brakingLimit));
        courier.currentSpeed = approach(courier.currentSpeed, desiredSpeed, acceleration);

        double effectiveHeight = effectiveArcHeight(courier.legStart, courier.smoothedEndpoint,
                courier.settings.arcHeight());
        double estimatedLength = Math.max(0.1,
                courier.legStart.distance(courier.smoothedEndpoint) + effectiveHeight * 0.5);
        courier.curveProgress = Math.min(1.0,
                courier.curveProgress + courier.currentSpeed / estimatedLength);
        Location curvePoint = bezierPoint(courier.legStart, courier.smoothedEndpoint,
                effectiveHeight, courier.curveProgress);
        Vector step = curvePoint.toVector().subtract(current.toVector());
        if (step.lengthSquared() < 0.000001 || step.dot(offset) <= 0.0) {
            step = offset.clone();
        }
        if (step.length() > courier.currentSpeed) step.normalize().multiply(courier.currentSpeed);

        Location next = current.clone().add(step);
        // 目标点带有每只悦灵独立的固定偏移；朝向仍保持指向该目标，避免左右摆头。
        Vector facing = target.toVector().subtract(next.toVector());
        if (facing.lengthSquared() < 0.000001) facing = offset;
        next.setDirection(facing);
        if (expandedHitBox != null
                && expandedHitBox.rayTrace(current.toVector(), step.clone().normalize(), step.length()) != null) {
            if (!courier.allay.teleport(target)) return MoveResult.FAILED;
            courier.lastLocation = target.clone();
            return MoveResult.HIT;
        }
        if (!courier.allay.teleport(next)) return MoveResult.FAILED;
        courier.lastLocation = next.clone();
        if (courier.curveProgress >= 1.0) {
            beginCurve(courier, target, cruiseSpeed,
                    courier.currentSpeed / Math.max(0.01, cruiseSpeed));
        }
        return MoveResult.MOVED;
    }

    private void beginCurve(Courier courier, Location target, double cruiseSpeed,
                            double startSpeedRatio) {
        Location current = courier.allay.getLocation();
        courier.legStart = current.clone();
        courier.smoothedEndpoint = target.clone();
        courier.curveProgress = 0.0;
        double ratio = Math.max(START_SPEED_RATIO, Math.min(1.0, startSpeedRatio));
        courier.currentSpeed = Math.max(0.01, cruiseSpeed * ratio);
    }

    private Location outboundTarget(Courier courier, Player target) {
        return target.getEyeLocation().add(courier.targetOffset);
    }

    private void updateSmoothedEndpoint(Courier courier, Location target) {
        if (courier.smoothedEndpoint == null || courier.smoothedEndpoint.getWorld() != target.getWorld()) {
            courier.smoothedEndpoint = target.clone();
            return;
        }
        Vector smoothed = courier.smoothedEndpoint.toVector().multiply(1.0 - ENDPOINT_SMOOTHING)
                .add(target.toVector().multiply(ENDPOINT_SMOOTHING));
        courier.smoothedEndpoint.setX(smoothed.getX());
        courier.smoothedEndpoint.setY(smoothed.getY());
        courier.smoothedEndpoint.setZ(smoothed.getZ());
    }

    private static Location bezierPoint(Location start, Location end, double height, double t) {
        double oneMinusT = 1.0 - t;
        Vector control = start.toVector().add(end.toVector()).multiply(0.5)
                .add(new Vector(0, height, 0));
        Vector point = start.toVector().multiply(oneMinusT * oneMinusT)
                .add(control.multiply(2.0 * oneMinusT * t))
                .add(end.toVector().multiply(t * t));
        return point.toLocation(start.getWorld());
    }

    private static double effectiveArcHeight(Location start, Location end, double configuredHeight) {
        double dx = end.getX() - start.getX();
        double dz = end.getZ() - start.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        return Math.min(configuredHeight, horizontalDistance * 0.35);
    }

    private static double approach(double current, double target, double amount) {
        if (current < target) return Math.min(target, current + amount);
        return Math.max(target, current - amount);
    }

    /** 为每只悦灵固定一个玩家眼睛附近的目标偏移，避免多只悦灵飞向完全相同的终点。 */
    private static Vector randomTargetOffset() {
        double angle = ThreadLocalRandom.current().nextDouble(0.0, Math.PI * 2.0);
        double radius = ThreadLocalRandom.current().nextDouble(-0.2, 0.2);
        double vertical = ThreadLocalRandom.current().nextDouble(-0.15, 0.15);
        return new Vector(Math.cos(angle) * radius, vertical, Math.sin(angle) * radius);
    }

    private CourierSettings readSettings() {
        SkillConfig skillConfig = config.getSkillConfig("thief");
        if (skillConfig == null) return CourierSettings.defaults();
        int spawnDelay = clamp(skillConfig.getInt("courier.spawn-delay-ticks", 10), 0, 100);
        int despawnDelay = clamp(skillConfig.getInt("courier.despawn-delay-ticks", 10), 0, 100);
        double outboundSpeed = clamp(skillConfig.getDouble("courier.outbound-speed", 0.45), 0.05, 2.0, 0.45);
        double returnSpeed = clamp(skillConfig.getDouble("courier.return-speed", 0.55), 0.05, 2.0, 0.55);
        double arcHeight = clamp(skillConfig.getDouble("courier.arc-height", 1.2), 0.0, 5.0, 1.2);
        double maxHealth = clamp(skillConfig.getDouble("courier.max-health", 6.0), 0.1, 2048.0, 6.0);
        String name = skillConfig.getString("courier.name", "<#c9a227>缴械信使</#c9a227>");
        if (name.isBlank()) name = "<#c9a227>缴械信使</#c9a227>";
        int maxLifetime = clamp(skillConfig.getInt("courier.max-lifetime-ticks", 200),
                spawnDelay + 20, 1200);
        return new CourierSettings(name, maxHealth, spawnDelay, despawnDelay,
                outboundSpeed, returnSpeed, arcHeight, maxLifetime);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max, double fallback) {
        if (!Double.isFinite(value)) return fallback;
        return Math.max(min, Math.min(max, value));
    }

    /** RegionAccessor 的初始化回调会在实体加入世界前执行，避免客户端看到未配置的悦灵。 */
    private void configure(Allay allay, CourierSettings settings) {
        allay.getPersistentDataContainer().set(Keys.THIEF_COURIER, PersistentDataType.BYTE, (byte) 1);
        allay.customName(MiniMessageHelper.deserialize(settings.name()));
        allay.setInvisible(true);
        allay.setGlowing(true);
        allay.setSilent(true);
        allay.setAI(false);
        allay.setGravity(false);
        // allay.setCollidable(false); // 如果设置为 false，箭无法命中
        allay.setCanPickupItems(false);
        allay.setCanDuplicate(false);
        allay.setPersistent(true);
        var maxHealth = allay.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(settings.maxHealth());
            allay.setHealth(settings.maxHealth());
        }
        allay.getEquipment().setItemInMainHand(ItemStack.empty());
        allay.getEquipment().setItemInMainHandDropChance(0.0f);
    }

    /** 命中时才读取主手；正式触发会先广播缴械事件，再原子校验并转移物品。 */
    private boolean transferMainHand(Player player, Courier courier) {
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

        if (ejectUnexpectedHeldItem(courier, courier.lastLocation)) {
            broadcastSound(courier.lastLocation, "courier.drop-sound");
        }
        player.getInventory().setItemInMainHand(ItemStack.empty());
        courier.carriedItem = carried;
        courier.allay.getEquipment().setItemInMainHand(carried.clone());
        courier.allay.getEquipment().setItemInMainHandDropChance(0.0f);
        return true;
    }

    private void playSound(Player player, String path) {
        SkillConfig skillConfig = config.getSkillConfig("thief");
        if (skillConfig != null) SoundPlayback.play(player, skillConfig.getSound(path));
    }

    private void broadcastSound(Location location, String path) {
        if (location == null || location.getWorld() == null) return;
        SkillConfig skillConfig = config.getSkillConfig("thief");
        if (skillConfig != null) SoundPlayback.broadcast(location, skillConfig.getSound(path));
    }

    /** 生成时的女巫粒子从悦灵位置向四周散开。 */
    private void spawnSpawnParticles(Location location) {
        if (location == null || location.getWorld() == null) return;
        location.getWorld().spawnParticle(Particle.WITCH, location, 24,
                0.35, 0.45, 0.35, 0.08);
    }

    /** 夺取失败时在悦灵头顶冒出少量愤怒粒子。 */
    private void spawnStealFailedParticles(Allay allay) {
        if (allay == null || !allay.isValid()) return;
        Location head = allay.getLocation().clone().add(0, allay.getHeight() - 0.2, 0);
        head.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, head, 2,
                0.12, 0.05, 0.12, 0.04);
    }

    /** 正常消失时在悦灵当前位置播放一团烟雾。 */
    private void spawnDespawnParticles(Location location) {
        if (location == null || location.getWorld() == null) return;
        location.getWorld().spawnParticle(Particle.POOF, location, 12,
                0.2, 0.3, 0.2, 0.12);
    }

    private void finishReturn(Courier courier, Location dropLocation) {
        if (dropCarriedItem(courier, dropLocation)) {
            broadcastSound(courier.lastLocation, "courier.drop-sound");
        }
        courier.phase = FlightPhase.DESPAWN_HOLD;
        courier.holdTicksRemaining = courier.settings.despawnDelayTicks();
    }

    private ReturnTarget resolveReturnTarget(Courier courier) {
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

    private void removeNaturally(Courier courier, boolean dropCarried) {
        couriers.remove(courier.allay.getUniqueId());
        if (dropCarried && dropCarriedItem(courier, courier.lastLocation)) {
            broadcastSound(courier.lastLocation, "courier.drop-sound");
        }
        spawnDespawnParticles(courier.lastLocation);
        broadcastSound(courier.lastLocation, "courier.despawn-sound");
        if (courier.allay.isValid()) courier.allay.remove();
    }

    /** 插件关闭时保全物品，但不播放退出过程中的游戏音效。 */
    private void removeSilently(Courier courier, boolean dropCarried) {
        couriers.remove(courier.allay.getUniqueId());
        if (dropCarried) dropCarriedItem(courier, courier.lastLocation);
        if (courier.allay.isValid()) courier.allay.remove();
    }

    private boolean dropCarriedItem(Courier courier, Location location) {
        ItemStack carried = courier.carriedItem;
        if (carried == null || carried.getType().isAir()) {
            carried = courier.allay.getEquipment().getItemInMainHand();
        }
        if (carried.getType().isAir() || location == null || location.getWorld() == null) return false;
        courier.allay.getEquipment().setItemInMainHand(ItemStack.empty());
        location.getWorld().dropItemNaturally(location, carried.clone());
        courier.carriedItem = null;
        return true;
    }

    /** 覆盖悦灵主手前先转移意外物品，避免无声删除或替换。 */
    private boolean ejectUnexpectedHeldItem(Courier courier, Location location) {
        if (courier.carriedItem != null) return false;
        ItemStack held = courier.allay.getEquipment().getItemInMainHand();
        if (held.getType().isAir() || location == null || location.getWorld() == null) return false;
        courier.allay.getEquipment().setItemInMainHand(ItemStack.empty());
        location.getWorld().dropItemNaturally(location, held.clone());
        return true;
    }

    private Team getDarkRedTeam() {
        Team team = scoreboard.getTeam(DARK_RED_TEAM_NAME);

        if (team == null) {
            team = scoreboard.registerNewTeam(DARK_RED_TEAM_NAME);
        }

        if (!team.hasColor() ||!NamedTextColor.DARK_RED.equals(team.color())) {
            team.color(NamedTextColor.DARK_RED);
        }

        return team;
    }

    private enum MoveResult {
        MOVED,
        REACHED,
        HIT,
        FAILED
    }

    private enum FlightPhase {
        SPAWN_HOLD,
        OUTBOUND,
        RETURNING,
        RETURN_DELAY,
        DESPAWN_HOLD
    }

    private record ReturnTarget(Location flightLocation, Location dropLocation) {}

    private record TriggerData(LivingEntity releasedMob, MobState state, InfernalMobHandle handle,
                               int level, long releasedTick, int cooldownTicks) {}

    private record CourierSettings(String name, double maxHealth, int spawnDelayTicks,
                                   int despawnDelayTicks, double outboundSpeed, double returnSpeed,
                                   double arcHeight, int maxLifetimeTicks) {
        private static CourierSettings defaults() {
            return new CourierSettings("<#c9a227>缴械信使</#c9a227>", 6.0,
                    10, 10, 0.45, 0.55, 1.2, 200);
        }
    }

    private static final class Courier {
        private final Allay allay;
        private final UUID targetId;
        private final UUID ownerId;
        private final Location origin;
        private final TriggerData triggerData;
        private final CourierSettings settings;
        private Location lastLocation;
        private Location overrideDropLocation;
        private Location legStart;
        private Location smoothedEndpoint;
        private final Vector targetOffset = randomTargetOffset();
        private ItemStack carriedItem;
        private FlightPhase phase = FlightPhase.SPAWN_HOLD;
        private int holdTicksRemaining;
        private int ageTicks;
        private double currentSpeed;
        private double curveProgress;

        private Courier(Allay allay, UUID targetId, UUID ownerId, Location origin,
                            TriggerData triggerData, CourierSettings settings) {
            this.allay = allay;
            this.targetId = targetId;
            this.ownerId = ownerId;
            this.origin = origin.clone();
            this.triggerData = triggerData;
            this.settings = settings;
            this.lastLocation = origin.clone();
            this.holdTicksRemaining = settings.spawnDelayTicks();
        }
    }
}
