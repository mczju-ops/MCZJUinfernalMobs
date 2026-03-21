package com.infernalmobs.service;

import com.infernalmobs.config.GuaranteedLootConfig;
import com.infernalmobs.config.GuaranteedLootConfig.GuaranteedRule;
import io.mczju.mczjuitemcreator.api.ItemCreatorApi;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 保底掉落服务：按规则累计击杀进度，达到阈值时发放奖励。
 */
public class GuaranteedLootService {

    private static final String PROGRESS_FILE = "guaranteed_loot_progress.yml";
    private static final String KEY_PLAYERS = "players";

    private final JavaPlugin plugin;
    private final File progressFile;
    private ItemCreatorApi itemCreatorApi;
    private GuaranteedLootConfig config;
    /** 玩家 id -> (规则 id -> 当前进度)，-1 表示已触发且不重置 */
    private final Map<String, Map<String, Integer>> progress = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    public GuaranteedLootService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.progressFile = new File(plugin.getDataFolder(), PROGRESS_FILE);
    }

    public void setConfig(GuaranteedLootConfig config) {
        this.config = config;
    }

    public void setItemCreatorApi(ItemCreatorApi api) {
        this.itemCreatorApi = api;
    }

    public void load() {
        progress.clear();
        dirty = false;
        if (!progressFile.exists()) return;
        var yml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(progressFile);
        var playersSec = yml.getConfigurationSection(KEY_PLAYERS);
        if (playersSec == null) return;
        for (String playerId : playersSec.getKeys(false)) {
            var ruleSec = playersSec.getConfigurationSection(playerId);
            if (ruleSec == null) continue;
            Map<String, Integer> byRule = new ConcurrentHashMap<>();
            for (String ruleId : ruleSec.getKeys(false)) {
                int v = ruleSec.getInt(ruleId, 0);
                byRule.put(ruleId, v);
            }
            if (!byRule.isEmpty()) progress.put(playerId, byRule);
        }
    }

    public void saveIfDirty() {
        if (!dirty) return;
        dirty = false;
        var yml = new org.bukkit.configuration.file.YamlConfiguration();
        var playersSec = yml.createSection(KEY_PLAYERS);
        for (Map.Entry<String, Map<String, Integer>> e : progress.entrySet()) {
            var ruleSec = playersSec.createSection(e.getKey());
            for (Map.Entry<String, Integer> re : e.getValue().entrySet()) {
                if (re.getValue() != 0) ruleSec.set(re.getKey(), re.getValue());
            }
        }
        try {
            yml.save(progressFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("保存 guaranteed_loot_progress.yml 失败: " + ex.getMessage());
        }
    }

    /**
     * 玩家击杀炒鸡怪时调用。若进度达到阈值则发放保底奖励。
     * @param playerId 玩家 UUID 字符串
     * @param level 怪物等级
     * @param killer 击杀者（用于发放物品，可 null 时掉落世界）
     * @param dropLocation 若无 killer 则在此位置掉落
     */
    public void onKill(String playerId, int level, Player killer, org.bukkit.Location dropLocation) {
        if (config == null || !config.isEnable() || config.getRules().isEmpty()) return;

        for (GuaranteedRule rule : config.getRules().values()) {
            if (!config.appliesToLevel(rule, level)) continue;

            int current = progress.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                    .getOrDefault(rule.id, 0);
            if (current < 0) continue;  // 已触发且不重置

            int next = current + 1;
            progress.get(playerId).put(rule.id, next);
            dirty = true;

            if (next >= rule.count) {
                if (rule.resetOnDrop) {
                    progress.get(playerId).put(rule.id, 0);
                } else {
                    progress.get(playerId).put(rule.id, -1);
                }
                giveGuaranteedItem(rule, killer, dropLocation);
            }
        }
    }

    private void giveGuaranteedItem(GuaranteedRule rule, Player killer, org.bukkit.Location dropLocation) {
        ItemStack item = createItem(rule.itemId, rule.itemAmount);
        if (item == null || item.getType().isAir()) return;

        if (killer != null && killer.isOnline()) {
            HashMap<Integer, ItemStack> overflow = killer.getInventory().addItem(item);
            if (!overflow.isEmpty()) {
                for (ItemStack left : overflow.values()) {
                    killer.getWorld().dropItemNaturally(killer.getLocation(), left);
                }
            }
        } else if (dropLocation != null && dropLocation.getWorld() != null) {
            dropLocation.getWorld().dropItemNaturally(dropLocation, item);
        }
    }

    private ItemStack createItem(String id, int amount) {
        if (id == null || id.isEmpty() || amount < 1) return null;
        if (itemCreatorApi != null) {
            var opt = itemCreatorApi.createItem(id, amount);
            if (opt != null && opt.isPresent() && !opt.get().getType().isAir()) {
                return opt.get();
            }
        }
        String upper = id.toUpperCase().replace(" ", "_");
        try {
            Material mat = Material.valueOf(upper);
            if (mat.isItem() && !mat.isAir()) {
                return new ItemStack(mat, amount);
            }
        } catch (IllegalArgumentException ignored) {}
        return null;
    }

    public void markDirty() {
        dirty = true;
    }
}
