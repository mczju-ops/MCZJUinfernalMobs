package com.infernalmobs.controller.listener;

import com.infernalmobs.factory.MobFactory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;

/** 负责区块实体加载恢复与卸载注销。 */
public final class MobPersistenceListener implements Listener {

    private final MobFactory mobFactory;

    public MobPersistenceListener(MobFactory mobFactory) {
        this.mobFactory = mobFactory;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof LivingEntity living && !(living instanceof Player)) {
                mobFactory.restoreFromPdc(living);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof LivingEntity living) mobFactory.unregisterForUnload(living);
        }
    }
}
