package com.infernalmobs.controller.listener;

import com.infernalmobs.config.ConfigLoader;
import com.infernalmobs.config.DeathMessageConfig;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.model.MobState;
import com.infernalmobs.service.CombatService;
import com.infernalmobs.util.Keys;
import com.infernalmobs.util.MiniMessageHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import com.infernalmobs.skill.impl.DualMorphSkill;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * 全知之眼：右键按准星射线判定瞄准实体；命中炒鸡怪才消耗并在粒子动画后显示其技能。
 * 非炒鸡怪（含玩家）在动作栏提示「名称不是炒鸡怪」，不消耗道具。
 * 幻形之锁（morph_controller）：可配置射线距离，右键空气/方块时延伸命中（见 config morph-controller.ray-range）。
 * 通过 PDC infernal_item 或 mi_id=infernal_eye 识别物品（兼容 ItemCreator magicItemId）。
 */
public class MagicItemListener implements Listener {

    private static final String INFERNAL_EYE_ID = "infernal_eye";
    private static final double RAY_SIZE = 0.15;
    private static final int REVEAL_STEPS = 12;
    private static final long REVEAL_STEP_TICKS = 2L;
    /** 与死亡播报、头顶名一致：<mob>= [LvN]前缀+名（含等级颜色、Lv15 炒鸡乱码） */
    private static final String EYE_LINE_TEMPLATE = "<mob> <white>| </white><skills>";

    private static final String MORPH_CONTROLLER_ID = "morph_controller";

    private final JavaPlugin plugin;
    private final ConfigLoader configLoader;
    private final CombatService combatService;

    public MagicItemListener(JavaPlugin plugin, ConfigLoader configLoader, CombatService combatService) {
        this.plugin = plugin;
        this.configLoader = configLoader;
        this.combatService = combatService;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        var item = event.getItem();
        if (item == null || item.getType().isAir()) return;

        var pdc = item.getPersistentDataContainer();
        String itemId = pdc.getOrDefault(Keys.IM_ITEM_ID, PersistentDataType.STRING, "");
        if (itemId.isEmpty()) itemId = pdc.getOrDefault(Keys.MI_ID, PersistentDataType.STRING, "");
        if (INFERNAL_EYE_ID.equals(itemId)) {
            handleInfernalEyeInteract(event);
            return;
        }
        if (isMorphController(item)) {
            tryMorphControllerRaycast(event);
        }
    }

    private void handleInfernalEyeInteract(PlayerInteractEvent event) {
        event.setCancelled(true);

        Player player = event.getPlayer();
        LivingEntity target = raycastTarget(player, configLoader.getInfernalEyeRange());
        if (target == null) {
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_EYE_DEATH, 1f, 0.5f);
            player.sendActionBar(MiniMessageHelper.deserialize("<gray>未瞄准任何生物"));
            return;
        }

        MobState mobState = combatService.getMobState(target.getUniqueId());
        if (mobState == null) {
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_EYE_DEATH, 1f, 0.5f);
            DeathMessageConfig dm = configLoader.getDeathMessageConfig();
            String typeName = target instanceof Player viewed
                    ? viewed.getName()
                    : (dm != null ? dm.getMobDisplayName(target.getType()) : target.getType().name());
            player.sendActionBar(MiniMessageHelper.deserialize("<gray><type>不是炒鸡怪", Placeholder.unparsed("type", typeName)));
            return;
        }

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_EYE_DEATH, 1f, 1.4f);
        consumeOneFromMainHand(player);
        playRevealAnimationThenSend(player, target, buildEyeLine(target, mobState));
    }

    /**
     * 幻形之锁：右键空气/方块时用射线延伸距离；仅成功封印幻形词条时取消事件并消耗道具。
     */
    private void tryMorphControllerRaycast(PlayerInteractEvent event) {
        double range = configLoader.getMorphControllerRayRange();
        if (range <= 0) return;

        Player player = event.getPlayer();
        LivingEntity target = raycastTarget(player, range);
        if (target == null) return;

        boolean suppressed = combatService.suppressMorphAffix(target);
        if (!suppressed) return;

        event.setCancelled(true);
        consumeOneFromMainHand(player);
        applyMorphControllerEffects(player, target);
    }

    /** 每次成功触发全知之眼后从主手扣除 1 个（堆叠则减数量，最后一个则清空）。 */
    private static void consumeOneFromMainHand(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) return;
        int amount = hand.getAmount();
        if (amount <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            hand.setAmount(amount - 1);
        }
    }

    private static LivingEntity raycastTarget(Player player, double maxDistance) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection();
        World world = player.getWorld();

        RayTraceResult result = world.rayTraceEntities(
                eye, dir, maxDistance, RAY_SIZE,
                e -> e instanceof LivingEntity && e != player
        );
        if (result == null) return null;
        Entity hit = result.getHitEntity();
        return hit instanceof LivingEntity le ? le : null;
    }

    private Component buildEyeLine(LivingEntity target, MobState mobState) {
        DeathMessageConfig dm = configLoader.getDeathMessageConfig();
        int level = mobState.getProfile().getLevel();
        String prefix = dm != null ? dm.getLevelPrefix(level) : "炒鸡";
        String mobName = dm != null ? dm.getMobDisplayName(target.getType()) : target.getType().name();
        String color = dm != null ? dm.getLevelTierColor(level) : "<gold>";
        String tagName = color.replaceAll("[<>]", "");
        String mobTemplate = color + "[Lv" + level + "]" + prefix + mobName + "</" + tagName + ">";
        Component mobComponent = MiniMessageHelper.deserialize(mobTemplate);

        var affixes = mobState.getProfile().getAffixes();
        Component skillsComponent = Component.empty();
        for (int i = 0; i < affixes.size(); i++) {
            if (i > 0) skillsComponent = skillsComponent.append(Component.text(" "));
            var affix = affixes.get(i);
            SkillConfig sc = configLoader.getSkillConfig(affix.getSkillId());
            String display = configLoader.getSkillDisplay(affix.getSkillId(), sc);
            Component skillLine = MiniMessageHelper.parseSkillDisplay(display);
            if (mobState.isAffixSuppressed(affix.getSkillId())) {
                skillLine = skillLine.decorate(net.kyori.adventure.text.format.TextDecoration.STRIKETHROUGH);
            }
            skillsComponent = skillsComponent.append(skillLine);
        }
        return MiniMessageHelper.deserialize(EYE_LINE_TEMPLATE,
                Placeholder.component("mob", mobComponent),
                Placeholder.component("skills", skillsComponent));
    }

    private void playRevealAnimationThenSend(Player player, LivingEntity target, Component line) {
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(MagicItemListener.class);
        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !target.isValid()) {
                    cancel();
                    return;
                }
                double t = (double) step / REVEAL_STEPS;
                Location from = target.getEyeLocation();
                Location to = player.getEyeLocation();
                Vector delta = to.toVector().subtract(from.toVector());
                Location point = from.clone().add(delta.multiply(t));

                player.getWorld().spawnParticle(Particle.ENCHANT, point, 8, 0.08, 0.08, 0.08, 0);

                step++;
                if (step > REVEAL_STEPS) {
                    player.sendMessage(line);
                    player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.9f, 1.2f);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, REVEAL_STEP_TICKS);
    }

    // ── morph_controller 右键实体 ─────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        // 仅处理主手，避免双触发
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof LivingEntity target)) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!isMorphController(hand)) return;

        boolean suppressed = combatService.suppressMorphAffix(target);
        if (!suppressed) return;

        event.setCancelled(true);
        consumeOne(hand);
        applyMorphControllerEffects(player, target);
    }

    private void applyMorphControllerEffects(Player player, LivingEntity target) {
        String particleKey = "TRIAL_OMEN";
        com.infernalmobs.config.SkillConfig sc = configLoader.getSkillConfig("morph");
        if (sc != null) particleKey = sc.getString("suppress-particle", "TRIAL_OMEN");
        DualMorphSkill.spawnSuppressParticles(target, plugin, particleKey);

        String soundKey = "BLOCK_VAULT_REJECT_REWARDED_PLAYER";
        float pitch = 1.4f;
        if (sc != null) {
            soundKey = sc.getString("suppress-sound", soundKey);
            pitch = (float) sc.getDouble("suppress-sound-pitch", pitch);
        }
        try {
            Sound sound = Sound.valueOf(soundKey.toUpperCase().replace(".", "_"));
            player.getWorld().playSound(player.getLocation(), sound, 2.0f, pitch);
        } catch (IllegalArgumentException ignored) {}
    }

    private static boolean isMorphController(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        String id = meta.getPersistentDataContainer().get(Keys.MI_ID, PersistentDataType.STRING);
        return MORPH_CONTROLLER_ID.equalsIgnoreCase(id != null ? id.trim() : null);
    }

    private static void consumeOne(ItemStack stack) {
        if (stack.getAmount() <= 1) stack.setAmount(0);
        else stack.setAmount(stack.getAmount() - 1);
    }
}
