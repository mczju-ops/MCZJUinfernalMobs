package com.infernalmobs.util;

import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * 位移免疫判定工具：
 * - 当 currentTick < expiresAt 时，视为位移免疫生效；
 * - 当 currentTick >= expiresAt 时，不免疫并自动清理该键。
 */
public final class DisplacementImmunityHelper {

    private DisplacementImmunityHelper() {}

    public static boolean isImmuneAndCleanup(Player player, long currentTick) {
        if (player == null) return false;
        Integer expiresAt = player.getPersistentDataContainer()
                .get(Keys.IM_DISPLACEMENT_IMMUNITY_EXPIRES_AT, PersistentDataType.INTEGER);
        if (expiresAt == null) return false;

        if (currentTick < expiresAt) return true;
        if (currentTick >= expiresAt) {
            player.getPersistentDataContainer().remove(Keys.IM_DISPLACEMENT_IMMUNITY_EXPIRES_AT);
        }
        return false;
    }
}
