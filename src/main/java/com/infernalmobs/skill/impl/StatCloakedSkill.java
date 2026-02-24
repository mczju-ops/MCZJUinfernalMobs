package com.infernalmobs.skill.impl;

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
 * 潜行：怪物诞生时获得隐身效果。若可穿盔甲，头盔栏装备玻璃瓶。
 */
public class StatCloakedSkill implements Skill {

    @Override
    public String getId() {
        return "cloaked";
    }

    @Override
    public SkillType getType() {
        return SkillType.STAT;
    }

    @Override
    public void onEquip(SkillContext ctx, SkillConfig config) {
        int duration = config.getDurationTicks("duration-ticks", -1);
        ctx.getEntity().addPotionEffect(new PotionEffect(
                PotionEffectType.INVISIBILITY, duration, 0, false, true));

        if (ctx.getEntity() instanceof Mob mob && mob.getEquipment() != null) {
            mob.getEquipment().setHelmet(new ItemStack(Material.GLASS_BOTTLE));
            mob.getEquipment().setHelmetDropChance(0);
        }
    }

    @Override
    public void onUnequip(SkillContext ctx) {}
}
