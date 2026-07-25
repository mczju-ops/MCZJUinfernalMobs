package com.infernalmobs.skill.impl;

import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 蓄力后锁定方向，以长矛突刺玩家。
 * 仅对 enabled-holders 白名单中的怪物生效，不在白名单内则跳过。
 * 蓄力期间主手换矛、粒子环绕、冲刺时锁定方向高速突进并拖粒子尾迹。
 */
public class RangeSpearSkill implements Skill {

    @Override
    public String getId() {
        return "spear";
    }

    @Override
    public SkillType getType() {
        return SkillType.RANGE;
    }

    @Override
    public void onEquip(SkillContext ctx, SkillConfig config) {}

    @Override
    public void onUnequip(SkillContext ctx) {}

    @Override
    public void onTrigger(SkillContext ctx, SkillConfig config) {
        LivingEntity mob = ctx.getEntity();
        Player target = ctx.getTargetPlayer();
        if (mob == null || !mob.isValid() || target == null || !target.isOnline()) return;
        if (target.getGameMode() == GameMode.CREATIVE || target.getGameMode() == GameMode.SPECTATOR) return;

        Set<EntityType> holders = parseEntityTypeSet(config, "enabled-holders");
        if (!holders.isEmpty() && !holders.contains(mob.getType())) return;

        EntityEquipment equip = mob.getEquipment();
        if (equip == null) return;

        Vector direction = target.getLocation().toVector().subtract(mob.getLocation().toVector());
        direction.setY(0);
        if (direction.lengthSquared() < 0.01) return;
        direction.normalize();

        int chargeTicks = Math.max(1, config.getInt("charge-ticks", 48));
        int lungeTicks = Math.max(1, config.getInt("lunge-ticks", 30));
        int speedAmplifier = config.getInt("lunge-speed-amplifier", 4);
        int sharpnessLevel = Math.max(0, config.getInt("sharpness-level", 5));
        double hitRadius = config.getDouble("hit-radius", 1.5);

        ItemStack spearItem = createSpearItem(config.getString("item", "NETHERITE_SPEAR"), sharpnessLevel);
        ItemStack savedHand = equip.getItemInMainHand();
        equip.setItemInMainHand(spearItem);
        equip.setItemInMainHandDropChance(0f);

        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_RAVAGER_STUNNED, 1.0f, 2.0f);

        new BukkitRunnable() {
            private int tick;
            private boolean hit;
            private boolean lunged;

            @Override
            public void run() {
                if (!mob.isValid() || mob.isDead() || !target.isOnline() || target.isDead()) {
                    cleanup();
                    cancel();
                    return;
                }

                Vector trackingDir = target.getLocation().toVector()
                        .subtract(mob.getLocation().toVector()).setY(0);
                if (trackingDir.lengthSquared() < 0.01) trackingDir = mob.getLocation().getDirection().setY(0);
                trackingDir.normalize();

                if (tick < chargeTicks) {
                    mob.setVelocity(new Vector(0, mob.getVelocity().getY(), 0));
                    if (tick % 2 == 0) {
                        double angle = tick * 0.4;
                        Location ringLoc = mob.getLocation().add(
                                Math.cos(angle) * 1.2, mob.getHeight() * 0.6, Math.sin(angle) * 1.2);
                        mob.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, ringLoc, 1, 0, 0, 0, 0);
                    }
                    if (tick == chargeTicks / 2) {
                        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_RAVAGER_STUNNED, 1.0f, 2.0f);
                    }
                    tick++;
                    return;
                }

                if (!lunged) {
                    mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, lungeTicks, speedAmplifier,
                            false, false, true));
                    mob.getWorld().playSound(mob.getLocation(), Sound.ITEM_SPEAR_LUNGE_3, 1.0f, 2.0f);
                    lunged = true;
                }

                Vector facing = mob.getLocation().getDirection().setY(0);
                if (facing.lengthSquared() < 0.01) facing = trackingDir;
                Location trailLoc = mob.getLocation().add(
                        facing.clone().multiply(-0.6).setY(mob.getHeight() * 0.5));
                mob.getWorld().spawnParticle(Particle.CRIT, trailLoc, 2, 0.15, 0.1, 0.15, 0.02);

                if (!hit && mob.getLocation().distanceSquared(target.getLocation()) <= hitRadius * hitRadius) {
                    target.damage(8.0, mob);
                    hit = true;
                }

                tick++;
                if (tick >= chargeTicks + lungeTicks) {
                    mob.setVelocity(new Vector(0, mob.getVelocity().getY(), 0));
                    cleanup();
                    cancel();
                }
            }

            private void cleanup() {
                equip.setItemInMainHand(savedHand);
            }
        }.runTaskTimer(ctx.getPlugin(), 0L, 1L);
    }

    private ItemStack createSpearItem(String itemName, int sharpnessLevel) {
        try {
            Material material = Material.valueOf(itemName.trim().toUpperCase());
            if (material.isItem()) {
                ItemStack item = new ItemStack(material);
                if (sharpnessLevel > 0) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        meta.addEnchant(Enchantment.SHARPNESS, sharpnessLevel, true);
                        item.setItemMeta(meta);
                    }
                }
                return item;
            }
        } catch (IllegalArgumentException | NullPointerException ignored) {
        }
        return new ItemStack(Material.NETHERITE_SPEAR);
    }

    private Set<EntityType> parseEntityTypeSet(SkillConfig config, String... keys) {
        Set<EntityType> out = new HashSet<>();
        if (config == null || keys == null) return out;
        for (String key : keys) {
            if (key == null || key.isBlank()) continue;
            List<String> raw = config.getStringList(key);
            if (raw == null || raw.isEmpty()) continue;
            for (String s : raw) {
                if (s == null || s.isBlank()) continue;
                try {
                    out.add(EntityType.valueOf(s.trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return out;
    }

}
