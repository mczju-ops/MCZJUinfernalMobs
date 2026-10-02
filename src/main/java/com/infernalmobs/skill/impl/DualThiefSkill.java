package com.infernalmobs.skill.impl;

import com.infernalmobs.InfernalMobsPlugin;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import com.infernalmobs.util.Keys;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * 缴械：概率召唤悦灵信使追击玩家，命中后夺取玩家当时的主手物品并送回主人。
 * - 炒鸡物品（PDC mczju:im_rarity 存在）：按 infernal-steal-chance 概率触发缴械。
 * - 非炒鸡普通物品：按 steal-chance 概率触发缴械（默认 10%）。
 */
public class DualThiefSkill implements Skill {

    @Override
    public String getId() {
        return "thief";
    }

    @Override
    public SkillType getType() {
        return SkillType.DUAL;
    }

    @Override
    public void onEquip(SkillContext ctx, SkillConfig config) {}

    @Override
    public void onUnequip(SkillContext ctx) {}

    @Override
    public void onTrigger(SkillContext ctx, SkillConfig config) {
        Player player = ctx.getTargetPlayer();
        if (player == null || !player.isOnline() || player.getGameMode() == GameMode.CREATIVE) return;

        ItemStack mainBefore = player.getInventory().getItemInMainHand().clone();
        if (mainBefore.getType().isAir()) return;

        double baseChance = config.getDouble("chance", 0.10);
        double chance = isInfernalItem(mainBefore)
            ? config.getDouble("infernal-steal-chance", baseChance)
            : config.getDouble("steal-chance", baseChance);
        if (Math.random() >= chance) return;
        if (ctx.isWeakened() && Math.random() < 0.5) return;  // 削弱: 概率再减小50%

        if (!(ctx.getPlugin() instanceof InfernalMobsPlugin plugin)) return;
        int cooldownTicks = config.getInt("cooldown-ticks", 80);
        boolean launched = plugin.getThiefCourierTestService().launch(
                player, ctx.getEntity(), ctx.getMobState(), ctx.getOrCreateHandle(),
                ctx.getCurrentTick(), cooldownTicks);
        if (launched) ctx.commitCooldown(getId(), cooldownTicks);
    }

    /**
     * 炒鸡物品判定：PDC mczju:im_rarity 存在即视为炒鸡物品。
     * 注意：炒鸡物品并非“不可缴械”，只是走更高的 infernal-steal-chance 概率。
     */
    private static boolean isInfernalItem(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(Keys.IM_RARITY, PersistentDataType.STRING);
    }

}
