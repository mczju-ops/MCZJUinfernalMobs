package com.infernalmobs.controller.listener;

import com.infernalmobs.InfernalMobsPlugin;
import com.infernalmobs.config.ConfigLoader;
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
import org.bukkit.entity.Item;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
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
                    // 与原版一致：getKiller() 为「最近对该实体造成伤害的玩家」记名（几秒内参与过即可），不要求最后一击是玩家（如摔死、环境杀）
                    Player killer = entity.getKiller();
                    if (killer != null) {
                        // 玩家击杀：经验缩放、战利品、统计、保底、播报
                        if (plugin instanceof InfernalMobsPlugin im) {
                            ConfigLoader cfg = im.getConfigLoader();
                            double mult = cfg.getExpMultiplier();
                            if (mult > 0 && event.getDroppedExp() > 0) {
                                int level = state.getProfile().getLevel();
                                int scaled = (int) Math.round(event.getDroppedExp() * level * mult);
                                event.setDroppedExp(scaled);
                            }
                        }
                        String uuid = killer.getUniqueId().toString();
                        String pname = killer.getName();
                        killStatsService.addKill(uuid, pname, state.getProfile().getLevel());
                        deathMessageService.broadcastIfEnabled(entity, state, killer);
                        LootService loot = plugin instanceof InfernalMobsPlugin im ? im.getLootService() : null;
                        // 保底掉落：先于常规抽取，以 dropItemNaturally 掉落在地并触发命令/广播
                        GuaranteedLootService guaranteedLootService = plugin instanceof InfernalMobsPlugin im
                                ? im.getGuaranteedLootService()
                                : null;
                        int mobLevel = state.getProfile().getLevel();
                        int deathLootRolls = loot != null ? loot.rollDeathLootTimes(mobLevel) : 0;
                        if (guaranteedLootService != null && loot != null) {
                            for (com.infernalmobs.config.GuaranteedLootConfig.GuaranteedRule rule
                                    : guaranteedLootService.collectTriggered(uuid, pname, mobLevel, deathLootRolls)) {
                                loot.processGuaranteedDrop(rule, entity, killer, mobLevel);
                            }
                        }
                        // 常规等级池抽取（与保底共用同一次 drop-times roll）
                        boolean vanillaDropsCleared = false;
                        if (loot != null) {
                            vanillaDropsCleared = loot.onInfernalMobDeath(event, entity, state, deathLootRolls);
                        }
                        // 若开启了 replace-vanilla-drops 清空原版掉落，则补回「当前仍装备」且本插件记录过的拾取物（不含已扔掉的）
                        if (vanillaDropsCleared) {
                            for (ItemStack picked : combatService.releasePickedUpItemsStillEquipped(entity)) {
                                if (picked != null && !picked.getType().isAir() && picked.getAmount() > 0) {
                                    event.getDrops().add(picked);
                                }
                            }
                        }
                    } else {
                        // 非玩家击杀：清空炒鸡经验加成与原版掉落，仅播报抢人头
                        event.getDrops().clear();
                        event.setDroppedExp(0);
                        deathMessageService.broadcastKillStealIfEnabled(entity, state);
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
     * 仅用于保护动物警告广播（只需要"有没有玩家参与"，不要求最后一击）。
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

    /**
     * 炒鸡怪把物品扔到地上时同步扣减拾取记录，避免列表里长期残留已丢弃的堆叠。
     * 生命为 0 时跳过：死亡相关掉落可能也走此事件，拾取补回由 {@link CombatService#releasePickedUpItemsStillEquipped} 统一处理。
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDropItem(EntityDropItemEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (entity.getHealth() <= 0) return;
        if (combatService.getMobState(entity.getUniqueId()) == null) return;
        Item drop = event.getItemDrop();
        if (drop == null) return;
        ItemStack stack = drop.getItemStack();
        combatService.unrecordDroppedPickedUpItem(entity.getUniqueId(), stack);
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

    /**
     * 僵尸系炒鸡怪：再生、瞬间治疗等回血按「原版 20 血 × 等级」封顶，避免头领僵尸抬高的 MAX_HEALTH 被回满导致超模。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof LivingEntity le)) return;
        if (event.getAmount() <= 0) return;
        MobState state = combatService.getMobState(le.getUniqueId());
        if (state == null) return;
        double zCap = CombatService.zombieFamilyHealCap(le, state);
        if (Double.isInfinite(zCap)) return;
        double cap = Math.min(zCap, le.getMaxHealth());
        double cur = le.getHealth();
        if (cur >= cap) {
            event.setCancelled(true);
            return;
        }
        double after = cur + event.getAmount();
        if (after > cap) {
            event.setAmount(cap - cur);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        combatService.onProjectileHit(event);
    }

    /** 区块卸载时，同步注销该区块内所有已追踪的炒鸡怪，避免内存泄漏。 */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        for (org.bukkit.entity.Entity e : event.getEntities()) {
            if (combatService.getMobState(e.getUniqueId()) != null) {
                combatService.unregisterMob(e.getUniqueId());
            }
        }
    }
}
