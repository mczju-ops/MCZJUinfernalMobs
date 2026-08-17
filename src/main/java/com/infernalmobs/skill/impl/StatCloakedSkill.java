package com.infernalmobs.skill.impl;

import com.infernalmobs.api.event.affix.equipped.InfernalMobCloakedEvent;
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
 * 潜行：怪物诞生时获得隐身效果。若为 Mob，头盔栏装备玻璃瓶。
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

        InfernalMobCloakedEvent event = new InfernalMobCloakedEvent(
                ctx.getEntity(), null, ctx.getOrCreateHandle(), ctx.getMobState().getProfile().getLevel(),
                duration, ItemStack.of(Material.GLASS_BOTTLE));
        if (!ctx.fire(event)) return;
        ctx.getEntity().addPotionEffect(new PotionEffect(
                PotionEffectType.INVISIBILITY, event.getDurationTicks(), 0, false, true));

        if (ctx.getEntity() instanceof Mob mob) {
            var equipment = mob.getEquipment();
            equipment.setHelmet(event.getHelmet());
            equipment.setHelmetDropChance(0);
        }
    }

    @Override
    public void onUnequip(SkillContext ctx) {}
}
