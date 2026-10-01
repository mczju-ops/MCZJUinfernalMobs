package com.infernalmobs.service;

import com.infernalmobs.api.InfernalMobHandle;
import com.infernalmobs.api.event.affix.effect.InfernalMobFireworkDamageEvent;
import com.infernalmobs.util.Keys;
import com.infernalmobs.util.PdcHandleCodec;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
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

    private LivingEntity findLivingEntity(UUID uuid) {
        Entity entity = plugin.getServer().getEntity(uuid);
        return entity instanceof LivingEntity living ? living : null;
    }
}
