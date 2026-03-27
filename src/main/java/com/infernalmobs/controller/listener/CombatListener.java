package com.infernalmobs.controller.listener;

import com.infernalmobs.InfernalMobsPlugin;
import com.infernalmobs.config.ProtectedAnimalsConfig;
import com.infernalmobs.model.MobState;
import com.infernalmobs.service.CombatService;
import com.infernalmobs.service.DeathMessageService;
import com.infernalmobs.service.GuaranteedLootService;
import com.infernalmobs.service.KillStatsService;
import com.infernalmobs.service.LootService;
import com.infernalmobs.util.MiniMessageHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
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
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 战斗事件监听，将事件委托给 CombatService 处理。
 */
public class CombatListener implements Listener {

    private final JavaPlugin plugin;
    private final CombatService combatService;
    private final DeathMessageService deathMessageService;
    private final KillStatsService killStatsService;

    public CombatListener(JavaPlugin plugin, CombatService combatService, DeathMessageService deathMessageService,
                          KillStatsService killStatsService) {
        this.plugin = plugin;
        this.combatService = combatService;
        this.deathMessageService = deathMessageService;
        this.killStatsService = killStatsService;
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
                ProtectedAnimalsConfig protectedAnimalsConfig = null;
                if (plugin instanceof InfernalMobsPlugin im) {
                    protectedAnimalsConfig = im.getConfigLoader().getProtectedAnimalsConfig();
                }

                boolean protectedAnimal = protectedAnimalsConfig != null
                        && protectedAnimalsConfig.enabled()
                        && protectedAnimalsConfig.protects(entity.getType());

                // 炒鸡小动物：不给炒鸡奖励，清理掉落/经验并警告击杀者。
                if (protectedAnimal) {
                    event.getDrops().clear();
                    if (protectedAnimalsConfig.clearExp()) {
                        event.setDroppedExp(0);
                    }
                    broadcastProtectedAnimalWarning(entity, protectedAnimalsConfig);
                } else {
                    Player killer = entity.getKiller();
                    if (killer != null) {
                        String uuid = killer.getUniqueId().toString();
                        String pname = killer.getName();
                        killStatsService.addKill(uuid, pname, state.getProfile().getLevel());
                        deathMessageService.broadcastIfEnabled(entity, state, killer);
                        GuaranteedLootService guaranteedLootService = plugin instanceof InfernalMobsPlugin im
                                ? im.getGuaranteedLootService()
                                : null;
                        if (guaranteedLootService != null) {
                            guaranteedLootService.onKill(uuid, pname, state.getProfile().getLevel(), killer, entity.getLocation());
                        }
                    }
                    LootService loot = plugin instanceof InfernalMobsPlugin im ? im.getLootService() : null;
                    boolean vanillaDropsCleared = false;
                    if (loot != null) {
                        vanillaDropsCleared = loot.onInfernalMobDeath(event, entity, state);
                    }
                    // 若开启了 replace-vanilla-drops 清空原版掉落，则补回该炒鸡怪生前捡到的玩家物品
                    if (vanillaDropsCleared) {
                        for (ItemStack picked : combatService.consumePickedUpItems(entity.getUniqueId())) {
                            if (picked != null && !picked.getType().isAir() && picked.getAmount() > 0) {
                                event.getDrops().add(picked);
                            }
                        }
                    }
                }
            }
        }
        combatService.unregisterMob(event.getEntity().getUniqueId());
    }

    private static void broadcastProtectedAnimalWarning(LivingEntity entity, ProtectedAnimalsConfig cfg) {
        Player killer = resolvePlayerKiller(entity);
        if (killer == null) return;

        String template = cfg.messageTemplate();
        if (template == null || template.isEmpty()) return;

        Component line = MiniMessageHelper.deserialize(template,
                Placeholder.unparsed("player_name", killer.getName()));
        for (Player p : entity.getWorld().getPlayers()) {
            p.sendMessage(line);
        }
    }

    /**
     * 优先使用 Bukkit 的 getKiller；若为空则从最后一次伤害事件里解析玩家（含玩家射出的投射物）。
     */
    private static Player resolvePlayerKiller(LivingEntity entity) {
        Player killer = entity.getKiller();
        if (killer != null) return killer;

        if (entity.getLastDamageCause() instanceof EntityDamageByEntityEvent byEntity) {
            if (byEntity.getDamager() instanceof Player p) return p;
            if (byEntity.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;
        LivingEntity entity = (LivingEntity) event.getEntity();
        if (combatService.getMobState(entity.getUniqueId()) == null) return;
        combatService.recordPickedUpItem(entity.getUniqueId(), event.getItem().getItemStack());
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
