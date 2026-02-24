package com.infernalmobs.controller.listener;

import org.bukkit.entity.Creeper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * 苦力怕爆炸时清空其身上的药水效果（避免带药效的炒鸡怪苦力怕爆炸时效果过于异常）。
 */
public class CreeperExplodeListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCreeperExplode(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof Creeper creeper)) return;
        creeper.clearActivePotionEffects();
    }
}
