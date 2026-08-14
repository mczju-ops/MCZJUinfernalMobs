package com.infernalmobs.skill.impl;

import com.infernalmobs.api.InfernalMobHandle;
import com.infernalmobs.api.event.InfernalMobThiefEvent;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.controller.listener.ThiefResistanceListener;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import com.infernalmobs.util.Keys;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.util.Vector;
/**
 * 盗贼：受击或攻击时，玩家主手物品掉落在怪物身后。
 * - 炒鸡物品（PDC mczju:im_rarity 存在）：按 infernal-steal-chance 概率触发缴械。
 * - 非炒鸡普通物品：按 steal-chance 概率触发缴械（默认 10%）。
 * 触发前存主手快照，延迟后再读并对比：若主手物品已消失（如耐久为 0 被消耗），则不执行缴械。
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
        // 免疫缴械（PDC im_thief_resistance）已由 ThiefResistanceListener 在事件层取消，此处不再重复判定
        // 按物品类型分别取概率：优先读取特定键，缺省回退到通用的 "chance"
        double baseChance = config.getDouble("chance", 0.10);
        double chance = isInfernalItem(mainBefore)
            ? config.getDouble("infernal-steal-chance", baseChance)
            : config.getDouble("steal-chance", baseChance);
        if (Math.random() >= chance) return;
        if (ctx.isWeakened() && Math.random() < 0.5) return;  // 削弱: 概率再减小50%
        ctx.setTriggered(true);  // 概率判定通过：告知框架“已触发”，冷却改为成功后才扣

        // 掉落坐标用触发时怪物位置，延迟任务内不再用 ctx.getEntity()。这样与变身同时触发时，原实体被移除、新实体同位置生成，掉落仍落在“怪物处”正确位置
        Location mobLoc = ctx.getEntity().getLocation().clone();
        String soundKey = config.getString("sound", "ENTITY_WIND_CHARGE_THROW");
        String lineParticleKey = config.getString("line-particle", "REDSTONE");
        int cooldownTicks = config.getInt("cooldown-ticks", 80);
        final long triggerTick = ctx.getCurrentTick();

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;
                ItemStack mainNow = player.getInventory().getItemInMainHand();
                // 对比：主手物品消失了（被消耗/破损）则不执行缴械；主手还在则照常缴械
                if (mainNow.getType().isAir()) return;
                // 延迟到执行时再次判断，避免玩家在 1 tick 内换上了免疫物品
                if (ThiefResistanceListener.isResistant(mainNow)) return;

                // 缴械真正发生时 call 专属事件：外部可改掉落位置 / 最终冷却 / 取消本次缴械
                InfernalMobThiefEvent event = new InfernalMobThiefEvent(
                        ctx.getEntity(), ctx.getHandle(), ctx.getMobState().getProfile().getLevel(),
                        player, mainNow.clone(), mobLoc.clone().add(0, 0.5, 0), cooldownTicks);
                ctx.getPlugin().getServer().getPluginManager().callEvent(event);
                if (event.isCancelled()) return;
                ctx.getMobState().setCooldown("thief", triggerTick + event.getCooldownTicks());

                Location dropAt = event.getDropLocation();
                // 画线：玩家眼睛 -> 掉落点（方便看清缴械触发方向）
                spawnLineParticles(lineParticleKey, player.getEyeLocation(), dropAt, mobLoc);

                player.getInventory().setItemInMainHand(ItemStack.empty());
                if (mobLoc.getWorld() != null) {
                    var dropped = mobLoc.getWorld().dropItemNaturally(dropAt, event.getItemStack().clone());
                    if (dropped != null) dropped.setInvulnerable(true);
                    try {
                        Sound sound = Sound.valueOf(soundKey.toUpperCase().replace(".", "_"));
                        mobLoc.getWorld().playSound(mobLoc, sound, 0.8f, 0.6f);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }.runTaskLater(ctx.getPlugin(), 1L);
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

    private void spawnLineParticles(String particleKey, Location from, Location to, Location anchor) {
        if (anchor == null || anchor.getWorld() == null || from == null || to == null) return;
        Particle p = parseParticle(particleKey, "SPELL_WITCH");
        if (p == null) return;
        var world = anchor.getWorld();
        Vector dir = to.toVector().add(new Vector(0, 0.3, 0)).subtract(from.toVector()).normalize();
        double dist = from.distance(to);
        double step = 0.16; // 更密，线更明显
        int count = (int) Math.max(1, dist / step);
        for (int i = 0; i <= count; i++) {
            Location loc = from.clone().add(dir.clone().multiply(i * step));
            // 每个采样点多喷几个粒子，提高可见度
            world.spawnParticle(p, loc, 4, 0.03, 0.03, 0.03, 0);
        }
    }

    private static Particle parseParticle(String key, String fallbackKey) {
        if (key != null && !key.isEmpty()) {
            try {
                return Particle.valueOf(key.toUpperCase().replace(".", "_"));
            } catch (IllegalArgumentException ignored) {}
        }
        try {
            return Particle.valueOf(fallbackKey.toUpperCase().replace(".", "_"));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
