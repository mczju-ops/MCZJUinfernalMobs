package com.infernalmobs.service;

import com.infernalmobs.config.ConfigLoader;
import com.infernalmobs.config.DeathMessageConfig;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.model.MobState;
import com.infernalmobs.util.MiniMessageHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

/**
 * 击杀播报服务。MiniMessage 模板 + Placeholder，世界广播；怪物名 [LvN]前缀+名 紧凑有框框有颜色。
 */
public class DeathMessageService {

    private final ConfigLoader config;
    private final Random random = new Random();
    private CombatService combatService;

    public DeathMessageService(ConfigLoader config) {
        this.config = config;
    }

    public void setCombatService(CombatService combatService) {
        this.combatService = combatService;
    }

    public void broadcastIfEnabled(LivingEntity entity, MobState mobState, Player killer) {
        DeathMessageConfig dm = config.getDeathMessageConfig();
        if (dm == null || !dm.enable() || killer == null) return;

        int level = mobState.getProfile().getLevel();
        Component playerComponent = getColoredPlayerComponent(killer, dm);
        Component weaponComponent = getWeaponComponent(killer, dm.defaultWeapon());
        Component mobComponent = buildMobComponentWithHover(entity, mobState, dm);

        String template = pickRandom(dm.messages());
        Component message = MiniMessageHelper.deserialize(template,
                Placeholder.component("player", playerComponent),
                Placeholder.component("weapon", weaponComponent),
                Placeholder.component("mob", mobComponent));

        for (Player p : getBroadcastTargets(entity.getWorld(), level, dm)) {
            p.sendMessage(message);
        }
    }

    /** 玩家被炒鸡怪击杀时世界广播。若怪物手持特殊武器且开启，播报带武器的模板。 */
    public void broadcastSlainByIfEnabled(Player victim, LivingEntity killerMob, MobState mobState) {
        DeathMessageConfig dm = config.getDeathMessageConfig();
        if (dm == null || !dm.slainByEnable()) return;

        int level = mobState.getProfile().getLevel();
        Component playerComponent = getColoredPlayerComponent(victim, dm);
        Component mobComponent = buildMobComponentWithHover(killerMob, mobState, dm);
        String template = pickRandom(dm.slainByMessages());
        Component weaponComponent = null;

        if (dm.slainByWithWeaponEnable() && killerMob instanceof Mob mob && !dm.slainByWithWeaponMessages().isEmpty()) {
            ItemStack hand = mob.getEquipment() != null ? mob.getEquipment().getItemInMainHand() : null;
            if (hand != null && !hand.getType().isAir() && isSpecialWeapon(hand, dm.slainByWithWeaponWhen())) {
                template = pickRandom(dm.slainByWithWeaponMessages());
                weaponComponent = getMobWeaponComponent(hand, dm.defaultWeapon());
            }
        }
        Component message = weaponComponent == null
                ? MiniMessageHelper.deserialize(template,
                        Placeholder.component("player", playerComponent),
                        Placeholder.component("mob", mobComponent))
                : MiniMessageHelper.deserialize(template,
                        Placeholder.component("player", playerComponent),
                        Placeholder.component("mob", mobComponent),
                        Placeholder.component("weapon", weaponComponent));
        for (Player p : getBroadcastTargets(victim.getWorld(), level, dm)) {
            p.sendMessage(message);
        }
    }

    /**
     * 炒鸡怪非玩家击杀时播报「抢人头」消息。
     * 找最近玩家作为「受害者」，伤害来源解析为实体名/伤害类型名。
     * 占位符：{@code <nearest_player>}、{@code <mob>}、{@code <source>}
     */
    public void broadcastKillStealIfEnabled(LivingEntity entity, MobState mobState) {
        DeathMessageConfig dm = config.getDeathMessageConfig();
        if (dm == null || !dm.killStealEnable()) return;

        // 找最近的玩家（在 killStealRange 格以内）
        double range = dm.killStealRange();
        Player nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (Player p : entity.getWorld().getPlayers()) {
            if (p.getWorld() != entity.getWorld()) continue;
            double dist = p.getLocation().distanceSquared(entity.getLocation());
            if (dist < nearestDist && dist <= range * range) {
                nearestDist = dist;
                nearest = p;
            }
        }
        if (nearest == null) return;  // 范围内无玩家，不播报

        // 解析伤害来源名（实体自定义名 > 实体类型名 > 伤害类型名）
        Component sourceComponent = resolveSourceComponent(entity);
        Component nearestPlayerComponent = getColoredPlayerComponent(nearest, dm);
        Component mobComponent = buildMobComponentWithHover(entity, mobState, dm);

        String template = pickRandom(dm.killStealMessages());
        Component message = MiniMessageHelper.deserialize(template,
                Placeholder.component("nearest_player", nearestPlayerComponent),
                Placeholder.component("mob", mobComponent),
                Placeholder.component("source", sourceComponent));

        int level = mobState.getProfile().getLevel();
        for (Player p : getBroadcastTargets(entity.getWorld(), level, dm)) {
            p.sendMessage(message);
        }
    }

    /** 常见非实体伤害类型的中文名。未收录的降级为英文原名。 */
    private static final java.util.Map<EntityDamageEvent.DamageCause, String> CAUSE_NAMES =
            java.util.Map.ofEntries(
                    java.util.Map.entry(EntityDamageEvent.DamageCause.FALL,             "摔落"),
                    java.util.Map.entry(EntityDamageEvent.DamageCause.FIRE,             "火焰"),
                    java.util.Map.entry(EntityDamageEvent.DamageCause.FIRE_TICK,        "燃烧"),
                    java.util.Map.entry(EntityDamageEvent.DamageCause.LAVA,             "岩浆"),
                    java.util.Map.entry(EntityDamageEvent.DamageCause.DROWNING,         "溺水"),
                    java.util.Map.entry(EntityDamageEvent.DamageCause.SUFFOCATION,      "窒息"),
                    java.util.Map.entry(EntityDamageEvent.DamageCause.STARVATION,       "饥饿"),
                    java.util.Map.entry(EntityDamageEvent.DamageCause.LIGHTNING,        "雷击"),
                    java.util.Map.entry(EntityDamageEvent.DamageCause.POISON,           "中毒"),
                    java.util.Map.entry(EntityDamageEvent.DamageCause.MAGIC,            "魔法"),
                    java.util.Map.entry(EntityDamageEvent.DamageCause.WITHER,           "凋零"),
                    java.util.Map.entry(EntityDamageEvent.DamageCause.VOID,             "虚空"),
                    java.util.Map.entry(EntityDamageEvent.DamageCause.CONTACT,          "接触伤害"),
                    java.util.Map.entry(EntityDamageEvent.DamageCause.THORNS,           "荆棘"),
                    java.util.Map.entry(EntityDamageEvent.DamageCause.BLOCK_EXPLOSION,  "方块爆炸"),
                    java.util.Map.entry(EntityDamageEvent.DamageCause.ENTITY_EXPLOSION, "爆炸"),
                    java.util.Map.entry(EntityDamageEvent.DamageCause.PROJECTILE,       "弹射物"),
                    java.util.Map.entry(EntityDamageEvent.DamageCause.CUSTOM,           "自定义伤害"),
                    java.util.Map.entry(EntityDamageEvent.DamageCause.MELTING,          "融化"),
                    java.util.Map.entry(EntityDamageEvent.DamageCause.FREEZE,           "冰冻")
            );

    /** 解析最后一次伤害来源的可读名称。 */
    private Component resolveSourceComponent(LivingEntity entity) {
        EntityDamageEvent cause = entity.getLastDamageCause();
        if (cause instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();

            // 检测是否带有 infernalmobs_skill_id 元数据（技能弹射物/烟花）
            String skillId = extractSkillId(damager);
            if (skillId != null) {
                // 尝试找到施法的炒鸡怪名
                Component casterComp = resolveCasterComponent(damager);
                Component skillComp = buildSkillComponent(skillId);
                return casterComp == null ? skillComp
                        : casterComp.append(MiniMessageHelper.deserialize("<gray> 的 </gray>"))
                                    .append(skillComp);
            }

            // 检测施法方是否为另一只炒鸡怪（普通近战）
            LivingEntity shooter = resolveShooter(damager);
            if (shooter != null && combatService != null
                    && combatService.getMobState(shooter.getUniqueId()) != null) {
                com.infernalmobs.model.MobState state = combatService.getMobState(shooter.getUniqueId());
                return buildMobComponentWithHover(shooter, state,
                        config.getDeathMessageConfig());
            }

            // 普通实体：优先自定义名，否则类型名
            if (damager instanceof LivingEntity le && le.customName() != null) {
                return le.customName();
            }
            String typeName = formatMaterial(damager.getType().name());
            return MiniMessageHelper.deserialize("<gray>" + typeName + "</gray>");
        }
        // 非实体伤害：优先查中文映射，没有则降级为英文原名
        if (cause != null) {
            String name = CAUSE_NAMES.getOrDefault(cause.getCause(),
                    cause.getCause().name().toLowerCase().replace('_', ' '));
            return MiniMessageHelper.deserialize("<gray>" + name + "</gray>");
        }
        return MiniMessageHelper.deserialize("<gray>未知</gray>");
    }

    /** 提取实体上的 infernalmobs_skill_id 元数据值，没有则返回 null。 */
    private String extractSkillId(Entity entity) {
        if (!entity.hasMetadata("infernalmobs_skill_id")) return null;
        var values = entity.getMetadata("infernalmobs_skill_id");
        return values.isEmpty() ? null : values.get(0).asString();
    }

    /** 从弹射物/烟花元数据还原施法炒鸡怪组件，找不到返回 null。 */
    private Component resolveCasterComponent(Entity damager) {
        java.util.UUID casterUuid = null;
        if (damager.hasMetadata("infernalmobs_source")) {
            var v = damager.getMetadata("infernalmobs_source");
            if (!v.isEmpty() && v.get(0).value() instanceof java.util.UUID u) casterUuid = u;
        } else if (damager.hasMetadata("infernalmobs_firework_source")) {
            var v = damager.getMetadata("infernalmobs_firework_source");
            if (!v.isEmpty() && v.get(0).value() instanceof java.util.UUID u) casterUuid = u;
        }
        if (casterUuid == null || combatService == null) return null;
        com.infernalmobs.model.MobState state = combatService.getMobState(casterUuid);
        if (state == null) return null;
        final java.util.UUID finalUuid = casterUuid;
        // 从世界里找该实体
        for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
            LivingEntity le = (LivingEntity) world.getEntities().stream()
                    .filter(e -> e.getUniqueId().equals(finalUuid) && e instanceof LivingEntity)
                    .findFirst().orElse(null);
            if (le != null) return buildMobComponentWithHover(le, state, config.getDeathMessageConfig());
        }
        return null;
    }

    /** 将 skill ID 翻译为带颜色的技能名组件。 */
    private Component buildSkillComponent(String skillId) {
        com.infernalmobs.config.SkillConfig sc = config.getSkillConfig(skillId);
        String display = config.getSkillDisplay(skillId, sc);
        if (display != null && !display.isBlank()) {
            return MiniMessageHelper.parseSkillDisplay(display);
        }
        return MiniMessageHelper.deserialize("<gray>" + skillId + "</gray>");
    }

    /** 从箭矢等弹射物的 Shooter 中提取 LivingEntity。 */
    private LivingEntity resolveShooter(Entity damager) {
        if (damager instanceof org.bukkit.entity.Projectile proj
                && proj.getShooter() instanceof LivingEntity le) {
            return le;
        }
        return null;
    }

    /** 普通玩家绿色，OP 深红。避免玩家名含 &lt;&gt; 注入，用 Component 着色。 */
    private Component getColoredPlayerComponent(Player player, DeathMessageConfig dm) {
        NamedTextColor c = player.isOp() ? toNamedColor(dm.playerColorOp()) : toNamedColor(dm.playerColorNormal());
        return Component.text(player.getName()).color(c != null ? c : NamedTextColor.WHITE);
    }

    private NamedTextColor toNamedColor(String tag) {
        if (tag == null) return NamedTextColor.WHITE;
        String s = tag.replaceAll("<|>", "").toLowerCase().replace("-", "_");
        for (NamedTextColor n : NamedTextColor.NAMES.values()) {
            if (n.toString().equalsIgnoreCase(s)) return n;
        }
        return switch (s) {
            case "green" -> NamedTextColor.GREEN;
            case "dark_red" -> NamedTextColor.DARK_RED;
            default -> NamedTextColor.WHITE;
        };
    }

    /** 11 级及以上全服播报，否则仅炒鸡插件生效的世界（事件世界）内播报。 */
    private Collection<? extends Player> getBroadcastTargets(World eventWorld, int mobLevel, DeathMessageConfig dm) {
        if (mobLevel >= dm.globalBroadcastLevelThreshold()) {
            return Bukkit.getOnlinePlayers();
        }
        return eventWorld.getPlayers();
    }

    private boolean isSpecialWeapon(ItemStack item, String when) {
        if (when == null) when = "enchanted";
        return switch (when.toLowerCase()) {
            case "custom_name" -> {
                ItemMeta m = item.getItemMeta();
                yield m != null && m.hasDisplayName();
            }
            case "non_air" -> !item.getType().isAir();
            case "enchanted" -> item.getEnchantments() != null && !item.getEnchantments().isEmpty();
            default -> item.getEnchantments() != null && !item.getEnchantments().isEmpty();
        };
    }

    private Component getMobWeaponComponent(ItemStack hand, String defaultWeapon) {
        if (hand == null || hand.getType().isAir()) return MiniMessageHelper.deserialize("<white>" + defaultWeapon + "</white>");
        Component name = hand.displayName();
        if (name != null && !PlainTextComponentSerializer.plainText().serialize(name).isEmpty()) return name;
        return MiniMessageHelper.deserialize("<white>" + formatMaterial(hand.getType().name()) + "</white>");
    }

    private Component buildMobComponentWithHover(LivingEntity entity, MobState mobState, DeathMessageConfig dm) {
        int level = mobState.getProfile().getLevel();
        String prefix = dm.getLevelPrefix(level);
        String mobName = dm.getMobDisplayName(entity.getType());
        String color = dm.getLevelTierColor(level);
        String tagName = color.replaceAll("[<>]", "");
        String template = color + "[Lv" + level + "]" + prefix + mobName + "</" + tagName + ">";
        Component textComponent = MiniMessageHelper.deserialize(template);

        List<Component> affixComps = new ArrayList<>();
        mobState.getProfile().getAffixes().forEach(affix -> {
            SkillConfig sc = config.getSkillConfig(affix.getSkillId());
            String display = config.getSkillDisplay(affix.getSkillId(), sc);
            affixComps.add(MiniMessageHelper.parseSkillDisplay(display));
        });
        if (affixComps.isEmpty()) return textComponent;

        Component hoverContent = Component.text("词条：");
        for (int i = 0; i < affixComps.size(); i++) {
            if (i > 0) hoverContent = hoverContent.append(Component.text(", "));
            hoverContent = hoverContent.append(affixComps.get(i));
        }
        return textComponent.hoverEvent(HoverEvent.showText(hoverContent));
    }

    private Component getWeaponComponent(Player killer, String defaultWeapon) {
        ItemStack hand = killer.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) return MiniMessageHelper.deserialize("<white>" + defaultWeapon + "</white>");
        Component name = hand.displayName();
        if (name != null && !PlainTextComponentSerializer.plainText().serialize(name).isEmpty()) {
            return name;
        }
        return MiniMessageHelper.deserialize("<white>" + formatMaterial(hand.getType().name()) + "</white>");
    }

    private String formatMaterial(String key) {
        return key.toLowerCase().replace('_', ' ');
    }

    private String pickRandom(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        return list.get(random.nextInt(list.size()));
    }
}
