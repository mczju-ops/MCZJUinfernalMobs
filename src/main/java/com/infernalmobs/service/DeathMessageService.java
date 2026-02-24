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
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 击杀播报服务。MiniMessage 模板 + Placeholder，世界广播；怪物名悬停显示词条列表。
 */
public class DeathMessageService {

    private static final String MOB_LINE_TEMPLATE = "<white>Lv <level> <level_prefix> <mob_name></white>";

    private final ConfigLoader config;
    private final Random random = new Random();

    public DeathMessageService(ConfigLoader config) {
        this.config = config;
    }

    public void broadcastIfEnabled(LivingEntity entity, MobState mobState, Player killer) {
        DeathMessageConfig dm = config.getDeathMessageConfig();
        if (dm == null || !dm.enable() || killer == null) return;

        String playerName = killer.getName();
        String weapon = getWeaponDisplay(killer, dm.defaultWeapon());
        Component mobComponent = buildMobComponentWithHover(entity, mobState, dm);

        String template = pickRandom(dm.messages());
        Component message = MiniMessageHelper.deserialize(template,
                Placeholder.unparsed("player", playerName),
                Placeholder.unparsed("weapon", weapon),
                Placeholder.component("mob", mobComponent));

        for (Player p : entity.getWorld().getPlayers()) {
            p.sendMessage(message);
        }
    }

    /** 玩家被炒鸡怪击杀时世界广播，模板用 <player> <mob>，mob 悬停显示词条。 */
    public void broadcastSlainByIfEnabled(Player victim, LivingEntity killerMob, MobState mobState) {
        DeathMessageConfig dm = config.getDeathMessageConfig();
        if (dm == null || !dm.slainByEnable()) return;

        Component mobComponent = buildMobComponentWithHover(killerMob, mobState, dm);
        String template = pickRandom(dm.slainByMessages());
        Component message = MiniMessageHelper.deserialize(template,
                Placeholder.unparsed("player", victim.getName()),
                Placeholder.component("mob", mobComponent));

        for (Player p : victim.getWorld().getPlayers()) {
            p.sendMessage(message);
        }
    }

    private Component buildMobComponentWithHover(LivingEntity entity, MobState mobState, DeathMessageConfig dm) {
        int level = mobState.getProfile().getLevel();
        String levelPrefix = dm.getLevelPrefix(level);
        String mobName = dm.getMobDisplayName(entity.getType());
        Component textComponent = MiniMessageHelper.deserialize(MOB_LINE_TEMPLATE,
                Placeholder.unparsed("level", String.valueOf(level)),
                Placeholder.parsed("level_prefix", levelPrefix),
                Placeholder.unparsed("mob_name", mobName));

        List<Component> affixComps = new ArrayList<>();
        mobState.getProfile().getAffixes().forEach(affix -> {
            SkillConfig sc = config.getSkillConfig(affix.getSkillId());
            String display = sc != null ? sc.getDisplay() : affix.getSkillId();
            affixComps.add(MiniMessageHelper.fromLegacy(display));
        });
        if (affixComps.isEmpty()) return textComponent;

        Component hoverContent = Component.text("词条：");
        for (int i = 0; i < affixComps.size(); i++) {
            if (i > 0) hoverContent = hoverContent.append(Component.text(", "));
            hoverContent = hoverContent.append(affixComps.get(i));
        }
        return textComponent.hoverEvent(HoverEvent.showText(hoverContent));
    }

    private String getWeaponDisplay(Player killer, String defaultWeapon) {
        ItemStack hand = killer.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) return defaultWeapon;
        ItemMeta meta = hand.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            Component name = meta.displayName();
            return name != null ? PlainTextComponentSerializer.plainText().serialize(name) : defaultWeapon;
        }
        return formatMaterial(hand.getType().name());
    }

    private String formatMaterial(String key) {
        return key.toLowerCase().replace('_', ' ');
    }

    private String pickRandom(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        return list.get(random.nextInt(list.size()));
    }
}
