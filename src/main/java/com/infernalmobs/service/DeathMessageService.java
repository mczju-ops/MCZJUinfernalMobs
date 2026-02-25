package com.infernalmobs.service;

import com.infernalmobs.config.ConfigLoader;
import com.infernalmobs.config.DeathMessageConfig;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.model.MobState;
import com.infernalmobs.util.MiniMessageHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 击杀播报服务。MiniMessage 模板 + Placeholder，世界广播；怪物名 [LvN]前缀+名 紧凑有框框有颜色。
 */
public class DeathMessageService {

    private final ConfigLoader config;
    private final Random random = new Random();

    public DeathMessageService(ConfigLoader config) {
        this.config = config;
    }

    public void broadcastIfEnabled(LivingEntity entity, MobState mobState, Player killer) {
        DeathMessageConfig dm = config.getDeathMessageConfig();
        if (dm == null || !dm.enable() || killer == null) return;

        String playerName = killer.getName();
        Component weaponComponent = getWeaponComponent(killer, dm.defaultWeapon());
        Component mobComponent = buildMobComponentWithHover(entity, mobState, dm);

        String template = pickRandom(dm.messages());
        Component message = MiniMessageHelper.deserialize(template,
                Placeholder.unparsed("player", playerName),
                Placeholder.component("weapon", weaponComponent),
                Placeholder.component("mob", mobComponent));

        for (Player p : entity.getWorld().getPlayers()) {
            p.sendMessage(message);
        }
    }

    /** 玩家被炒鸡怪击杀时世界广播。若怪物手持特殊武器且开启，播报带武器的模板。 */
    public void broadcastSlainByIfEnabled(Player victim, LivingEntity killerMob, MobState mobState) {
        DeathMessageConfig dm = config.getDeathMessageConfig();
        if (dm == null || !dm.slainByEnable()) return;

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
        if (weaponComponent == null) {
            Component message = MiniMessageHelper.deserialize(template,
                    Placeholder.unparsed("player", victim.getName()),
                    Placeholder.component("mob", mobComponent));
            for (Player p : victim.getWorld().getPlayers()) p.sendMessage(message);
            return;
        }
        Component message = MiniMessageHelper.deserialize(template,
                Placeholder.unparsed("player", victim.getName()),
                Placeholder.component("mob", mobComponent),
                Placeholder.component("weapon", weaponComponent));
        for (Player p : victim.getWorld().getPlayers()) p.sendMessage(message);
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
