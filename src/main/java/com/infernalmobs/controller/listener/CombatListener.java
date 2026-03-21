package com.infernalmobs.controller.listener;

import com.infernalmobs.InfernalMobsPlugin;
import com.infernalmobs.model.MobState;
import com.infernalmobs.service.CombatService;
import com.infernalmobs.service.DeathMessageService;
import com.infernalmobs.service.GuaranteedLootService;
import com.infernalmobs.service.KillStatsService;
import com.infernalmobs.service.LootService;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;

/**
 * 战斗事件监听，将事件委托给 CombatService 处理。
 */
public class CombatListener implements Listener {

    private final JavaPlugin plugin;
    private final CombatService combatService;
    private final DeathMessageService deathMessageService;
    private final KillStatsService killStatsService;
    private final GuaranteedLootService guaranteedLootService;

    public CombatListener(JavaPlugin plugin, CombatService combatService, DeathMessageService deathMessageService,
                          KillStatsService killStatsService, GuaranteedLootService guaranteedLootService) {
        this.plugin = plugin;
        this.combatService = combatService;
        this.deathMessageService = deathMessageService;
        this.killStatsService = killStatsService;
        this.guaranteedLootService = guaranteedLootService;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework fw && fw.hasMetadata("infernalmobs_firework_source")) {
            combatService.handleFireworkDamage(event);
            return;
        }
        if (event.getEntity() instanceof LivingEntity victim) {
            Player attackingPlayer = null;
            if (event.getDamager() instanceof Player p) attackingPlayer = p;
            else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) attackingPlayer = p;
            if (attackingPlayer != null) {
                MobState state = combatService.getMobState(victim.getUniqueId());
                if (state != null) combatService.onPlayerAttackMob(event, victim, attackingPlayer, state);
            }
        }
        if (event.getEntity() instanceof Player victim) {
            LivingEntity damager = null;
            if (event.getDamager() instanceof LivingEntity le) {
                damager = le;
            } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof LivingEntity shooter) {
                damager = shooter;
            }
            if (damager != null) {
                MobState state = combatService.getMobState(damager.getUniqueId());
                if (state != null) {
                    combatService.onMobAttack(event, damager, victim, state);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof LivingEntity) {
            LivingEntity entity = (LivingEntity) event.getEntity();
            MobState state = combatService.getMobState(entity.getUniqueId());
            if (state != null) {
                combatService.onMobDeath(event, entity, state);
                Player killer = entity.getKiller();
                if (killer != null) {
                    killStatsService.addKill(killer.getUniqueId().toString(), state.getProfile().getLevel());
                    deathMessageService.broadcastIfEnabled(entity, state, killer);
                    if (guaranteedLootService != null) {
                        guaranteedLootService.onKill(killer.getUniqueId().toString(), state.getProfile().getLevel(), killer, entity.getLocation());
                    }
                }
                LootService loot = plugin instanceof InfernalMobsPlugin im ? im.getLootService() : null;
                if (loot != null) {
                    loot.onInfernalMobDeath(event, entity, state);
                }
            }
        }
        combatService.unregisterMob(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (victim.getLastDamageCause() instanceof EntityDamageByEntityEvent damageEvent) {
            LivingEntity damager = null;
            if (damageEvent.getDamager() instanceof LivingEntity le) {
                damager = le;
            } else if (damageEvent.getDamager() instanceof Projectile proj && proj.getShooter() instanceof LivingEntity shooter) {
                damager = shooter;
            }
            if (damager != null) {
                MobState state = combatService.getMobState(damager.getUniqueId());
                if (state != null) {
                    event.setDeathMessage(null);  // 隐藏原版死亡消息，使用炒鸡怪播报
                    deathMessageService.broadcastSlainByIfEnabled(victim, damager, state);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof LivingEntity entity) {
            combatService.onMobDamaged(event, entity);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        combatService.onProjectileHit(event);
    }
}
