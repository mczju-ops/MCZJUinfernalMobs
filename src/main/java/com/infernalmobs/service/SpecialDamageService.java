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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
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
        String source = pdc.get(Keys.FIREWORK_SOURCE, org.bukkit.persistence.PersistentDataType.STRING);
        if (source == null) return;
        UUID mobUuid;
        try {
            mobUuid = UUID.fromString(source);
        } catch (IllegalArgumentException ex) {
            return;
        }
        LivingEntity mob = findLivingEntity(mobUuid);
        if (mob == null || !mob.isValid()) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        Integer level = pdc.get(Keys.FIREWORK_LEVEL, org.bukkit.persistence.PersistentDataType.INTEGER);
        String skillId = pdc.get(Keys.FIREWORK_SKILL_ID, org.bukkit.persistence.PersistentDataType.STRING);
        if (level == null || !"firework".equals(skillId)) return;
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
        if (!"ghastly".equals(pdc.get(Keys.GHASTLY_SKILL_ID,
                org.bukkit.persistence.PersistentDataType.STRING))) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        LivingEntity mob = findLivingEntity(readUuid(pdc, Keys.GHASTLY_SOURCE));
        if (mob == null || !mob.isValid()) return;
        Integer level = pdc.get(Keys.GHASTLY_LEVEL, org.bukkit.persistence.PersistentDataType.INTEGER);
        if (level == null) return;
        InfernalMobHandle handle = PdcHandleCodec.read(pdc, mob, level,
                Keys.GHASTLY_HANDLE_AFFIXES, Keys.GHASTLY_HANDLE_SUPPRESSED,
                Keys.GHASTLY_HANDLE_DISPLAY_NAME);
        if (handle == null) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.PROJECTILE) {
            Double damage = pdc.get(Keys.GHASTLY_DAMAGE, org.bukkit.persistence.PersistentDataType.DOUBLE);
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
        if (!"necromancer".equals(pdc.get(Keys.NECROMANCER_SKILL_ID,
                org.bukkit.persistence.PersistentDataType.STRING))) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        LivingEntity mob = findLivingEntity(readUuid(pdc, Keys.NECROMANCER_SOURCE));
        if (mob == null || !mob.isValid()) return;
        Integer level = pdc.get(Keys.NECROMANCER_LEVEL, org.bukkit.persistence.PersistentDataType.INTEGER);
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
        if (!"storm".equals(pdc.get(Keys.STORM_SKILL_ID,
                org.bukkit.persistence.PersistentDataType.STRING))) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        Double recordedDamage = pdc.get(Keys.STORM_DAMAGE, org.bukkit.persistence.PersistentDataType.DOUBLE);
        if (recordedDamage == null) return;
        event.setDamage(Math.max(0.0, recordedDamage));
        LivingEntity mob = findLivingEntity(readUuid(pdc, Keys.STORM_SOURCE));
        if (mob == null || !mob.isValid()) return;
        Integer level = pdc.get(Keys.STORM_LEVEL, org.bukkit.persistence.PersistentDataType.INTEGER);
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

    private UUID readUuid(org.bukkit.persistence.PersistentDataContainer pdc,
                           org.bukkit.NamespacedKey key) {
        String value = pdc.get(key, org.bukkit.persistence.PersistentDataType.STRING);
        if (value == null) return null;
        try { return UUID.fromString(value); } catch (IllegalArgumentException ex) { return null; }
    }

    private LivingEntity findLivingEntity(UUID uuid) {
        if (uuid == null) return null;
        Entity entity = plugin.getServer().getEntity(uuid);
        return entity instanceof LivingEntity living ? living : null;
    }
}
