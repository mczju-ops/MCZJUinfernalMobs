package com.infernalmobs.skill.impl;

import com.infernalmobs.api.event.affix.equipped.InfernalMobArmouredEvent;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.Material;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 重甲：怪物诞生时生效。
 * 若可穿盔甲（僵尸、骷髅等）则穿钻石套；
 * 若意外收到非 Mob 实体则以抗性提升II兜底。
 */
public class StatArmouredSkill implements Skill {

    @Override
    public String getId() {
        return "armoured";
    }

    @Override
    public SkillType getType() {
        return SkillType.STAT;
    }

    @Override
    public void onEquip(SkillContext ctx, SkillConfig config) {
        int tier = Math.min(config.getInt("armor-tier", 3), 4); // 0=皮革 1=金 2=铁 3=钻石 4=下界合金
        int level = ctx.getMobState() != null ? ctx.getMobState().getProfile().getLevel() : 1;
        if (tier == 3 && level >= 11) tier = 4; // Lv11+ 钻石升为合金
        ItemStack[] armor = getArmorSet(tier);

        InfernalMobArmouredEvent event = new InfernalMobArmouredEvent(
                ctx.getEntity(), null, ctx.getOrCreateHandle(), level,
                armor[0], armor[1], armor[2], armor[3]);
        if (!ctx.fire(event)) return;

        if (ctx.getEntity() instanceof Mob mob) {
            var equipment = mob.getEquipment();
            equipment.setHelmet(event.getHelmet());
            equipment.setChestplate(event.getChestplate());
            equipment.setLeggings(event.getLeggings());
            equipment.setBoots(event.getBoots());
            equipment.setHelmetDropChance(0);
            equipment.setChestplateDropChance(0);
            equipment.setLeggingsDropChance(0);
            equipment.setBootsDropChance(0);
        } else {
            int duration = config.getDurationTicks("resistance-duration-ticks", -1);
            int amplifier = config.getInt("resistance-amplifier", 1);
            ctx.getEntity().addPotionEffect(new PotionEffect(
                    PotionEffectType.RESISTANCE, duration, amplifier, false, true));
        }
    }

    @Override
    public void onUnequip(SkillContext ctx) {}

    private static ItemStack[] getArmorSet(int tier) {
        Material[] materials = switch (tier) {
            case 0 -> new Material[]{Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS};
            case 1 -> new Material[]{Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS};
            case 2 -> new Material[]{Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS};
            case 4 -> new Material[]{Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS};
            default -> new Material[]{Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS};
        };
        return new ItemStack[]{
                new ItemStack(materials[0]),
                new ItemStack(materials[1]),
                new ItemStack(materials[2]),
                new ItemStack(materials[3])
        };
    }
}
