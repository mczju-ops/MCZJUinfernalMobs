package com.infernalmobs.util;

import org.bukkit.NamespacedKey;

/**
 * 统一管理 NamespacedKey，用于 PDC 读写。
 * 与 ItemCreator、MCZJUMagicItems 等约定命名空间 "mczju"。
 */
public final class Keys {

    private static final String NAMESPACE = "mczju";

    private Keys() {}

    /** 统一创建 NamespacedKey（key 仅允许 a-z0-9_-./） */
    private static NamespacedKey key(String key) {
        return new NamespacedKey(NAMESPACE, key);
    }

    // === InfernalMobs 数据 ===
    /** 炒鸡物品稀有度，String，拥有该 PDC 时视为炒鸡物品（影响缴械效果） */
    public static final NamespacedKey IM_RARITY = key("im_rarity");
    /** 免疫缴械词条，Boolean，为 true 时无条件免疫缴械 */
    public static final NamespacedKey IM_THIEF_RESISTANCE = key("im_thief_resistance");
}
