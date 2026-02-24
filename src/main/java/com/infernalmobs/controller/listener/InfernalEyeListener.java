package com.infernalmobs.controller.listener;

import com.infernalmobs.config.ConfigLoader;
import com.infernalmobs.model.MobState;
import com.infernalmobs.service.CombatService;
import com.infernalmobs.util.Keys;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * 全知之眼：右键射线判定瞄准实体，若为炒鸡怪则显示其技能。
 * 通过 PDC infernal_item 或 mi_id=infernal_eye 识别物品（兼容 ItemCreator magicItemId）。
 */
public class InfernalEyeListener implements Listener {

    private static final String INFERNAL_EYE_ID = "infernal_eye";
    private static final double RAY_DISTANCE = 20;

    private final ConfigLoader configLoader;
    private final CombatService combatService;

    public InfernalEyeListener(ConfigLoader configLoader, CombatService combatService) {
        this.configLoader = configLoader;
        this.combatService = combatService;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        var item = event.getItem();
        if (item == null || item.getType().isAir()) return;

        var pdc = item.getPersistentDataContainer();
        String itemId = pdc.getOrDefault(Keys.IM_ITEM_ID, PersistentDataType.STRING, "");
        if (itemId.isEmpty()) itemId = pdc.getOrDefault(Keys.MI_ID, PersistentDataType.STRING, "");
        if (!INFERNAL_EYE_ID.equals(itemId)) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_EYE_DEATH, 1f, 1f);
        LivingEntity target = raycastTarget(player);
        if (target == null) {
            player.sendMessage(ChatColor.GRAY + "未瞄准任何生物");
            return;
        }

        MobState mobState = combatService.getMobState(target.getUniqueId());
        if (mobState == null) {
            player.sendMessage(ChatColor.GRAY + target.getType().name() + " 不是炒鸡怪");
            return;
        }

        int level = mobState.getProfile().getLevel();
        var affixes = mobState.getProfile().getAffixes();
        List<String> skillDisplays = new ArrayList<>();
        for (var affix : affixes) {
            var sc = configLoader.getSkillConfig(affix.getSkillId());
            String display = sc != null ? ChatColor.translateAlternateColorCodes('&', sc.getDisplay()) : affix.getSkillId();
            skillDisplays.add(display);
        }
        String skillsStr = String.join(ChatColor.WHITE + ", ", skillDisplays);
        player.sendMessage(ChatColor.GOLD + "Lv" + level + " " + target.getType().name()
                + ChatColor.WHITE + " | " + skillsStr);
    }

    private LivingEntity raycastTarget(Player player) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection();
        World world = player.getWorld();

        RayTraceResult result = world.rayTraceEntities(
                eye, dir, RAY_DISTANCE, 1.5,
                e -> e instanceof LivingEntity && e != player
        );
        if (result == null) return null;
        Entity hit = result.getHitEntity();
        return hit instanceof LivingEntity le ? le : null;
    }
}
