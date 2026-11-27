package com.infernalmobs.service;

import com.infernalmobs.api.InfernalMobHandle;
import com.infernalmobs.api.InfernalLootReward;
import com.infernalmobs.api.event.mob.InfernalMobDropEvent;
import com.infernalmobs.config.UltimateLootConfig.LootReward;
import com.infernalmobs.config.LootConfig;
import com.infernalmobs.config.LootConfig.RewardEntry;
import com.infernalmobs.config.SpecialLootConfig;
import com.infernalmobs.util.MiniMessageHelper;
import com.infernalmobs.util.ItemCreatorBridge;
import com.infernalmobs.model.MobState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 炒鸡怪掉落：使用已解析的 loot/settings.yml 与 loot/levels/ 快照执行等级池和特殊实体奖励。
 */
public class LootService {

    private static final String DEFAULT_BROADCAST_MESSAGE =
            "<gold>恭喜欧皇<green><player></green><gold>获得了<white><item></white>!";

    private final JavaPlugin plugin;
    private final LootConfig config;
    private final boolean itemCreatorAvailable;

    public LootService(JavaPlugin plugin, LootConfig config, boolean itemCreatorAvailable) {
        this.plugin = plugin;
        this.config = config;
        this.itemCreatorAvailable = itemCreatorAvailable;
    }

    public boolean isEnabled() {
        return config != null && config.isEnable() && itemCreatorAvailable;
    }

    /** 证章模块只要求物品插件可用，不依赖普通等级池总开关。 */
    public boolean isItemCreatorAvailable() {
        return itemCreatorAvailable;
    }

    public boolean isReplaceVanillaDrops() {
        return config != null && config.isReplaceVanillaDrops();
    }

    /** 获取掉落物品的配置显示名；未配置时返回物品 ID。 */
    public String getLootDisplayName(String itemId) {
        if (itemId == null || itemId.isBlank()) return "";
        Optional<ItemStack> item = ItemCreatorBridge.createItem(plugin, itemId, 1);
        return item.map(stack -> plainItemName(stack, itemId)).orElse(itemId);
    }

    /**
     * 仅在会执行等级池加权抽取时
     * 调用 {@link LootConfig#rollDropTimes(int)}，否则返回 0。供死亡掉落使用。
     */
    public int rollDeathLootTimes(int level) {
        if (!isEnabled()) return 0;
        if (getEligibleLevelRewards(level).isEmpty()) return 0;
        return config.rollDropTimes(level);
    }

    /** 按等级执行一次完整抽取，只返回物品，不执行奖励附带的命令或广播。 */
    public List<ItemStack> rollLevelLootItems(int level) {
        if (level < 1) return List.of();
        List<RolledReward> rolled = rollLevelRewards(level);
        if (rolled.isEmpty()) return List.of();

        List<ItemStack> items = new ArrayList<>(rolled.size());
        for (RolledReward reward : rolled) {
            items.add(reward.itemStack().clone());
        }
        return List.copyOf(items);
    }

    /** 按等级执行一次完整抽取，返回物品与原始奖励元数据，不执行命令或广播。 */
    public List<InfernalLootReward> rollLevelLootRewards(int level) {
        if (level < 1) return List.of();
        List<RolledReward> rolled = rollLevelRewards(level);
        if (rolled.isEmpty()) return List.of();

        List<InfernalLootReward> rewards = new ArrayList<>(rolled.size());
        for (RolledReward reward : rolled) {
            RewardEntry entry = reward.entry();
            rewards.add(new InfernalLootReward(
                    entry.id,
                    plainItemName(reward.itemStack(), entry.id),
                    reward.itemStack(),
                    entry.commands,
                    entry.broadcast,
                    resolveBroadcastMessage(entry)
            ));
        }
        return List.copyOf(rewards);
    }

    /**
     * 炒鸡怪死亡时：按等级表权重抽一条掉落；难打怪（ravager/warden 等）再额外掉落 special_loot。
     *
     * @param preRolledDropTimes 由 {@link #rollDeathLootTimes(int)} 得到并经事件调整的次数；传入 -1 则在内部单独 roll（不推荐）
     * @param collect            非 null 时改为收集到该列表（聚合掉落事件），否则直接掉落
     */
    public void onInfernalMobDeath(LivingEntity entity, MobState mobState,
                                   int preRolledDropTimes, List<ItemStack> collect) {
        // 1. 等级表按权重掉落（与普通炒鸡怪相同）
        if (isEnabled()) {
            int level = mobState.getProfile().getLevel();
            List<RewardEntry> eligible = getEligibleLevelRewards(level);
            if (!eligible.isEmpty()) {
                Player killer = entity.getKiller();
                String playerName = killer != null ? killer.getName() : "";
                int dropTimes = preRolledDropTimes >= 0
                        ? preRolledDropTimes
                        : config.rollDropTimes(level);
                for (RolledReward reward : rollRewards(eligible, dropTimes)) {
                    RewardEntry chosen = reward.entry();
                    collectDrop(collect, entity, reward.itemStack());
                    for (String cmd : chosen.commands) {
                        if (cmd == null || cmd.isEmpty()) continue;
                        String run = cmd.replace("{player}", playerName);
                        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), run));
                    }
                    if (chosen.broadcast) {
                        broadcastLootDrop(chosen, reward.itemStack(), playerName, level);
                    }
                }
            }
        }

        // 2. 难打怪物额外特殊战利品（独立于等级池，仅部分实体类型）
        dropSpecialLootIfApplicable(entity, mobState, collect);
    }

    /** 难打怪物额外特殊战利品：概率 = rate × 等级，可 >1 表示保底+小数概率额外。 */
    private void dropSpecialLootIfApplicable(LivingEntity entity, MobState mobState,
                                             List<ItemStack> collect) {
        // mama 生成的子怪仍参与等级池抽取，但跳过特殊实体额外掉落，避免通过增殖刷取特殊奖励。
        if (mobState.isMamaChild()) return;
        SpecialLootConfig slc = config.getSpecialLootConfig();
        if (!slc.enable() || slc.rates().isEmpty()) return;
        double rate = slc.getRate(entity.getType().name());
        if (rate <= 0) return;
        int level = Math.max(1, mobState.getProfile().getLevel());
        double prob = rate * level;
        int amount = rollSpecialLootAmount(prob);
        if (amount <= 0) return;
        ItemStack toDrop = createSpecialLootItem(slc.itemId(), amount);
        if (toDrop != null && !toDrop.getType().isAir()) {
            collectDrop(collect, entity, toDrop);
        }
    }

    /** 聚合时加入掉落表，否则直接以无敌实体掉落在世界。 */
    private static void collectDrop(List<ItemStack> collect, LivingEntity entity, ItemStack stack) {
        if (collect != null) {
            collect.add(stack);
        } else {
            dropInvulnerable(entity.getWorld().dropItemNaturally(entity.getLocation(), stack));
        }
    }

    /**
     * 聚合的死亡掉落统一落世界：先广播 {@link InfernalMobDropEvent}（监听器可追加 / 删除 / 取消），
     * 未被取消则逐个以无敌实体掉落在怪物位置。
     */
    public void flushDeathDrops(LivingEntity entity, MobState mobState, Player killer, List<ItemStack> drops) {
        if (drops == null || drops.isEmpty()) return;
        InfernalMobHandle handle = new InfernalMobHandle(entity,
                mobState.getProfile().getLevel(), mobState.getProfile().getAffixIds());
        InfernalMobDropEvent dropEvent = new InfernalMobDropEvent(
                entity, handle, mobState.getProfile().getLevel(), killer, drops);
        plugin.getServer().getPluginManager().callEvent(dropEvent);
        if (dropEvent.isCancelled()) return;
        for (ItemStack stack : dropEvent.getDrops()) {
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) continue;
            dropInvulnerable(entity.getWorld().dropItemNaturally(entity.getLocation(), stack));
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

    private List<RewardEntry> getEligibleLevelRewards(int level) {
        if (!isEnabled()) return List.of();
        List<RewardEntry> rewards = config.getRewardsForLevel(level);
        return rewards.isEmpty() ? List.of() : filterByRotation(rewards);
    }

    private List<RolledReward> rollLevelRewards(int level) {
        List<RewardEntry> eligible = getEligibleLevelRewards(level);
        if (eligible.isEmpty()) return List.of();
        return rollRewards(eligible, config.rollDropTimes(level));
    }

    private List<RolledReward> rollRewards(List<RewardEntry> eligible, int times) {
        if (eligible == null || eligible.isEmpty() || times <= 0) return List.of();
        List<RolledReward> result = new ArrayList<>(times);
        for (int i = 0; i < times; i++) {
            RewardEntry chosen = pickByWeight(eligible);
            if (chosen == null) break;
            Optional<ItemStack> created = ItemCreatorBridge.createItem(plugin, chosen.id, chosen.amount);
            if (created.isEmpty() || created.get().getType().isAir()) continue;
            result.add(new RolledReward(chosen, created.get().clone()));
        }
        return List.copyOf(result);
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
        Optional<ItemStack> opt = ItemCreatorBridge.createItem(plugin, id, amount);
        if (opt.isPresent() && !opt.get().getType().isAir()) {
            return opt.get();
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
        return rewards.getLast();
    }

    private record RolledReward(RewardEntry entry, ItemStack itemStack) {}

    /** 在怪物死亡位置执行证章抽取，证章掉落不受 replace-vanilla-drops 影响。 */
    public void processUltimateLootDrop(UltimateLootService.LootAward award,
                                 String playerId, LivingEntity entity, Player killer, int level,
                                 UltimateLootService lootService, List<ItemStack> collect) {
        LootReward reward = award.rule().reward;
        Optional<ItemStack> opt = ItemCreatorBridge.createItem(plugin, reward.itemId, award.amount());
        if (opt.isEmpty() || opt.get().getType().isAir()) return;

        ItemStack toDrop = opt.get().clone();
        collectDrop(collect, entity, toDrop);
        if (lootService != null && playerId != null) {
            lootService.recordObtained(playerId, award.rule().id, award.amount());
        }

        String playerName = killer != null ? killer.getName() : "";
        for (String cmd : reward.commands) {
            if (cmd == null || cmd.isEmpty()) continue;
            String run = cmd.replace("{player}", playerName);
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), run));
        }
        if (reward.broadcast) {
            broadcastUltimateLootDrop(reward, toDrop, playerName, level);
        }
    }

    private void broadcastUltimateLootDrop(LootReward reward, ItemStack stack, String playerName, int level) {
        String template = reward.broadcastMessage == null || reward.broadcastMessage.isBlank()
                ? DEFAULT_BROADCAST_MESSAGE : reward.broadcastMessage;
        template = template.replace("{player}", "<player>").replace("{item}", "<item>")
                .replace("{amount}", "<amount>").replace("{level}", "<level>");
        Component msg = MiniMessageHelper.deserialize(template,
                Placeholder.unparsed("player", playerName == null ? "未知玩家" : playerName),
                Placeholder.component("item", stack.displayName()),
                Placeholder.unparsed("amount", String.valueOf(stack.getAmount())),
                Placeholder.unparsed("level", String.valueOf(level)));
        for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(msg);
    }

    private static void dropInvulnerable(Item itemEntity) {
        if (itemEntity == null) return;
        itemEntity.setInvulnerable(true);
    }

    private void broadcastLootDrop(RewardEntry chosen, ItemStack itemStack, String playerName, int level) {
        String template = resolveBroadcastMessage(chosen);
        // 兼容 {player}/{item}/{amount}/{level} 写法
        template = template
                .replace("{player}", "<player>")
                .replace("{item}", "<item>")
                .replace("{amount}", "<amount>")
                .replace("{level}", "<level>");
        Component msg = MiniMessageHelper.deserialize(template,
                Placeholder.unparsed("player", playerName == null ? "未知玩家" : playerName),
                Placeholder.component("item", itemStack.displayName()),
                Placeholder.unparsed("amount", String.valueOf(itemStack.getAmount())),
                Placeholder.unparsed("level", String.valueOf(level)));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(msg);
        }
    }

    private static String resolveBroadcastMessage(RewardEntry reward) {
        return reward.broadcastMessage == null || reward.broadcastMessage.isEmpty()
                ? DEFAULT_BROADCAST_MESSAGE
                : reward.broadcastMessage;
    }

    private static String plainItemName(ItemStack itemStack, String fallback) {
        String name = PlainTextComponentSerializer.plainText().serialize(itemStack.displayName());
        return name.isBlank() ? fallback : name;
    }

}
