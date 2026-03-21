package com.infernalmobs.service;

import com.infernalmobs.util.Keys;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.Set;

/**
 * 魔法王套装削弱检测：根据玩家盔甲 PDC mczju:mi_id 判断是否削弱特定技能。
 * 不同装备削弱的技能不同，削弱效果见各技能实现。
 * <p>
 * magic_king_helmet   -> blinding, ender, confusing, vexsummoner, swap, morph
 * magic_king_chestplate -> poisonous, withering, lifesteal, molten, weakness, rust, 1up
 * magic_king_leggings -> thief, tosser, storm, vengeance, wardenwrath, archer, firework
 * magic_king_boots    -> quicksand, sapper, webber, gravity, refrigerate, ghastly, necromancer
 */
public class MagicKingArmorService {

    private static final Map<String, Set<String>> ARMOR_SKILLS = Map.of(
            "magic_king_helmet", Set.of("blinding", "ender", "confusing", "vexsummoner", "swap", "morph"),
            "magic_king_chestplate", Set.of("poisonous", "withering", "lifesteal", "molten", "weakness", "rust", "1up"),
            "magic_king_leggings", Set.of("thief", "tosser", "storm", "vengeance", "wardenwrath", "archer", "firework"),
            "magic_king_boots", Set.of("quicksand", "sapper", "webber", "gravity", "refrigerate", "ghastly", "necromancer")
    );

    /**
     * 玩家是否穿戴了能削弱该技能的魔法王盔甲。
     */
    public boolean isWeakened(Player player, String skillId) {
        if (player == null || skillId == null) return false;
        return hasResistingArmor(player, skillId);
    }

    private boolean hasResistingArmor(Player player, String skillId) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        if (armor == null) return false;
        String[] armorIds = {"magic_king_boots", "magic_king_leggings", "magic_king_chestplate", "magic_king_helmet"};
        for (int i = 0; i < armor.length && i < armorIds.length; i++) {
            ItemStack item = armor[i];
            if (item == null || item.getType() == Material.AIR) continue;
            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;
            if (!meta.getPersistentDataContainer().has(Keys.MI_ID, PersistentDataType.STRING)) continue;
            String miId = meta.getPersistentDataContainer().get(Keys.MI_ID, PersistentDataType.STRING);
            if (miId == null) continue;
            Set<String> skills = ARMOR_SKILLS.get(miId);
            if (skills != null && skills.contains(skillId)) return true;
        }
        return false;
    }
}
