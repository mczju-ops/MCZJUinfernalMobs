package com.infernalmobs.service;

import com.infernalmobs.config.LootConfig;
import com.infernalmobs.config.LootConfig.RewardEntry;
import com.infernalmobs.config.SpecialLootConfig;
import io.mczju.mczjuitemcreator.api.ItemCreatorApi;
import com.infernalmobs.model.MobState;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 炒鸡怪特殊掉落：按 loot.yml 的等级区间 + rewards（id/amount/weight/commands），权重抽取后调用 ItemCreatorApi.createItem 发放。
 * 难打怪物额外掉落：按 config special-loot 配置，概率 = rates × 等级，可大于 1 表示保底+概率额外。
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
     * 炒鸡怪死亡时：按等级表权重抽一条掉落；难打怪（ravager/warden 等）再额外掉落 special_loot。
     */
    public void onInfernalMobDeath(EntityDeathEvent event, LivingEntity entity, MobState mobState) {
        // 1. 等级表按权重掉落（与普通炒鸡怪相同）
        if (isEnabled()) {
            List<RewardEntry> rewards = config.getRewardsForLevel(mobState.getProfile().getLevel());
            if (!rewards.isEmpty()) {
                List<RewardEntry> eligible = filterByRotation(rewards);
                if (!eligible.isEmpty()) {
                    if (config.isReplaceVanillaDrops()) {
                        event.getDrops().clear();
                    }
                    RewardEntry chosen = pickByWeight(eligible);
                    if (chosen != null) {
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
                }
            }
        }

        // 2. 难打怪物额外特殊战利品（独立于等级池，仅部分实体类型）
        dropSpecialLootIfApplicable(event, entity, mobState);
    }

    /** 难打怪物额外特殊战利品：概率 = rate × 等级，可 >1 表示保底+小数概率额外。 */
    private void dropSpecialLootIfApplicable(EntityDeathEvent event, LivingEntity entity, MobState mobState) {
        SpecialLootConfig slc = config.getSpecialLootConfig();
        if (slc == null || !slc.enable() || slc.rates().isEmpty()) return;
        double rate = slc.getRate(entity.getType().name());
        if (rate <= 0) return;
        int level = Math.max(1, mobState.getProfile().getLevel());
        double prob = rate * level;
        int amount = rollSpecialLootAmount(prob);
        if (amount <= 0) return;
        ItemStack toDrop = createSpecialLootItem(slc.itemId(), amount);
        if (toDrop != null && !toDrop.getType().isAir()) {
            entity.getWorld().dropItemNaturally(entity.getLocation(), toDrop);
        }
    }

    /** 过滤轮换：仅保留无 rotation-set 或 rotation-set 等于当月激活套的项。 */
    private List<RewardEntry> filterByRotation(List<RewardEntry> rewards) {
        if (!config.isRotationEnable()) return rewards;
        int activeSet = (Calendar.getInstance().get(Calendar.MONTH) % config.getRotationSets()) + 1;
        List<RewardEntry> out = new ArrayList<>();
        for (RewardEntry e : rewards) {
            if (e.rotationSet == null || e.rotationSet == activeSet) out.add(e);
        }
        return out;
    }

    /** 按概率 roll：prob=8.8 → 80% 返回 9，20% 返回 8。 */
    private static int rollSpecialLootAmount(double prob) {
        if (prob <= 0) return 0;
        int floor = (int) Math.floor(prob);
        double frac = prob - floor;
        if (frac <= 0) return floor;
        return ThreadLocalRandom.current().nextDouble() < frac ? floor + 1 : floor;
    }

    private ItemStack createSpecialLootItem(String id, int amount) {
        if (id == null || id.isEmpty() || amount < 1) return null;
        if (itemCreatorApi != null) {
            Optional<ItemStack> opt = itemCreatorApi.createItem(id, amount);
            if (opt != null && opt.isPresent() && !opt.get().getType().isAir()) {
                return opt.get();
            }
        }
        return createVanillaItem(id, amount);
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
