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
    /** 炒鸡道具 ID，String，用于识别全知之眼等炒鸡道具 */
    public static final NamespacedKey IM_ITEM_ID = key("infernal_item");
    /** 炒鸡物品稀有度，String */
    public static final NamespacedKey IM_RARITY = key("im_rarity");
    /** 免疫缴械词条，Boolean */
    public static final NamespacedKey IM_THIEF_RESISTANCE = key("im_thief_resistance");
    /** 位移免疫过期 tick，Integer（Bukkit#getCurrentTick 基准） */
    public static final NamespacedKey IM_DISPLACEMENT_IMMUNITY_EXPIRES_AT = key("displacement_immunity_expires_at");
    /** ender 技能禁用过期 tick，Integer（Bukkit#getCurrentTick 基准） */
    public static final NamespacedKey IM_ENDER_SUPPRESSED_EXPIRES_AT = key("ender_suppressed_expires_at");
    /** dye 词条抽中的染料 ID，String */
    public static final NamespacedKey IM_DYE_ID = key("im_dye_id");
    /** dye 词条抽中的方案 ID，String（由外部 Dye 插件分配） */
    public static final NamespacedKey IM_DYE_SCHEME_ID = key("im_dye_scheme_id");
    /** dye 词条死亡掉落物品 ID，String（用于 ICA createItem） */
    public static final NamespacedKey IM_DYE_DROP_ITEM_ID = key("im_dye_drop_item_id");
    /** dye 词条抽中的颜色十六进制（如 #55FFAA），String */
    public static final NamespacedKey IM_DYE_HEX = key("im_dye_hex");

    // === MCZJUMagicItems 数据 ===
    /** 内置 ID，String，ItemCreator 的 magicItemId 写入此键 */
    public static final NamespacedKey MI_ID = key("mi_id");
    /** 版本号，int */
    public static final NamespacedKey MI_VERSION = key("mi_version");
    /** 冷却(ms)，long */
    public static final NamespacedKey MI_CD = key("mi_cd");
    /** 剩余使用次数，int */
    public static final NamespacedKey MI_USES = key("mi_uses");
}
