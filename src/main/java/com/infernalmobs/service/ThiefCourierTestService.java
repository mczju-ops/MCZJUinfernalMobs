package com.infernalmobs.service;

import com.infernalmobs.config.ConfigLoader;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.util.SoundPlayback;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * thief 重制的第一阶段测试器：只验证悦灵的视觉状态和往返飞行，不接管玩家物品。
 * 后续正式信使逻辑稳定后，此类会被正式信使服务替代或直接扩展。
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

    /** 在玩家头顶生成一只测试悦灵，飞向玩家后再返回生成点。 */
    public boolean spawnFor(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        Location origin = player.getLocation().add(4, 5.0, 0);
        Allay allay = (Allay) origin.getWorld().spawnEntity(origin, org.bukkit.entity.EntityType.ALLAY);
        configure(allay);
        playSound(origin, "courier.spawn-sound");
        couriers.put(allay.getUniqueId(), new TestCourier(allay, player.getUniqueId(), origin));
        start();
        return true;
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (TestCourier courier : new ArrayList<>(couriers.values())) {
            remove(courier, false);
        }
        couriers.clear();
    }

    private void tick() {
        for (TestCourier courier : new ArrayList<>(couriers.values())) {
            if (!couriers.containsKey(courier.allay.getUniqueId())) continue;
            courier.ageTicks++;
            if (courier.ageTicks > MAX_LIFETIME_TICKS) {
                remove(courier, false);
                continue;
            }
            if (!courier.allay.isValid() || courier.allay.isDead()) {
                playSound(courier.lastLocation, "courier.death-sound");
                couriers.remove(courier.allay.getUniqueId());
                continue;
            }

            Player target = plugin.getServer().getPlayer(courier.targetId);
            if (target == null || !target.isOnline() || target.getWorld() != courier.allay.getWorld()) {
                remove(courier, false);
                continue;
            }

            if (courier.returning) {
                if (moveTowards(courier, courier.origin)) {
                    remove(courier, false);
                }
                continue;
            }

            if (courier.pauseTicks > 0) {
                courier.pauseTicks--;
                continue;
            }

            Location targetLocation = target.getLocation().clone().add(0, 0.3, 0);
            if (moveTowards(courier, targetLocation)) {
                courier.returning = true;
                courier.pauseTicks = ARRIVAL_PAUSE_TICKS;
            }
        }
        if (couriers.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    /** 直接传送到逐 tick 计算的位置，避免测试阶段受到方块碰撞阻挡。 */
    private boolean moveTowards(TestCourier courier, Location target) {
        Location current = courier.allay.getLocation();
        if (current.getWorld() != target.getWorld()) return false;
        Vector offset = target.toVector().subtract(current.toVector());
        double distance = offset.length();
        if (distance <= ARRIVAL_DISTANCE) {
            courier.lastLocation = current.clone();
            return true;
        }
        Vector step = offset.normalize().multiply(Math.min(SPEED, distance));
        Location next = current.clone().add(step);
        next.setDirection(step);
        if (!courier.allay.teleport(next)) return false;
        courier.lastLocation = next.clone();
        return false;
    }

    private void configure(Allay allay) {
        allay.setInvisible(true);
        allay.setGlowing(true);
        allay.setSilent(true);
        allay.setAI(false);
        allay.setGravity(false);
        allay.setCollidable(false);
        allay.setCanPickupItems(false);
        allay.setCanDuplicate(false);
        allay.setPersistent(true);
        allay.getEquipment().setItemInMainHand(new ItemStack(Material.GOLD_INGOT));
        allay.getEquipment().setItemInMainHandDropChance(0.0f);
    }

    private void playSound(Location location, String path) {
        if (location == null || location.getWorld() == null) return;
        SkillConfig skillConfig = config.getSkillConfig("thief");
        if (skillConfig != null) SoundPlayback.broadcast(location, skillConfig.getSound(path));
    }

    private void remove(TestCourier courier, boolean playDeathSound) {
        couriers.remove(courier.allay.getUniqueId());
        if (playDeathSound) playSound(courier.lastLocation, "courier.death-sound");
        if (courier.allay.isValid()) courier.allay.remove();
    }

    private static final class TestCourier {
        private final Allay allay;
        private final UUID targetId;
        private final Location origin;
        private Location lastLocation;
        private boolean returning;
        private int pauseTicks;
        private int ageTicks;

        private TestCourier(Allay allay, UUID targetId, Location origin) {
            this.allay = allay;
            this.targetId = targetId;
            this.origin = origin.clone();
            this.lastLocation = origin.clone();
        }
    }
}
