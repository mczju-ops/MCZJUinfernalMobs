package com.infernalmobs.controller.listener;

import com.infernalmobs.service.CombatService;
import com.infernalmobs.util.Keys;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * 炒鸡怪捡起过物品后，它掉出的物品（包括活着换装被替换下来的旧装备、死亡掉落）
 * 以无敌实体落地。无敌只写在 Item 实体上，拾起/进入容器后自然失效，再次丢出不会恢复。
 */
public final class InfernalDropProtectionListener implements Listener {

    private final CombatService combatService;

    public InfernalDropProtectionListener(CombatService combatService) {
        this.combatService = combatService;
    }

    /** 只标记当前已注册的炒鸡怪；普通怪/玩家不处理。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        LivingEntity entity = event.getEntity();
        if (combatService.getMobState(entity.getUniqueId()) == null) return;

        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (pdc.getOrDefault(Keys.INFERNAL_PICKED_UP, PersistentDataType.BOOLEAN, false)) return;
        pdc.set(Keys.INFERNAL_PICKED_UP, PersistentDataType.BOOLEAN, true);
    }

    /** 捡过东西的炒鸡怪，之后掉出的物品全部以无敌实体落地。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(EntityDropItemEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (!entity.getPersistentDataContainer()
                .getOrDefault(Keys.INFERNAL_PICKED_UP, PersistentDataType.BOOLEAN, false)) return;
        event.getItemDrop().setInvulnerable(true);
    }
}
