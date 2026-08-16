package com.infernalmobs.skill.impl;

import com.infernalmobs.api.event.affix.triggered.InfernalMobGhostEvent;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.factory.MobFactory;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import com.infernalmobs.util.MiniMessageHelper;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

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

        ThreadLocalRandom random = ThreadLocalRandom.current();
        boolean evil = random.nextInt(3) == 1;

        // 皮甲：邪恶=黑色，普通=白色，随机保护附魔
        ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE, 1);
        LeatherArmorMeta chestMeta = (LeatherArmorMeta) chest.getItemMeta();
        if (chestMeta != null) {
            chestMeta.setColor(evil ? Color.BLACK : Color.WHITE);
            chest.setItemMeta(chestMeta);
        }
        chest.addUnsafeEnchantment(Enchantment.PROTECTION, random.nextInt(1, 11));

        // 头盔：邪恶=凋零头，普通=骷髅头
        ItemStack skull = new ItemStack(evil ? Material.WITHER_SKELETON_SKULL : Material.SKELETON_SKULL, 1);
        ItemMeta skullMeta = skull.getItemMeta();
        if (skullMeta != null) {
            skullMeta.displayName(MiniMessageHelper.deserialize("<white>Ghost Head"));
            skull.setItemMeta(skullMeta);
        }

        ItemStack mainHand = random.nextInt(5) == 0 ? new ItemStack(Material.STONE_HOE, 1) : null;
        double floatSpeed = config.getDouble("float-speed", 0.3);
        int summonLevel = Math.max(1, config.getInt("summon-level", 1));
        double maxHealth = config.getDouble("health", 40);
        List<String> skillIds = evil
                ? List.of("ender", "necromancer", "withering", "blinding")
                : List.of("ender", "ghastly", "sapper", "confusing");

        InfernalMobGhostEvent event = new InfernalMobGhostEvent(
                ctx.getEntity(), ctx.getTargetPlayer(), ctx.getHandle(),
                ctx.getMobState().getProfile().getLevel(), loc, summonLevel, maxHealth, floatSpeed,
                skull, chest, mainHand, skillIds);
        if (!ctx.fire(event)) return;

        Location spawnLocation = event.getSpawnLocation();
        if (spawnLocation.getWorld() == null) return;

        Zombie ghost = (Zombie) spawnLocation.getWorld().spawnEntity(spawnLocation, EntityType.ZOMBIE);
        ghost.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0));
        ghost.setCanPickupItems(false);
        ItemStack helmet = event.getHelmet();
        ItemStack chestplate = event.getChestplate();
        ItemStack eventMainHand = event.getMainHand();
        ghost.getEquipment().setHelmet(helmet);
        ghost.getEquipment().setChestplate(chestplate);
        ghost.getEquipment().setItemInMainHand(eventMainHand);
        if (helmet != null && !helmet.getType().isAir()) ghost.getEquipment().setHelmetDropChance(0);
        if (chestplate != null && !chestplate.getType().isAir()) ghost.getEquipment().setChestplateDropChance(0);
        if (eventMainHand != null && !eventMainHand.getType().isAir()) {
            ghost.getEquipment().setItemInMainHandDropChance(0);
        }

        if (event.getFloatSpeed() > 0.0) {
            ghostMove(ghost, ctx.getPlugin(), event.getFloatSpeed());
        }

        MobFactory factory = ctx.getMobFactory();
        if (factory != null && !event.getAffixIds().isEmpty()) {
            factory.mechanizeWithAffixes(
                    ghost, spawnLocation, event.getSummonLevel(), event.getAffixIds());
        }

        var maxHealthAttribute = ghost.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttribute != null) {
            maxHealthAttribute.setBaseValue(event.getMaxHealth());
            ghost.setHealth(Math.min(ghost.getMaxHealth(), event.getMaxHealth()));
        }
    }

    private void ghostMove(Entity entity, JavaPlugin plugin, double floatSpeed) {
        if (entity == null || !entity.isValid() || entity.isDead()) return;

        Vector v = entity.getLocation().getDirection().multiply(floatSpeed);
        entity.setVelocity(v);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> ghostMove(entity, plugin, floatSpeed), 2L);
    }
}
