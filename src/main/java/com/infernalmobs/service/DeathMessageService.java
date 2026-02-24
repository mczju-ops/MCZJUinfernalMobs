package com.infernalmobs.service;

import com.infernalmobs.config.ConfigLoader;
import com.infernalmobs.config.DeathMessageConfig;
import com.infernalmobs.model.MobState;
import org.bukkit.ChatColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Random;

/**
 * 击杀播报服务。当玩家击杀炒鸡怪时，从配置中随机选取消息模板并广播。
 */
public class DeathMessageService {

    private final ConfigLoader config;
    private final Random random = new Random();

    public DeathMessageService(ConfigLoader config) {
        this.config = config;
    }

    /**
     * 若配置启用且击杀者为玩家，则广播击杀消息。
     */
    public void broadcastIfEnabled(LivingEntity entity, MobState mobState, Player killer) {
        DeathMessageConfig dm = config.getDeathMessageConfig();
        if (dm == null || !dm.enable() || killer == null) return;

        String playerName = killer.getName();
        String mobDisplay = buildMobDisplayName(entity, mobState, dm);
        String weapon = getWeaponDisplay(killer, dm.defaultWeapon());

        String template = pickRandom(dm.messages());
        String msg = template
                .replace("<player>", playerName)
                .replace("<mob>", mobDisplay)
                .replace("<weapon>", weapon);

        String colored = ChatColor.translateAlternateColorCodes('&', msg);
        entity.getWorld().getPlayers().forEach(p -> p.sendMessage(colored));
    }

    private String buildMobDisplayName(LivingEntity entity, MobState mobState, DeathMessageConfig dm) {
        int level = mobState.getProfile().getLevel();
        String levelPrefix = dm.getLevelPrefix(level);
        String namePrefix = dm.namePrefix();
        String mobName = dm.getMobDisplayName(entity.getType());
        String raw = "&fLv " + level + " " + levelPrefix + " " + mobName;
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    private String getWeaponDisplay(Player killer, String defaultWeapon) {
        ItemStack hand = killer.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) return defaultWeapon;
        ItemMeta meta = hand.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', meta.getDisplayName()));
        }
        return formatMaterial(hand.getType().name());
    }

    private String formatMaterial(String key) {
        return key.toLowerCase().replace('_', ' ');
    }

    private String pickRandom(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        return list.get(random.nextInt(list.size()));
    }
}
