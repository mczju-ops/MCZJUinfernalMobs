package com.infernalmobs.controller.listener;

import com.infernalmobs.service.ThiefCourierTestService;
import com.infernalmobs.util.Keys;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;

/** 拦截 thief 悦灵的玩家交互，并接管其死亡掉落。 */
public final class ThiefCourierListener implements Listener {

    private final ThiefCourierTestService courierService;

    public ThiefCourierListener(ThiefCourierTestService courierService) {
        this.courierService = courierService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEntityEvent event) {
        if (isCourier(event.getRightClicked())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCourierDeath(EntityDeathEvent event) {
        if (!isCourier(event.getEntity())) return;
        event.getDrops().clear();
        courierService.handleDeath(event);
    }

    private boolean isCourier(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return false;
        return living.getPersistentDataContainer().has(Keys.THIEF_COURIER, PersistentDataType.BYTE);
    }
}
