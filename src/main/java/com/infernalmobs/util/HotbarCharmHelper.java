package com.infernalmobs.util;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 快捷栏护符判定工具：
 * - 统计快捷栏(1~9)中指定 mi_id 的数量
 * - 根据数量映射抵抗率并判定是否抵抗本次技能触发
 */
public final class HotbarCharmHelper {

    private HotbarCharmHelper() {}

    public static int countHotbarByMiId(Player player, String miId) {
        if (player == null || miId == null || miId.isBlank()) return 0;
        int count = 0;
        for (int slot = 0; slot <= 8; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack == null || stack.getType().isAir()) continue;
            ItemMeta meta = stack.getItemMeta();
            if (meta == null) continue;
            String id = meta.getPersistentDataContainer().get(Keys.MI_ID, PersistentDataType.STRING);
            if (id != null && miId.equalsIgnoreCase(id.trim())) count++;
        }
        return count;
    }

    /**
     * 1/2/3+ 个护符分别为 30%/60%/100% 抵抗。
     */
    public static boolean resistedByGravityCharm(Player player) {
        int count = countHotbarByMiId(player, "gravity_charm");
        if (count <= 0) return false;
        if (count >= 3) return true;
        double chance = count == 2 ? 0.60 : 0.30;
        return ThreadLocalRandom.current().nextDouble() < chance;
    }
}

