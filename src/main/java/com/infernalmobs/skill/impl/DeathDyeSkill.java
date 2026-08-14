package com.infernalmobs.skill.impl;

import com.infernalmobs.api.dye.DyeSchemeRequest;
import com.infernalmobs.api.dye.DyeSchemeResult;
import com.infernalmobs.api.dye.InfernalDyeApi;
import com.infernalmobs.api.event.InfernalMobDyeEvent;
import com.infernalmobs.InfernalMobsPlugin;
import com.infernalmobs.config.DyeConfig;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import com.infernalmobs.util.Keys;
import com.infernalmobs.util.ItemCreatorBridge;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Dye 联动词条：
 * - 生成时优先向外部 Dye 插件请求方案并记录 schemeId。
 * - 死亡时按记录方案解析 itemId，调用 ICA 生成掉落。
 */
public class DeathDyeSkill implements Skill {
    private static final String DEFAULT_ITEM_ID = "infernal_dye";
    private static final String DEBUG_PREFIX = "[InfernalMobs:debug:dye] ";

    @Override
    public String getId() {
        return "dye";
    }

    @Override
    public SkillType getType() {
        return SkillType.DEATH;
    }

    @Override
    public void onEquip(SkillContext ctx, SkillConfig config) {
        if (ctx == null || ctx.getEntity() == null || config == null) return;

        debug(ctx, "onEquip start entity=" + entityTag(ctx) + " level=" + levelOf(ctx));
        InfernalDyeApi dyeApi = resolveDyeApi(ctx);
        if (dyeApi != null) {
            debug(ctx, "requestScheme trigger entity=" + entityTag(ctx)
                    + " level=" + levelOf(ctx)
                    + " affixes=" + affixIdsOf(ctx)
                    + " provider=" + dyeApi.getClass().getName());
            Optional<DyeSchemeResult> opt = dyeApi.requestScheme(buildRequest(ctx));
            if (opt.isPresent()) {
                storeScheme(ctx, opt.get());
                var pdc = ctx.getEntity().getPersistentDataContainer();
                debug(ctx, "requestScheme hit schemeId=" + pdc.get(Keys.IM_DYE_SCHEME_ID, PersistentDataType.STRING)
                        + " dropItemId=" + pdc.get(Keys.IM_DYE_DROP_ITEM_ID, PersistentDataType.STRING)
                        + " hex=" + pdc.get(Keys.IM_DYE_HEX, PersistentDataType.STRING));
                debug(ctx, "note: leather render is owned by external Dye plugin, InfernalMobs only stores scheme/id.");
                return;
            }
            debug(ctx, "requestScheme empty -> fallback to local pool");
        } else {
            debug(ctx, "dyeApi missing -> fallback to local pool");
        }

        // 回退：Dye 插件不可用/未返回时，使用本地 pool
        DyeConfig dyeConfig = resolveDyeConfig(ctx);
        LocalDyeEntry local = pickLocal(config, dyeConfig);
        if (local == null || local.itemId.isBlank()) {
            debug(ctx, "local fallback failed: no valid itemId");
            return;
        }
        ctx.getEntity().getPersistentDataContainer().set(Keys.IM_DYE_ID, PersistentDataType.STRING, local.itemId);
        ctx.getEntity().getPersistentDataContainer().set(Keys.IM_DYE_DROP_ITEM_ID, PersistentDataType.STRING, local.itemId);
        if (!local.hexColor.isBlank()) {
            ctx.getEntity().getPersistentDataContainer().set(Keys.IM_DYE_HEX, PersistentDataType.STRING, local.hexColor);
        }
        debug(ctx, "local fallback selected itemId=" + local.itemId + " hex=" + local.hexColor);
        debug(ctx, "note: local fallback does not render armor by itself; external Dye renderer is still required.");
    }

    @Override
    public void onUnequip(SkillContext ctx) {
        if (ctx == null || ctx.getEntity() == null) return;
        String schemeId = ctx.getEntity().getPersistentDataContainer().get(Keys.IM_DYE_SCHEME_ID, PersistentDataType.STRING);
        if (schemeId == null || schemeId.isBlank()) return;
        InfernalDyeApi dyeApi = resolveDyeApi(ctx);
        if (dyeApi != null) {
            dyeApi.unbindEntity(ctx.getEntity().getUniqueId());
            debug(ctx, "onUnequip unbindEntity schemeId=" + schemeId + " entity=" + entityTag(ctx));
        } else {
            debug(ctx, "onUnequip skip unbind: dyeApi missing schemeId=" + schemeId + " entity=" + entityTag(ctx));
        }
    }

    @Override
    public void onTrigger(SkillContext ctx, SkillConfig config) {
        if (ctx == null || ctx.getEntity() == null || config == null) return;

        DyeConfig dyeCfg = resolveDyeConfig(ctx);
        double chance = dyeCfg != null ? dyeCfg.deathChance() : config.getDouble("chance", 1.0);
        double roll = Math.random();
        if (chance <= 0 || roll >= chance) {
            debug(ctx, "onTrigger skip by chance chance=" + chance + " roll=" + roll + " entity=" + entityTag(ctx));
            return;
        }
        if (!ctx.fire(new InfernalMobDyeEvent(ctx.getEntity(), ctx.getTargetPlayer(), ctx.getHandle(), ctx.getMobState().getProfile().getLevel()))) return;

        String itemId = resolveDropItemId(ctx, config, dyeCfg);
        int amount = dyeCfg != null ? dyeCfg.dropAmount() : Math.max(1, config.getInt("amount", 1));
        if (itemId == null || itemId.isBlank()) {
            debug(ctx, "onTrigger abort: resolved empty itemId entity=" + entityTag(ctx));
            return;
        }

        if (!ItemCreatorBridge.isAvailable(ctx.getPlugin())) {
            debug(ctx, "onTrigger abort: ItemCreatorApi missing itemId=" + itemId + " entity=" + entityTag(ctx));
            return;
        }
        Optional<ItemStack> opt = ItemCreatorBridge.createItem(ctx.getPlugin(), itemId, amount);
        if (opt.isEmpty()) {
            debug(ctx, "onTrigger abort: ICA createItem empty itemId=" + itemId + " amount=" + amount);
            return;
        }

        ItemStack stack = opt.get().clone();
        if (stack.getType().isAir() || stack.getAmount() <= 0) {
            debug(ctx, "onTrigger abort: invalid stack type=" + stack.getType() + " amount=" + stack.getAmount());
            return;
        }
        ensureMagicItemId(stack, itemId);

        // 玩家击杀且正在聚合掉落时，收集进掉落表（由 InfernalMobDropEvent 统一落世界）；否则直接掉落
        java.util.List<ItemStack> collect = ctx.getCollectTo();
        if (collect != null) {
            collect.add(stack);
        } else {
            Item drop = ctx.getEntity().getWorld().dropItemNaturally(ctx.getEntity().getLocation(), stack);
            if (drop != null) drop.setInvulnerable(true);
        }
        debug(ctx, "onTrigger drop success itemId=" + itemId + " amount=" + stack.getAmount() + " entity=" + entityTag(ctx));
    }

    private static DyeSchemeRequest buildRequest(SkillContext ctx) {
        int level = ctx.getMobState() != null ? ctx.getMobState().getProfile().getLevel() : 1;
        List<String> affixIds = ctx.getMobState() != null
                ? ctx.getMobState().getProfile().getAffixes().stream().map(a -> a.getSkillId()).collect(Collectors.toList())
                : List.of();
        return new DyeSchemeRequest(
                ctx.getEntity().getUniqueId(),
                ctx.getEntity().getType(),
                ctx.getEntity().getLocation(),
                level,
                affixIds
        );
    }

    private static void storeScheme(SkillContext ctx, DyeSchemeResult r) {
        var pdc = ctx.getEntity().getPersistentDataContainer();
        if (!r.getSchemeId().isBlank()) pdc.set(Keys.IM_DYE_SCHEME_ID, PersistentDataType.STRING, r.getSchemeId());
        if (!r.getDropItemId().isBlank()) {
            pdc.set(Keys.IM_DYE_DROP_ITEM_ID, PersistentDataType.STRING, r.getDropItemId());
            pdc.set(Keys.IM_DYE_ID, PersistentDataType.STRING, r.getDropItemId());
        }
        if (!r.getHexColor().isBlank()) pdc.set(Keys.IM_DYE_HEX, PersistentDataType.STRING, r.getHexColor());
    }

    private static String resolveDropItemId(SkillContext ctx, SkillConfig config, DyeConfig dyeCfg) {
        var pdc = ctx.getEntity().getPersistentDataContainer();
        String direct = pdc.get(Keys.IM_DYE_DROP_ITEM_ID, PersistentDataType.STRING);
        if (direct != null && !direct.isBlank()) {
            debug(ctx, "resolveDropItemId from pdc.dropItemId=" + direct);
            return direct;
        }

        String schemeId = pdc.get(Keys.IM_DYE_SCHEME_ID, PersistentDataType.STRING);
        if (schemeId != null && !schemeId.isBlank()) {
            InfernalDyeApi dyeApi = resolveDyeApi(ctx);
            if (dyeApi != null) {
                Optional<String> resolved = dyeApi.resolveDropItemId(schemeId, ctx.getEntity().getUniqueId());
                if (resolved.isPresent() && !resolved.get().isBlank()) {
                    debug(ctx, "resolveDropItemId from dyeApi schemeId=" + schemeId + " itemId=" + resolved.get());
                    return resolved.get();
                }
                debug(ctx, "resolveDropItemId dyeApi miss schemeId=" + schemeId);
            } else {
                debug(ctx, "resolveDropItemId skip dyeApi: provider missing schemeId=" + schemeId);
            }
        }

        String old = pdc.get(Keys.IM_DYE_ID, PersistentDataType.STRING);
        if (old != null && !old.isBlank()) {
            debug(ctx, "resolveDropItemId from legacy pdc.im_dye_id=" + old);
            return old;
        }
        if (dyeCfg != null && dyeCfg.fallbackItemId() != null && !dyeCfg.fallbackItemId().isBlank()) {
            debug(ctx, "resolveDropItemId from dye.yml fallback=" + dyeCfg.fallbackItemId());
            return dyeCfg.fallbackItemId();
        }
        String fromSkillConfig = config.getString("item-id", DEFAULT_ITEM_ID);
        debug(ctx, "resolveDropItemId from skills.dye.item-id=" + fromSkillConfig);
        return fromSkillConfig;
    }

    private static LocalDyeEntry pickLocal(SkillConfig config, DyeConfig dyeCfg) {
        List<LocalDyeEntry> entries = new ArrayList<>();
        if (dyeCfg != null) {
            for (DyeConfig.Entry e : dyeCfg.pool()) {
                if (e == null || e.itemId() == null || e.itemId().isBlank()) continue;
                if (e.weight() <= 0) continue;
                entries.add(new LocalDyeEntry(e.itemId(), e.weight(), e.hex()));
            }
        }
        if (entries.isEmpty()) {
            List<Map<?, ?>> raw = config.getSection() != null ? config.getSection().getMapList("pool") : List.of();
            for (Map<?, ?> node : raw) {
                if (node == null) continue;
                String itemId = asString(node.get("item-id"));
                if (itemId.isBlank()) continue;
                double weight = asDouble(node.get("weight"), 1.0);
                if (weight <= 0) continue;
                String hex = asString(node.get("hex"));
                entries.add(new LocalDyeEntry(itemId, weight, hex));
            }
        }
        if (entries.isEmpty()) {
            if (dyeCfg != null) {
                return new LocalDyeEntry(dyeCfg.fallbackItemId(), 1.0, dyeCfg.fallbackHex());
            }
            return new LocalDyeEntry(config.getString("item-id", DEFAULT_ITEM_ID), 1.0, config.getString("hex", ""));
        }
        return pickByWeight(entries);
    }

    private static LocalDyeEntry pickByWeight(List<LocalDyeEntry> entries) {
        double total = 0;
        for (LocalDyeEntry e : entries) total += e.weight;
        if (total <= 0) return entries.get(ThreadLocalRandom.current().nextInt(entries.size()));
        double r = ThreadLocalRandom.current().nextDouble() * total;
        for (LocalDyeEntry e : entries) {
            r -= e.weight;
            if (r <= 0) return e;
        }
        return entries.get(entries.size() - 1);
    }

    private static String asString(Object obj) {
        return obj == null ? "" : String.valueOf(obj).trim();
    }

    private static double asDouble(Object obj, double def) {
        if (obj instanceof Number n) return n.doubleValue();
        if (obj != null) {
            try {
                return Double.parseDouble(String.valueOf(obj).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    private static void ensureMagicItemId(ItemStack stack, String itemId) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;
        var pdc = meta.getPersistentDataContainer();
        String existed = pdc.get(Keys.MI_ID, PersistentDataType.STRING);
        if (existed == null || existed.isBlank()) {
            pdc.set(Keys.MI_ID, PersistentDataType.STRING, itemId);
            stack.setItemMeta(meta);
        }
    }

    private static InfernalDyeApi resolveDyeApi(SkillContext ctx) {
        if (ctx == null || ctx.getPlugin() == null) return null;
        var rsp = ctx.getPlugin().getServer().getServicesManager().getRegistration(InfernalDyeApi.class);
        return rsp != null ? rsp.getProvider() : null;
    }

    private static DyeConfig resolveDyeConfig(SkillContext ctx) {
        if (ctx == null || ctx.getPlugin() == null) return null;
        if (ctx.getPlugin() instanceof InfernalMobsPlugin im) {
            return im.getDyeConfig();
        }
        return null;
    }

    private static void debug(SkillContext ctx, String msg) {
        if (!(ctx != null && ctx.getPlugin() instanceof InfernalMobsPlugin im)) return;
        if (!im.getConfigLoader().isDebug()) return;
        im.getLogger().info(DEBUG_PREFIX + msg);
    }

    private static String entityTag(SkillContext ctx) {
        if (ctx == null || ctx.getEntity() == null) return "null";
        return ctx.getEntity().getType() + "@" + ctx.getEntity().getUniqueId();
    }

    private static int levelOf(SkillContext ctx) {
        return ctx != null && ctx.getMobState() != null ? ctx.getMobState().getProfile().getLevel() : -1;
    }

    private static List<String> affixIdsOf(SkillContext ctx) {
        if (ctx == null || ctx.getMobState() == null || ctx.getMobState().getProfile() == null) return List.of();
        return ctx.getMobState().getProfile().getAffixes().stream()
                .map(a -> a.getSkillId())
                .collect(Collectors.toList());
    }

    private static final class LocalDyeEntry {
        private final String itemId;
        private final double weight;
        private final String hexColor;

        private LocalDyeEntry(String itemId, double weight, String hexColor) {
            this.itemId = itemId;
            this.weight = weight;
            this.hexColor = hexColor != null ? hexColor : "";
        }
    }
}
