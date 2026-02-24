package com.infernalmobs.skill.impl;

import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.factory.MobFactory;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 亡灵：怪物死亡时召唤一只幽灵僵尸。
 * 参照 infernal_mobs：隐身、骷髅头/凋零头、染色皮甲、飘浮移动、固定技能组合。
 */
public class DeathGhostSkill implements Skill {

    @Override
    public String getId() {
        return "ghost";
    }

    @Override
    public SkillType getType() {
        return SkillType.DEATH;
    }

    @Override
    public void onEquip(SkillContext ctx, SkillConfig config) {}

    @Override
    public void onUnequip(SkillContext ctx) {}

    @Override
    public void onTrigger(SkillContext ctx, SkillConfig config) {
        Location loc = ctx.getEntity().getLocation();
        if (loc.getWorld() == null) return;

        boolean evil = new Random().nextInt(3) == 1;

        Zombie ghost = (Zombie) loc.getWorld().spawnEntity(loc, org.bukkit.entity.EntityType.ZOMBIE);
        ghost.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0));
        ghost.setCanPickupItems(false);

        // 皮甲：邪恶=黑色，普通=白色，随机保护附魔
        ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE, 1);
        LeatherArmorMeta chestMeta = (LeatherArmorMeta) chest.getItemMeta();
        if (chestMeta != null) {
            chestMeta.setColor(evil ? Color.BLACK : Color.WHITE);
            chest.setItemMeta(chestMeta);
        }
        chest.addUnsafeEnchantment(Enchantment.PROTECTION, new Random().nextInt(10) + 1);

        // 头盔：邪恶=凋零头，普通=骷髅头
        ItemStack skull = new ItemStack(evil ? Material.WITHER_SKELETON_SKULL : Material.SKELETON_SKULL, 1);
        ItemMeta skullMeta = skull.getItemMeta();
        if (skullMeta != null) {
            skullMeta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&fGhost Head"));
            skull.setItemMeta(skullMeta);
        }

        ghost.getEquipment().setHelmet(skull);
        ghost.getEquipment().setChestplate(chest);
        ghost.getEquipment().setHelmetDropChance(0);
        ghost.getEquipment().setChestplateDropChance(0);

        if (new Random().nextInt(5) == 0) {
            ghost.getEquipment().setItemInMainHand(new ItemStack(Material.STONE_HOE, 1));
            ghost.getEquipment().setItemInMainHandDropChance(0);
        }

        double floatSpeed = config.getDouble("float-speed", 0.3);
        ghostMove(ghost, ctx.getPlugin(), floatSpeed);

        List<String> skillIds = new ArrayList<>();
        skillIds.add("ender");
        if (evil) {
            skillIds.add("necromancer");
            skillIds.add("withering");
            skillIds.add("blinding");
        } else {
            skillIds.add("ghastly");
            skillIds.add("sapper");
            skillIds.add("confusing");
        }

        MobFactory factory = ctx.getMobFactory();
        if (factory != null) {
            int level = Math.max(1, config.getInt("summon-level", 1));
            factory.mechanizeWithAffixes(ghost, loc, level, skillIds);
        }

        double hp = config.getDouble("health", 40);
        if (ghost.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null) {
            ghost.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(hp);
            ghost.setHealth(hp);
        }
    }

    private void ghostMove(Entity entity, JavaPlugin plugin, double floatSpeed) {
        if (entity == null || !entity.isValid() || entity.isDead()) return;

        Vector v = entity.getLocation().getDirection().multiply(floatSpeed);
        entity.setVelocity(v);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> ghostMove(entity, plugin, floatSpeed), 2L);
    }
}
