package com.infernalmobs.controller.listener;

import com.infernalmobs.util.Keys;
import org.bukkit.entity.AbstractCubeMob;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.persistence.PersistentDataType;

/** 清理由炒鸡方块类生物分裂产生的子代所继承的自定义名称。 */
public final class CubeMobSplitListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCubeMobSplit(EntityTransformEvent event) {
        if (event.getTransformReason() != EntityTransformEvent.TransformReason.SPLIT) return;
        if (!(event.getEntity() instanceof AbstractCubeMob source)) return;
        if (!source.getPersistentDataContainer().has(Keys.INFERNAL_MOB,
                PersistentDataType.TAG_CONTAINER)) return;

        for (Entity transformed : event.getTransformedEntities()) {
            if (!(transformed instanceof LivingEntity child)) continue;
            child.customName(null);
            child.setCustomNameVisible(false);
        }
    }
}
