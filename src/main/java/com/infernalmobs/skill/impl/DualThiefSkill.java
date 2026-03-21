package com.infernalmobs.skill.impl;

import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * 盗贼：受击或攻击时，玩家主手物品掉落在怪物身后。
 * 触发前存主手、副手快照，延迟后再读并对比：若主手物品已消失（如耐久为 0 被消耗），则不执行缴械；若主手物品还在，再照常缴械。
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

        // 触发前存主手（延迟后再对比：主手消失则不缴械）
        ItemStack mainBefore = player.getInventory().getItemInMainHand().clone();
        if (mainBefore.getType().isAir()) return;
        if (ctx.isWeakened() && Math.random() < 0.5) return;  // 削弱: 概率减小50%

        // 掉落坐标用触发时怪物位置，延迟任务内不再用 ctx.getEntity()。这样与变身同时触发时，原实体被移除、新实体同位置生成，掉落仍落在“怪物处”正确位置
        Location mobLoc = ctx.getEntity().getLocation().clone();
        String soundKey = config.getString("sound", "ENTITY_WIND_CHARGE_THROW");

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;
                ItemStack mainNow = player.getInventory().getItemInMainHand();
                // 对比：主手物品消失了（被消耗/破损）则不执行缴械；主手还在则照常缴械
                if (mainNow.getType().isAir()) return;
                // 主手物品还在，照常缴械（dropAt 基于触发时保存的 mobLoc，与是否变身无关）
                Vector away = player.getLocation().toVector().subtract(mobLoc.toVector()).setY(0).normalize();
                Location dropAt = mobLoc.clone().add(away.multiply(1.5)).add(0, 0.5, 0);

                player.getInventory().setItemInMainHand(ItemStack.empty());
                if (mobLoc.getWorld() != null) {
                    mobLoc.getWorld().dropItemNaturally(dropAt, mainNow.clone());
                    try {
                        org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundKey.toUpperCase().replace(".", "_"));
                        mobLoc.getWorld().playSound(mobLoc, sound, 0.8f, 0.6f);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }.runTaskLater(ctx.getPlugin(), 1L);
    }
}
