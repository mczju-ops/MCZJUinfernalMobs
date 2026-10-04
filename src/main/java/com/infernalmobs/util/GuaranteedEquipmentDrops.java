package com.infernalmobs.util;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** 读取并处理实体装备栏中原版保证掉落的物品。 */
public final class GuaranteedEquipmentDrops {

    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HAND, EquipmentSlot.OFF_HAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private GuaranteedEquipmentDrops() {}

    /** 收集掉落概率至少为 100% 的装备副本。 */
    public static List<ItemStack> collect(LivingEntity entity) {
        List<ItemStack> result = new ArrayList<>();
        EntityEquipment equipment = entity == null ? null : entity.getEquipment();
        if (equipment == null) return result;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack item = item(equipment, slot);
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) continue;
            if (dropChance(equipment, slot) < 1.0f) continue;
            result.add(item.clone());
        }
        return result;
    }

    public static void drop(LivingEntity entity, Location location) {
        if (location == null || location.getWorld() == null) return;
        for (ItemStack item : collect(entity)) {
            location.getWorld().dropItemNaturally(location, item).setInvulnerable(true);
        }
    }

    private static ItemStack item(EntityEquipment equipment, EquipmentSlot slot) {
        return switch (slot) {
            case HAND -> equipment.getItemInMainHand();
            case OFF_HAND -> equipment.getItemInOffHand();
            case HEAD -> equipment.getHelmet();
            case CHEST -> equipment.getChestplate();
            case LEGS -> equipment.getLeggings();
            case FEET -> equipment.getBoots();
            default -> null;
        };
    }

    private static float dropChance(EntityEquipment equipment, EquipmentSlot slot) {
        return switch (slot) {
            case HAND -> equipment.getItemInMainHandDropChance();
            case OFF_HAND -> equipment.getItemInOffHandDropChance();
            case HEAD -> equipment.getHelmetDropChance();
            case CHEST -> equipment.getChestplateDropChance();
            case LEGS -> equipment.getLeggingsDropChance();
            case FEET -> equipment.getBootsDropChance();
            default -> 0.0f;
        };
    }
}
