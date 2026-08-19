package com.infernalmobs.controller.listener;

import com.infernalmobs.api.event.affix.InfernalAffixAttemptEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * morph 词条禁用监听：当 morph 词条已被 morph_controller 禁用时，
 * 取消 morph 的 {@link InfernalAffixAttemptEvent}，使变身不进入条件与概率判定。
 * 禁用状态由事件 handle 携带（CombatService 构造事件时写入 suppressedAffixes），
 * 外部插件也可监听同一事件获知“已禁用/已取消”。
 */
public class MorphSuppressListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAffixAttempt(InfernalAffixAttemptEvent event) {
        if (!"morph".equals(event.getAffixId())) return;
        if (event.getHandle().isAffixSuppressed("morph")) {
            event.setCancelled(true);
        }
    }
}
