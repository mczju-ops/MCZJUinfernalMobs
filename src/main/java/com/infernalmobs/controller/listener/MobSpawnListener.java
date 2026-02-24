package com.infernalmobs.controller.listener;

import com.infernalmobs.config.ConfigLoader;
import com.infernalmobs.factory.MobFactory;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.Set;

/**
 * 接管怪物生成。在启用世界中，取消原版自然刷怪，由插件控制何时生成。
 * 注意：为完全接管，需要根据刷怪逻辑在合适时机手动 spawn 并 mechanize。
 * 此处采用简化策略：监听 CreatureSpawnEvent，对自然刷怪进行替换。
 */
public class MobSpawnListener implements Listener {

    private static final Set<EntityType> HOSTILE_MOBS = Set.of(
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER, EntityType.SPIDER,
            EntityType.CAVE_SPIDER, EntityType.ENDERMAN, EntityType.WITCH, EntityType.PHANTOM,
            EntityType.DROWNED, EntityType.STRAY, EntityType.HUSK, EntityType.PIGLIN,
            EntityType.PIGLIN_BRUTE, EntityType.ZOGLIN, EntityType.WARDEN,
            EntityType.BLAZE, EntityType.GHAST, EntityType.MAGMA_CUBE
    );

    private final ConfigLoader config;
    private final MobFactory mobFactory;

    public MobSpawnListener(ConfigLoader config, MobFactory mobFactory) {
        this.config = config;
        this.mobFactory = mobFactory;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;
        LivingEntity entity = (LivingEntity) event.getEntity();
        if (!HOSTILE_MOBS.contains(event.getEntityType())) return;
        if (!config.isWorldEnabled(event.getLocation().getWorld().getName())) return;

        // 仅处理自然刷怪（NATURAL, SPAWNER 等），避免影响刷怪蛋、命令等
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason != CreatureSpawnEvent.SpawnReason.NATURAL
                && reason != CreatureSpawnEvent.SpawnReason.SPAWNER
                && reason != CreatureSpawnEvent.SpawnReason.PATROL
                && reason != CreatureSpawnEvent.SpawnReason.REINFORCEMENTS) {
            return;
        }

        // 不取消事件，直接对已生成的实体进行炒鸡怪改造
        mobFactory.mechanize(entity, event.getLocation());
    }
}
