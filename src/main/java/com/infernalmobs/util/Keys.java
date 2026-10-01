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
    /** 炒鸡怪完整状态容器，类型为 TAG_CONTAINER。 */
    public static final NamespacedKey INFERNAL_MOB = key("infernal_mob");
    public static final NamespacedKey INFERNAL_DATA_VERSION = key("data-version");
    public static final NamespacedKey INFERNAL_LEVEL = key("level");
    public static final NamespacedKey INFERNAL_AFFIXES = key("affixes");
    public static final NamespacedKey INFERNAL_SUPPRESSED_AFFIXES = key("suppressed-affixes");
    public static final NamespacedKey INFERNAL_USED_ONE_TIME = key("used-one-time");
    public static final NamespacedKey INFERNAL_MORPH_TARGETS = key("morph-targets");

    /** 炒鸡物品稀有度，String，拥有该 PDC 时视为炒鸡物品（影响缴械效果） */
    public static final NamespacedKey IM_RARITY = key("im_rarity");
    /** 免疫缴械词条，Boolean，为 true 时无条件免疫缴械 */
    public static final NamespacedKey IM_THIEF_RESISTANCE = key("im_thief_resistance");

    public static final NamespacedKey FIREWORK_SOURCE = key("infernalmobs_firework_source");
    public static final NamespacedKey FIREWORK_HANDLE_AFFIXES = key("infernalmobs_firework_handle_affixes");
    public static final NamespacedKey FIREWORK_HANDLE_SUPPRESSED = key("infernalmobs_firework_handle_suppressed");
    public static final NamespacedKey FIREWORK_HANDLE_DISPLAY_NAME = key("infernalmobs_firework_handle_display_name");
    public static final NamespacedKey FIREWORK_LEVEL = key("infernalmobs_firework_level");
    public static final NamespacedKey FIREWORK_SKILL_ID = key("infernalmobs_skill_id");
    public static final NamespacedKey STORM_SOURCE = key("infernalmobs_source");
    public static final NamespacedKey STORM_SKILL_ID = key("infernalmobs_storm_skill_id");
    public static final NamespacedKey STORM_HANDLE_AFFIXES = key("infernalmobs_storm_handle_affixes");
    public static final NamespacedKey STORM_HANDLE_SUPPRESSED = key("infernalmobs_storm_handle_suppressed");
    public static final NamespacedKey STORM_HANDLE_DISPLAY_NAME = key("infernalmobs_storm_handle_display_name");
    public static final NamespacedKey STORM_LEVEL = key("infernalmobs_storm_level");
    public static final NamespacedKey STORM_DAMAGE = key("infernalmobs_damage");
    public static final NamespacedKey GHASTLY_SOURCE = key("infernalmobs_source");
    public static final NamespacedKey GHASTLY_SKILL_ID = key("infernalmobs_ghastly_skill_id");
    public static final NamespacedKey GHASTLY_HANDLE_AFFIXES = key("infernalmobs_ghastly_handle_affixes");
    public static final NamespacedKey GHASTLY_HANDLE_SUPPRESSED = key("infernalmobs_ghastly_handle_suppressed");
    public static final NamespacedKey GHASTLY_HANDLE_DISPLAY_NAME = key("infernalmobs_ghastly_handle_display_name");
    public static final NamespacedKey GHASTLY_LEVEL = key("infernalmobs_ghastly_level");
    public static final NamespacedKey GHASTLY_DAMAGE = key("infernalmobs_ghastly_damage");
    public static final NamespacedKey GHASTLY_FIRE_TICKS = key("infernalmobs_ghastly_fire_ticks");
    public static final NamespacedKey NECROMANCER_SOURCE = key("infernalmobs_source");
    public static final NamespacedKey NECROMANCER_SKILL_ID = key("infernalmobs_necromancer_skill_id");
    public static final NamespacedKey NECROMANCER_HANDLE_AFFIXES = key("infernalmobs_necromancer_handle_affixes");
    public static final NamespacedKey NECROMANCER_HANDLE_SUPPRESSED = key("infernalmobs_necromancer_handle_suppressed");
    public static final NamespacedKey NECROMANCER_HANDLE_DISPLAY_NAME = key("infernalmobs_necromancer_handle_display_name");
    public static final NamespacedKey NECROMANCER_LEVEL = key("infernalmobs_necromancer_level");
    public static final NamespacedKey ARCHER_SKILL_ID = key("infernalmobs_archer_skill_id");
}
