package com.infernalmobs.skill.impl;

import com.infernalmobs.api.event.affix.triggered.InfernalMobRustEvent;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

/**
 * 锈蚀：受击时概率使玩家主手物品耐久降低。
 * 未损坏时播放配置的锈蚀提示音；物品损坏由标准耐久流程负责反馈。
 */
public class PassiveRustSkill implements Skill {

    @Override
    public String getId() {
        return "rust";
    }

    @Override
    public SkillType getType() {
        return SkillType.PASSIVE;
    }

    @Override
    public void onEquip(SkillContext ctx, SkillConfig config) {}

    @Override
    public void onUnequip(SkillContext ctx) {}

    @Override
    public void onTrigger(SkillContext ctx, SkillConfig config) {
        Player player = ctx.getTargetPlayer();
        if (player == null || !player.isOnline() || player.getGameMode() == GameMode.CREATIVE) return;

        double chance = config.getDouble("chance", 0.3);
        if (Math.random() >= chance) return;

        ItemStack mainBefore = player.getInventory().getItemInMainHand().clone();
        if (mainBefore.getType().isAir()) return;

        if (!(mainBefore.getItemMeta() instanceof Damageable)) return;

        int damageAmount = Math.max(0, config.getInt("damage-amount", 20));
        if (ctx.isWeakened() && damageAmount > 0) damageAmount = Math.max(1, damageAmount / 2);
        InfernalMobRustEvent event = new InfernalMobRustEvent(
                ctx.getEntity(), player, ctx.getHandle(),
                ctx.getMobState().getProfile().getLevel(), mainBefore, damageAmount);
        if (!ctx.fire(event) || event.getDamageAmount() == 0) return;

        ItemStack mainNow = player.getInventory().getItemInMainHand();
        if (!mainNow.equals(mainBefore)) return;

        player.damageItemStack(EquipmentSlot.HAND, event.getDamageAmount());
        ItemStack mainAfter = player.getInventory().getItemInMainHand();
        boolean broken = mainAfter.getType().isAir() || mainAfter.getAmount() < mainBefore.getAmount();
        if (broken) return;

        String soundKey = config.getString("sound", "ENTITY_ITEM_BREAK");
        float pitch = (float) config.getDouble("sound-pitch", 0.7);
        try {
            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundKey.toUpperCase().replace(".", "_"));
            player.getWorld().playSound(player.getLocation(), sound, 1f, pitch);
        } catch (IllegalArgumentException ignored) {}
    }
}
