package com.infernalmobs.service;

import com.infernalmobs.api.InfernalMobHandle;
import com.infernalmobs.api.event.affix.effect.InfernalMobFireworkDamageEvent;
import com.infernalmobs.api.event.affix.effect.InfernalMobGhastlyDamageEvent;
import com.infernalmobs.api.event.affix.effect.InfernalMobNecromancerDamageEvent;
import com.infernalmobs.api.event.affix.effect.InfernalMobStormDamageEvent;
import com.infernalmobs.util.Keys;
import com.infernalmobs.util.PdcHandleCodec;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.WitherSkull;
import org.bukkit.NamespacedKey;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/** 处理特殊技能实体造成的伤害归因；不负责普通战斗触发。 */
public final class SpecialDamageService {

    private final JavaPlugin plugin;

    public SpecialDamageService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 将烟花爆炸伤害归因到释放它的炒鸡怪，并保留可取消/改伤害事件语义。 */
    public void handleFireworkDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Firework firework)) return;
        var pdc = firework.getPersistentDataContainer();
        LivingEntity mob = findSource(pdc, Keys.FIREWORK_SOURCE);
        if (mob == null || !mob.isValid()) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        Integer level = readInteger(pdc, Keys.FIREWORK_LEVEL);
        if (level == null || !hasSkill(pdc, Keys.FIREWORK_SKILL_ID, "firework")) return;
        InfernalMobHandle handle = PdcHandleCodec.read(pdc, mob, level);
        if (handle == null) return;

        double damage = event.getDamage();
        event.setCancelled(true);
        InfernalMobFireworkDamageEvent damageEvent = new InfernalMobFireworkDamageEvent(
                mob, victim, firework, handle, level, damage);
        plugin.getServer().getPluginManager().callEvent(damageEvent);
        if (damageEvent.isCancelled() || damageEvent.getDamage() <= 0.0) return;
        victim.damage(damageEvent.getDamage(), mob);
    }

    public void handleGhastlyDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Fireball fireball)) return;
        var pdc = fireball.getPersistentDataContainer();
        if (!hasSkill(pdc, Keys.GHASTLY_SKILL_ID, "ghastly")) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        LivingEntity mob = findSource(pdc, Keys.GHASTLY_SOURCE);
        if (mob == null || !mob.isValid()) return;
        Integer level = readInteger(pdc, Keys.GHASTLY_LEVEL);
        if (level == null) return;
        InfernalMobHandle handle = PdcHandleCodec.read(pdc, mob, level,
                Keys.GHASTLY_HANDLE_AFFIXES, Keys.GHASTLY_HANDLE_SUPPRESSED,
                Keys.GHASTLY_HANDLE_DISPLAY_NAME);
        if (handle == null) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.PROJECTILE) {
            Double damage = readDouble(pdc, Keys.GHASTLY_DAMAGE);
            if (damage != null) event.setDamage(Math.max(0.0, damage));
        }
        InfernalMobGhastlyDamageEvent damageEvent = new InfernalMobGhastlyDamageEvent(
                mob, victim, fireball, handle, level, event.getCause(), event.getDamage());
        plugin.getServer().getPluginManager().callEvent(damageEvent);
        if (damageEvent.isCancelled()) event.setCancelled(true);
        else event.setDamage(damageEvent.getDamage());
    }

    public void handleNecromancerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof WitherSkull skull)) return;
        var pdc = skull.getPersistentDataContainer();
        if (!hasSkill(pdc, Keys.NECROMANCER_SKILL_ID, "necromancer")) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        LivingEntity mob = findSource(pdc, Keys.NECROMANCER_SOURCE);
        if (mob == null || !mob.isValid()) return;
        Integer level = readInteger(pdc, Keys.NECROMANCER_LEVEL);
        if (level == null) return;
        InfernalMobHandle handle = PdcHandleCodec.read(pdc, mob, level,
                Keys.NECROMANCER_HANDLE_AFFIXES, Keys.NECROMANCER_HANDLE_SUPPRESSED,
                Keys.NECROMANCER_HANDLE_DISPLAY_NAME);
        if (handle == null) return;
        InfernalMobNecromancerDamageEvent damageEvent = new InfernalMobNecromancerDamageEvent(
                mob, victim, skull, handle, level, event.getCause(), event.getDamage());
        plugin.getServer().getPluginManager().callEvent(damageEvent);
        if (damageEvent.isCancelled()) event.setCancelled(true);
        else event.setDamage(damageEvent.getDamage());
    }

    public void handleStormDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LightningStrike lightning)) return;
        var pdc = lightning.getPersistentDataContainer();
        if (!hasSkill(pdc, Keys.STORM_SKILL_ID, "storm")) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        Double recordedDamage = readDouble(pdc, Keys.STORM_DAMAGE);
        if (recordedDamage == null) return;
        event.setDamage(Math.max(0.0, recordedDamage));
        LivingEntity mob = findSource(pdc, Keys.STORM_SOURCE);
        if (mob == null || !mob.isValid()) return;
        Integer level = readInteger(pdc, Keys.STORM_LEVEL);
        if (level == null) return;
        InfernalMobHandle handle = PdcHandleCodec.read(pdc, mob, level,
                Keys.STORM_HANDLE_AFFIXES, Keys.STORM_HANDLE_SUPPRESSED,
                Keys.STORM_HANDLE_DISPLAY_NAME);
        if (handle == null) return;
        InfernalMobStormDamageEvent damageEvent = new InfernalMobStormDamageEvent(
                mob, victim, lightning, handle, level, event.getDamage());
        plugin.getServer().getPluginManager().callEvent(damageEvent);
        if (damageEvent.isCancelled() || damageEvent.getDamage() <= 0.0) event.setCancelled(true);
        else event.setDamage(damageEvent.getDamage());
    }

    private boolean hasSkill(PersistentDataContainer pdc, NamespacedKey key, String expected) {
        return expected.equals(pdc.get(key, PersistentDataType.STRING));
    }

    private Integer readInteger(PersistentDataContainer pdc, NamespacedKey key) {
        return pdc.get(key, PersistentDataType.INTEGER);
    }

    private Double readDouble(PersistentDataContainer pdc, NamespacedKey key) {
        return pdc.get(key, PersistentDataType.DOUBLE);
    }

    private LivingEntity findSource(PersistentDataContainer pdc, NamespacedKey key) {
        return findLivingEntity(readUuid(pdc, key));
    }

    private UUID readUuid(PersistentDataContainer pdc, NamespacedKey key) {
        String value = pdc.get(key, PersistentDataType.STRING);
        if (value == null) return null;
        try { return UUID.fromString(value); } catch (IllegalArgumentException ex) { return null; }
    }

    private LivingEntity findLivingEntity(UUID uuid) {
        if (uuid == null) return null;
        Entity entity = plugin.getServer().getEntity(uuid);
        return entity instanceof LivingEntity living ? living : null;
    }
}
