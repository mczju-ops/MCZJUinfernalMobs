package com.infernalmobs.controller.listener;

import com.infernalmobs.api.event.InfernalAffixTriggerEvent;
import com.infernalmobs.util.Keys;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * 免疫缴械（PDC mczju:im_thief_resistance）监听。
 * 玩家主手物品携带该 PDC 时，取消 thief 词条的 {@link InfernalAffixTriggerEvent}，
 * 使缴械不生效。免疫逻辑从技能内部移到事件层，外部插件也可监听同一事件获知“已免疫/已取消”。
 */
public class ThiefResistanceListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAffixTrigger(InfernalAffixTriggerEvent event) {
        if (!"thief".equals(event.getAffixId())) return;
        if (!(event.getTarget() instanceof Player player)) return;
        if (isResistant(player.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    /**
     * 免疫缴械标记：PDC mczju:im_thief_resistance = 1b/true（兼容字符串 "1"/"true"）。
     * 供监听器与技能延迟校验共用，保证判定逻辑唯一。
     */
    public static boolean isResistant(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        Byte b = meta.getPersistentDataContainer().get(Keys.IM_THIEF_RESISTANCE, PersistentDataType.BYTE);
        if (b != null) return b != 0;
        String s = meta.getPersistentDataContainer().get(Keys.IM_THIEF_RESISTANCE, PersistentDataType.STRING);
        return s != null && ("1".equals(s) || "true".equalsIgnoreCase(s));
    }
}
