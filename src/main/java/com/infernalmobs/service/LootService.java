package com.infernalmobs.service;

import com.infernalmobs.config.GuaranteedLootConfig;
import com.infernalmobs.config.LootConfig;
import com.infernalmobs.config.LootConfig.RewardEntry;
import com.infernalmobs.config.SpecialLootConfig;
import com.infernalmobs.util.MiniMessageHelper;
import io.mczju.mczjuitemcreator.api.ItemCreatorApi;
import com.infernalmobs.model.MobState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Item;
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
     * 与 {@link #onInfernalMobDeath(EntityDeathEvent, LivingEntity, MobState, int)} 一致：仅在会执行等级池加权抽取时
     * 调用 {@link LootConfig#rollDropTimes(int)}，否则返回 0。供保底进度与死亡掉落共用同一次 roll。
     */
    public int rollDeathLootTimes(int level) {
        if (!isEnabled()) return 0;
        List<RewardEntry> rewards = config.getRewardsForLevel(level);
        if (rewards.isEmpty()) return 0;
        List<RewardEntry> eligible = filterByRotation(rewards);
        if (eligible.isEmpty()) return 0;
        return config.rollDropTimes(level);
    }

    /**
     * 炒鸡怪死亡时：按等级表权重抽一条掉落；难打怪（ravager/warden 等）再额外掉落 special_loot。
     *
     * @param preRolledDropTimes 已由 {@link #rollDeathLootTimes(int)} 与保底共用的一次结果；传入 -1 则在内部单独 roll（不推荐）
     */
    public boolean onInfernalMobDeath(EntityDeathEvent event, LivingEntity entity, MobState mobState, int preRolledDropTimes) {
        boolean vanillaDropsCleared = false;
        // 1. 等级表按权重掉落（与普通炒鸡怪相同）
        if (isEnabled()) {
            List<RewardEntry> rewards = config.getRewardsForLevel(mobState.getProfile().getLevel());
            if (!rewards.isEmpty()) {
                List<RewardEntry> eligible = filterByRotation(rewards);
                if (!eligible.isEmpty()) {
                    if (config.isReplaceVanillaDrops()) {
                        event.getDrops().clear();
                        vanillaDropsCleared = true;
                    }
                    Player killer = entity.getKiller();
                    String playerName = killer != null ? killer.getName() : "";
                    int dropTimes = preRolledDropTimes >= 0 ? preRolledDropTimes : config.rollDropTimes(mobState.getProfile().getLevel());
                    for (int i = 0; i < dropTimes; i++) {
                        RewardEntry chosen = pickByWeight(eligible);
                        if (chosen == null) break;

                        ItemStack toDrop = null;
                        Optional<ItemStack> opt = itemCreatorApi.createItem(chosen.id, chosen.amount);
                        if (opt != null && opt.isPresent() && !opt.get().getType().isAir()) {
                            toDrop = opt.get().clone();
                        }

                        if (toDrop != null && !toDrop.getType().isAir()) {
                            dropInvulnerable(entity.getWorld().dropItemNaturally(entity.getLocation(), toDrop));
                            for (String cmd : chosen.commands) {
                                if (cmd == null || cmd.isEmpty()) continue;
                                String run = cmd.replace("{player}", playerName);
                                Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), run));
                            }
                            if (chosen.broadcast) {
                                broadcastLootDrop(chosen, playerName, mobState.getProfile().getLevel());
                            }
                        }
                    }
                }
            }
        }

        // 2. 难打怪物额外特殊战利品（独立于等级池，仅部分实体类型）
        dropSpecialLootIfApplicable(event, entity, mobState);
        return vanillaDropsCleared;
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
            dropInvulnerable(entity.getWorld().dropItemNaturally(entity.getLocation(), toDrop));
        }
    }

    /** 过滤轮换：仅保留无 rotation-set 或 rotation-set 集合包含当月激活套的项。 */
    private List<RewardEntry> filterByRotation(List<RewardEntry> rewards) {
        if (!config.isRotationEnable()) return rewards;
        int activeSet = (Calendar.getInstance().get(Calendar.MONTH) % config.getRotationSets()) + 1;
        List<RewardEntry> out = new ArrayList<>();
        for (RewardEntry e : rewards) {
            if (e.rotationSets == null || e.rotationSets.contains(activeSet)) out.add(e);
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
        return null;
    }

    private static RewardEntry pickByWeight(List<RewardEntry> rewards) {
        double total = 0;
        for (RewardEntry e : rewards) total += e.weight;
        if (total <= 0) return rewards.isEmpty() ? null : rewards.get(ThreadLocalRandom.current().nextInt(rewards.size()));
        double r = ThreadLocalRandom.current().nextDouble() * total;
        for (RewardEntry e : rewards) {
            r -= e.weight;
            if (r < 0) return e;
        }
        return rewards.get(rewards.size() - 1);
    }

    /**
     * 处理保底掉落：在怪物死亡位置掉落物品（无敌实体），并触发 loot config 中对应条目的命令和广播。
     * 保底掉落不受 replace-vanilla-drops 影响（始终以 dropItemNaturally 掉落在地）。
     * 若 loot config 中不存在匹配的 RewardEntry，仍然掉落物品，但不触发命令和广播。
     *
     * @param rule     触发的保底规则
     * @param entity   死亡的炒鸡怪
     * @param killer   击杀玩家（用于命令中的 {player} 占位）
     * @param level    怪物等级（用于广播模板）
     */
    public void processGuaranteedDrop(GuaranteedLootConfig.GuaranteedRule rule,
                                      LivingEntity entity, Player killer, int level) {
        if (itemCreatorApi == null) return;
        Optional<ItemStack> opt = itemCreatorApi.createItem(rule.itemId, rule.itemAmount);
        if (opt == null || opt.isEmpty() || opt.get().getType().isAir()) return;

        ItemStack toDrop = opt.get().clone();
        dropInvulnerable(entity.getWorld().dropItemNaturally(entity.getLocation(), toDrop));

        // 在当前怪等级的 loot 池里查找同名条目，获取命令和广播配置
        if (config != null) {
            RewardEntry entry = config.getRewardsForLevel(level).stream()
                    .filter(e -> rule.itemId.equals(e.id))
                    .findFirst().orElse(null);
            if (entry != null) {
                String playerName = killer != null ? killer.getName() : "";
                for (String cmd : entry.commands) {
                    if (cmd == null || cmd.isEmpty()) continue;
                    String run = cmd.replace("{player}", playerName);
                    Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), run));
                }
                if (entry.broadcast) {
                    broadcastLootDrop(entry, playerName, level);
                }
            }
        }
    }

    private static void dropInvulnerable(Item itemEntity) {
        if (itemEntity == null) return;
        itemEntity.setInvulnerable(true);
    }

    private void broadcastLootDrop(RewardEntry chosen, String playerName, int level) {
        String template = chosen.broadcastMessage;
        if (template == null || template.isEmpty()) {
            template = "<gold>恭喜欧皇 <yellow><player></yellow> <gold>获得了 <aqua><item></aqua><white>x<amount></white>!";
        }
        // 兼容 {player}/{item}/{amount}/{level} 写法
        template = template
                .replace("{player}", "<player>")
                .replace("{item}", "<item>")
                .replace("{amount}", "<amount>")
                .replace("{level}", "<level>");
        Component msg = MiniMessageHelper.deserialize(template,
                Placeholder.unparsed("player", playerName == null ? "未知玩家" : playerName),
                Placeholder.unparsed("item", config.getLootDisplayName(chosen.id)),
                Placeholder.unparsed("amount", String.valueOf(chosen.amount)),
                Placeholder.unparsed("level", String.valueOf(level)));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(msg);
        }
    }

}
