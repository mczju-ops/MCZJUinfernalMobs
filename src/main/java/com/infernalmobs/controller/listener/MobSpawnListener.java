package com.infernalmobs.controller.listener;

import com.infernalmobs.config.ConfigLoader;
import com.infernalmobs.factory.MobFactory;
import com.infernalmobs.service.CombatService;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 监听 {@link CreatureSpawnEvent}，在启用世界且生成原因匹配配置时，对已生成的生物调用 {@link MobFactory#mechanize}。
 * 是否炒鸡化完全由区域 {@code infernal-allow-types} 与 {@code defaults.infernal.allow-types} 白名单决定，无额外硬编码生物表。
 */
public class MobSpawnListener implements Listener {

    private final ConfigLoader config;
    private final MobFactory mobFactory;
    private final CombatService combatService;
    private final JavaPlugin plugin;

    public MobSpawnListener(ConfigLoader config, MobFactory mobFactory, CombatService combatService,
                            JavaPlugin plugin) {
        this.config = config;
        this.mobFactory = mobFactory;
        this.combatService = combatService;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;
        LivingEntity entity = (LivingEntity) event.getEntity();
        if (!config.isWorldEnabled(event.getLocation().getWorld().getName())) return;

        if (entity.getType() == EntityType.CAMEL_HUSK) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    syncCamelHuskPassenger(entity);
                }
            }.runTask(plugin);
        }

        // 生成原因可配置（config.yml: infernal-spawn-reasons）
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (!config.getInfernalSpawnReasons().contains(reason)) return;

        // 不取消事件；白名单在 mechanize 内与区域/defaults 一致
        mobFactory.mechanize(entity, event.getLocation());
    }

    private void syncCamelHuskPassenger(LivingEntity camel) {
        if (!camel.isValid() || camel.getPassengers().isEmpty()) return;

        Entity firstPassenger = camel.getPassengers().get(0);
        boolean infernalMount = combatService.getMobState(camel.getUniqueId()) != null;
        boolean infernalRider = combatService.getMobState(firstPassenger.getUniqueId()) != null;
        if (!infernalMount && !infernalRider) return;

        if (camel.getPassengers().size() < 2) {
            addInfernalPassenger(camel);
            return;
        }

        Entity secondPassenger = camel.getPassengers().get(1);
        if (!(secondPassenger instanceof LivingEntity passenger)) return;
        if (combatService.getMobState(passenger.getUniqueId()) != null) return;

        mobFactory.mechanizeWithLevelForced(passenger, camel.getLocation(),
                mobFactory.computeLevelAt(camel.getLocation()));
    }

    private void addInfernalPassenger(LivingEntity camel) {
        List<EntityType> riderPool = getEnabledRiderPool();
        if (riderPool.isEmpty()) return;

        List<EntityType> attempts = new ArrayList<>(riderPool);
        Collections.shuffle(attempts);
        for (EntityType type : attempts) {
            Entity passenger = camel.getWorld().spawnEntity(camel.getLocation(), type);
            if (!(passenger instanceof LivingEntity passengerEntity)) {
                passenger.remove();
                continue;
            }

            mobFactory.mechanizeWithLevelForced(passengerEntity, camel.getLocation(),
                    mobFactory.computeLevelAt(camel.getLocation()));
            if (camel.addPassenger(passenger)) return;
            passenger.remove();
        }
    }

    private List<EntityType> getEnabledRiderPool() {
        var mountedConfig = config.getSkillConfig("mounted");
        if (mountedConfig == null) return List.of();

        List<EntityType> pool = new ArrayList<>();
        for (String value : mountedConfig.getStringList("enabled-riders")) {
            try {
                EntityType type = EntityType.valueOf(value.trim().toUpperCase(Locale.ROOT));
                if (type.isSpawnable() && type.getEntityClass() != null
                        && LivingEntity.class.isAssignableFrom(type.getEntityClass())) {
                    pool.add(type);
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore invalid configured entity types.
            }
        }
        return pool;
    }
}
