package com.infernalmobs.service;

import com.infernalmobs.config.LootConfig;
import com.infernalmobs.config.LootConfig.RewardEntry;
import io.mczju.mczjuitemcreator.api.ItemCreatorApi;
import com.infernalmobs.model.MobState;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 炒鸡怪特殊掉落：按 loot.yml 的等级区间 + rewards（id/amount/weight/commands），权重抽取后调用 ItemCreatorApi.createItem 发放。
 */
public class LootService {

    private final JavaPlugin plugin;
    private final LootConfig config;
    private final ItemCreatorApi itemCreatorApi;

    public LootService(JavaPlugin plugin, LootConfig config, ItemCreatorApi itemCreatorApi) {
        this.plugin = plugin;
        this.config = config;
        this.itemCreatorApi = itemCreatorApi;
    }

    public boolean isEnabled() {
        return config != null && config.isEnable() && itemCreatorApi != null;
    }

    /**
     * 炒鸡怪死亡时：按等级取 rewards，权重抽一条，createItem 掉落并执行 commands。
     */
    public void onInfernalMobDeath(EntityDeathEvent event, LivingEntity entity, MobState mobState) {
        if (!isEnabled()) return;

        List<RewardEntry> rewards = config.getRewardsForLevel(mobState.getProfile().getLevel());
        if (rewards.isEmpty()) return;

        if (config.isReplaceVanillaDrops()) {
            event.getDrops().clear();
        }

        RewardEntry chosen = pickByWeight(rewards);
        if (chosen == null) return;

        ItemStack toDrop = null;
        Optional<ItemStack> opt = itemCreatorApi.createItem(chosen.id, chosen.amount);
        if (opt != null && opt.isPresent() && !opt.get().getType().isAir()) {
            toDrop = opt.get().clone();
        } else {
            toDrop = createVanillaItem(chosen.id, chosen.amount);
        }
        if (toDrop != null && !toDrop.getType().isAir()) {
            entity.getWorld().dropItemNaturally(entity.getLocation(), toDrop);
        }

        Player killer = entity.getKiller();
        String playerName = killer != null ? killer.getName() : "";
        for (String cmd : chosen.commands) {
            if (cmd == null || cmd.isEmpty()) continue;
            String run = cmd.replace("{player}", playerName);
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), run));
        }
    }

    /** ItemCreator 无此 id 时，尝试按原版 Material 名创建（如 iron_ingot、diamond）。 */
    private static ItemStack createVanillaItem(String id, int amount) {
        if (id == null || id.isEmpty() || amount < 1) return null;
        String upper = id.toUpperCase().replace(" ", "_");
        try {
            Material mat = Material.valueOf(upper);
            if (mat.isItem() && !mat.isAir()) {
                return new ItemStack(mat, amount);
            }
        } catch (IllegalArgumentException ignored) {}
        return null;
    }

    private static RewardEntry pickByWeight(List<RewardEntry> rewards) {
        int total = 0;
        for (RewardEntry e : rewards) total += e.weight;
        if (total <= 0) return rewards.isEmpty() ? null : rewards.get(ThreadLocalRandom.current().nextInt(rewards.size()));
        int r = ThreadLocalRandom.current().nextInt(total);
        for (RewardEntry e : rewards) {
            if (r < e.weight) return e;
            r -= e.weight;
        }
        return rewards.get(rewards.size() - 1);
    }
}
