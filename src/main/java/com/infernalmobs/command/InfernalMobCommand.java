package com.infernalmobs.command;

import com.infernalmobs.InfernalMobsPlugin;
import com.infernalmobs.config.ConfigLoader;
import com.infernalmobs.factory.MobFactory;
import com.infernalmobs.service.CombatService;
import com.infernalmobs.util.MiniMessageHelper;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 调试指令：灵活生成炒鸡怪、查看统计等。
 * /im spawn &lt;实体类型&gt; [等级] [技能1,技能2,...]
 * /im stats
 */
public class InfernalMobCommand implements CommandExecutor, TabCompleter {

    private static void send(CommandSender sender, String miniMessage) {
        sender.sendMessage(MiniMessageHelper.miniMessage().deserialize(miniMessage));
    }

    private static void send(CommandSender sender, String template, TagResolver... resolvers) {
        sender.sendMessage(MiniMessageHelper.deserialize(template, resolvers));
    }

    private static final Set<EntityType> SPAWNABLE_TYPES = Set.of(
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER, EntityType.SPIDER,
            EntityType.CAVE_SPIDER, EntityType.ENDERMAN, EntityType.WITCH, EntityType.PHANTOM,
            EntityType.DROWNED, EntityType.STRAY, EntityType.HUSK, EntityType.PIGLIN,
            EntityType.PIGLIN_BRUTE, EntityType.ZOGLIN, EntityType.WARDEN,
            EntityType.BLAZE, EntityType.GHAST, EntityType.MAGMA_CUBE, EntityType.VINDICATOR,
            EntityType.EVOKER, EntityType.PILLAGER, EntityType.RAVAGER, EntityType.GUARDIAN,
            EntityType.ELDER_GUARDIAN
    );

    private final InfernalMobsPlugin plugin;
    private final ConfigLoader configLoader;
    private final MobFactory mobFactory;
    private final CombatService combatService;

    public InfernalMobCommand(InfernalMobsPlugin plugin, ConfigLoader configLoader, MobFactory mobFactory, CombatService combatService) {
        this.plugin = plugin;
        this.configLoader = configLoader;
        this.mobFactory = mobFactory;
        this.combatService = combatService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase();
        if ("spawn".equals(sub)) return handleSpawn(sender, args);
        if ("stats".equals(sub)) return handleStats(sender);
        if ("debug".equals(sub)) return handleDebug(sender, args);
        if ("reload".equals(sub)) return handleReload(sender);
        sendHelp(sender);
        return true;
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        if (args.length < 2) {
            boolean on = configLoader.isDebug();
            send(sender, "<gold>[炒鸡怪]</gold> <white>调试模式: </white><state>", Placeholder.parsed("state", on ? "<green>开" : "<red>关"));
            return true;
        }
        String v = args[1].toLowerCase();
        if ("on".equals(v) || "true".equals(v) || "1".equals(v)) {
            configLoader.setDebug(true);
            send(sender, "<green>已开启调试模式，控制台将输出技能关键节点日志");
            return true;
        }
        if ("off".equals(v) || "false".equals(v) || "0".equals(v)) {
            configLoader.setDebug(false);
            send(sender, "<yellow>已关闭调试模式");
            return true;
        }
        send(sender, "<red>用法: /im debug [on|off]");
        return true;
    }

    private boolean handleSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "<red>该指令仅玩家可执行");
            return true;
        }
        if (args.length < 2) {
            send(sender, "<red>用法: /im spawn <实体类型> [等级] [技能1,技能2,...]");
            return true;
        }
        EntityType type;
        try {
            type = EntityType.valueOf(args[1].toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            send(sender, "<red>未知实体类型: <type>", Placeholder.unparsed("type", args[1]));
            return true;
        }
        if (!SPAWNABLE_TYPES.contains(type) || !type.isSpawnable() || !type.isAlive()) {
            send(sender, "<red>无法生成该类型的炒鸡怪: <type>", Placeholder.unparsed("type", type.name()));
            return true;
        }
        int level = 5;
        List<String> skillIds = new ArrayList<>();
        if (args.length >= 3) {
            if (isNumeric(args[2])) {
                level = Math.max(1, Math.min(100, Integer.parseInt(args[2])));
                if (args.length >= 4) {
                    skillIds = parseSkillIds(args, 3);
                }
            } else {
                skillIds = parseSkillIds(args, 2);
            }
        }
        List<String> invalid = validateSkillIds(skillIds);
        if (!invalid.isEmpty()) {
            send(sender, "<red>未知技能: <skills>", Placeholder.unparsed("skills", String.join(", ", invalid)));
            return true;
        }
        Location loc = getSpawnLocation(player);
        World world = loc.getWorld();
        if (world == null) {
            send(sender, "<red>无法获取世界");
            return true;
        }
        LivingEntity entity = (LivingEntity) world.spawnEntity(loc, type);
        if (skillIds.isEmpty()) {
            mobFactory.mechanizeWithLevel(entity, loc, level);
        } else {
            mobFactory.mechanizeWithAffixes(entity, loc, level, skillIds);
        }
        String skillsStr = skillIds.isEmpty() ? "" : " [" + String.join(", ", skillIds) + "]";
        send(sender, "<green>已生成炒鸡怪: <type> Lv<level><skills>", Placeholder.unparsed("type", type.name()), Placeholder.unparsed("level", String.valueOf(level)), Placeholder.unparsed("skills", skillsStr));
        return true;
    }

    private boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) return false;
        for (char c : s.toCharArray()) if (!Character.isDigit(c)) return false;
        return true;
    }

    private List<String> parseSkillIds(String[] args, int start) {
        List<String> list = new ArrayList<>();
        for (int i = start; i < args.length; i++) {
            for (String id : args[i].split(",")) {
                id = id.trim().toLowerCase();
                if (!id.isEmpty()) list.add(id);
            }
        }
        return list;
    }

    private List<String> validateSkillIds(List<String> ids) {
        if (ids.isEmpty()) return List.of();
        var configs = configLoader.getSkillConfigs();
        return ids.stream().filter(id -> !configs.containsKey(id)).collect(Collectors.toList());
    }

    private List<String> getAllSkillIds() {
        return new ArrayList<>(configLoader.getSkillConfigs().keySet());
    }

    private Location getSpawnLocation(Player player) {
        org.bukkit.block.Block target = player.getTargetBlockExact(6);
        if (target != null && target.getType().isSolid()) {
            return target.getRelative(0, 1, 0).getLocation().add(0.5, 0, 0.5);
        }
        return player.getLocation().add(player.getLocation().getDirection().multiply(2)).add(0.5, 0, 0.5);
    }

    private boolean handleStats(CommandSender sender) {
        int count = combatService.getTrackedCount();
        send(sender, "<gold>[炒鸡怪]</gold> <white>当前追踪数: </white><count>", Placeholder.unparsed("count", String.valueOf(count)));
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("infernalmobs.reload") && sender instanceof Player) {
            send(sender, "<red>你没有权限执行该指令");
            return true;
        }
        try {
            configLoader.reload();
            if (plugin != null) plugin.reloadLootConfig();
            send(sender, "<green>[炒鸡怪] 已重新加载 config.yml 与 loot.yml（技能参数、权重、区域、等级掉落池）");
        } catch (Exception e) {
            send(sender, "<red>[炒鸡怪] 重载失败: <err>", Placeholder.unparsed("err", e.getMessage()));
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        send(sender, "<gold>=== InfernalMobs 炒鸡怪指令 ===</gold>");
        send(sender, "<yellow>/im spawn <实体类型> [等级] [技能1,技能2,...]</yellow> <gray>- 在面前生成炒鸡怪</gray>");
        send(sender, "<gray>  例: /im spawn zombie 5  或  /im spawn creeper 10 poisonous,armoured,ender</gray>");
        send(sender, "<yellow>/im stats</yellow> <gray>- 查看当前追踪的炒鸡怪数量</gray>");
        send(sender, "<yellow>/im debug [on|off]</yellow> <gray>- 调试模式开关，控制台输出技能日志</gray>");
        send(sender, "<yellow>/im reload</yellow> <gray>- 从 config.yml 重新加载技能参数等配置</gray>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("spawn", "stats", "debug", "reload").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && "debug".equalsIgnoreCase(args[0])) {
            return Arrays.asList("on", "off").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && "spawn".equalsIgnoreCase(args[0])) {
            String prefix = args[1].toUpperCase();
            return SPAWNABLE_TYPES.stream()
                    .map(Enum::name)
                    .filter(s -> s.startsWith(prefix))
                    .sorted()
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && "spawn".equalsIgnoreCase(args[0])) {
            if (isNumeric(args[2]) || args[2].isEmpty()) {
                return Arrays.asList("1", "5", "10", "15", "20");
            }
            return filterPrefix(getAllSkillIds(), args[2].toLowerCase());
        }
        if (args.length >= 4 && "spawn".equalsIgnoreCase(args[0])) {
            return filterPrefix(getAllSkillIds(), args[args.length - 1].toLowerCase());
        }
        return Collections.emptyList();
    }

    private List<String> filterPrefix(List<String> list, String prefix) {
        if (prefix.isEmpty()) return list;
        return list.stream()
                .filter(s -> s.startsWith(prefix))
                .sorted()
                .collect(Collectors.toList());
    }
}
