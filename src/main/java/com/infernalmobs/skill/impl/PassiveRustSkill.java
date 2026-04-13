package com.infernalmobs.skill.impl;

import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

/**
 * 锈蚀：受击时概率使玩家主手物品耐久降低。
 * 物品损坏音效变调播放。
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

        ItemStack main = player.getInventory().getItemInMainHand();
        if (main.getType().isAir()) return;

        if (!(main.getItemMeta() instanceof Damageable damageable)) return;
        int maxDamage = main.getType().getMaxDurability();
        if (maxDamage <= 0) return;

        int damageAmount = config.getInt("damage-amount", 20);
        if (ctx.isWeakened()) damageAmount = Math.max(1, damageAmount / 2);
        int current = damageable.getDamage();
        int next = Math.min(current + damageAmount, maxDamage);
        damageable.setDamage(next);
        main.setItemMeta(damageable);

        if (next >= maxDamage) {
            player.getInventory().setItemInMainHand(ItemStack.empty());
        }

        String soundKey = config.getString("sound", "ENTITY_ITEM_BREAK");
        float pitch = (float) config.getDouble("sound-pitch", 1.5);
        try {
            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundKey.toUpperCase().replace(".", "_"));
            player.getWorld().playSound(player.getLocation(), sound, 1f, pitch);
        } catch (IllegalArgumentException ignored) {}
    }
}
