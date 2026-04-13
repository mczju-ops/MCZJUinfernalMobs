package com.infernalmobs.command;

import com.infernalmobs.InfernalMobsPlugin;
import com.infernalmobs.config.ConfigLoader;
import com.infernalmobs.factory.MobFactory;
import com.infernalmobs.service.CombatService;
import com.infernalmobs.service.KillStatsService;
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

    /** 所有可生成的 LivingEntity 类型（运行时从枚举动态计算，支持全版本所有生物）。 */
    private static final Set<EntityType> SPAWNABLE_TYPES = Arrays.stream(EntityType.values())
            .filter(EntityType::isSpawnable)
            .filter(t -> t.getEntityClass() != null && org.bukkit.entity.LivingEntity.class.isAssignableFrom(t.getEntityClass()))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    private final InfernalMobsPlugin plugin;
    private final ConfigLoader configLoader;
    private final MobFactory mobFactory;
    private final CombatService combatService;
    private final KillStatsService killStatsService;

    public InfernalMobCommand(InfernalMobsPlugin plugin, ConfigLoader configLoader, MobFactory mobFactory, CombatService combatService, KillStatsService killStatsService) {
        this.plugin = plugin;
        this.configLoader = configLoader;
        this.mobFactory = mobFactory;
        this.combatService = combatService;
        this.killStatsService = killStatsService;
    }

    private static final String PERM_ADMIN = "infernalmobs.admin";
    private static final String PERM_RELOAD = "infernalmobs.reload";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!sender.hasPermission(PERM_ADMIN)) {
                send(sender, "<red>你没有权限使用该指令");
                return true;
            }
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase();
        if ("reload".equals(sub)) {
            if (!sender.hasPermission(PERM_ADMIN) && !sender.hasPermission(PERM_RELOAD)) {
                send(sender, "<red>你没有权限执行该指令");
                return true;
            }
            return handleReload(sender);
        }
        if (!sender.hasPermission(PERM_ADMIN)) {
            send(sender, "<red>你没有权限使用该指令");
            return true;
        }
        if ("spawn".equals(sub)) return handleSpawn(sender, args);
        if ("spawnat".equals(sub)) return handleSpawnAt(sender, args);
        if ("stats".equals(sub)) return handleStats(sender, args);
        if ("debug".equals(sub)) return handleDebug(sender, args);
        if ("clear".equals(sub)) return handleClear(sender, args);
        if ("cleantags".equals(sub)) return handleCleanTags(sender);
        sendHelp(sender);
        return true;
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        if (args.length < 2) {
            boolean on = configLoader.isDebug();
            send(sender, "<white>调试模式: </white><state>", Placeholder.parsed("state", on ? "<green>开" : "<red>关"));
            return true;
        }
        String v = args[1].toLowerCase();
        if ("on".equals(v) || "true".equals(v) || "1".equals(v)) {
            configLoader.setDebug(true);
            send(sender, "<green>已开启调试模式：控制台将输出技能日志与 [InfernalMobs:debug:mechanize]（区域/等级等）");
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
        if (!SPAWNABLE_TYPES.contains(type)) {
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
            mobFactory.mechanizeWithRequiredAffixes(entity, loc, level, skillIds);
        }
        String skillsStr = skillIds.isEmpty() ? "" : " [" + String.join(", ", skillIds) + "]";
        send(sender, "<green>已生成炒鸡怪: <type> Lv<level><skills>", Placeholder.unparsed("type", type.name()), Placeholder.unparsed("level", String.valueOf(level)), Placeholder.unparsed("skills", skillsStr));
        return true;
    }

    /**
     * /im spawnat &lt;x&gt; &lt;y&gt; &lt;z&gt; &lt;world&gt; &lt;实体类型&gt; [等级] [技能1,技能2,...]
     * 控制台、命令方块、玩家均可使用。
     */
    private boolean handleSpawnAt(CommandSender sender, String[] args) {
        if (args.length < 6) {
            send(sender, "<red>用法: /im spawnat <x> <y> <z> <world> <实体类型> [等级] [技能1,技能2,...]");
            return true;
        }
        double x, y, z;
        try {
            x = Double.parseDouble(args[1]);
            y = Double.parseDouble(args[2]);
            z = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            send(sender, "<red>坐标必须为数字，例: /im spawnat 100 64 -200 world zombie 5");
            return true;
        }
        World world = plugin.getServer().getWorld(args[4]);
        if (world == null) {
            send(sender, "<red>未找到世界: <world>", Placeholder.unparsed("world", args[4]));
            return true;
        }
        EntityType type;
        try {
            type = EntityType.valueOf(args[5].toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            send(sender, "<red>未知实体类型: <type>", Placeholder.unparsed("type", args[5]));
            return true;
        }
        if (!SPAWNABLE_TYPES.contains(type)) {
            send(sender, "<red>无法生成该类型的炒鸡怪: <type>", Placeholder.unparsed("type", type.name()));
            return true;
        }
        int level = 5;
        List<String> skillIds = new ArrayList<>();
        if (args.length >= 7) {
            if (isNumeric(args[6])) {
                level = Math.max(1, Math.min(100, Integer.parseInt(args[6])));
                if (args.length >= 8) skillIds = parseSkillIds(args, 7);
            } else {
                skillIds = parseSkillIds(args, 6);
            }
        }
        List<String> invalid = validateSkillIds(skillIds);
        if (!invalid.isEmpty()) {
            send(sender, "<red>未知技能: <skills>", Placeholder.unparsed("skills", String.join(", ", invalid)));
            return true;
        }
        Location loc = new Location(world, x, y, z);
        LivingEntity entity = (LivingEntity) world.spawnEntity(loc, type);
        if (skillIds.isEmpty()) {
            mobFactory.mechanizeWithLevel(entity, loc, level);
        } else {
            mobFactory.mechanizeWithRequiredAffixes(entity, loc, level, skillIds);
        }
        String skillsStr = skillIds.isEmpty() ? "" : " [" + String.join(", ", skillIds) + "]";
        send(sender, "<green>已生成炒鸡怪: <type> Lv<level> @ <world> (<x>, <y>, <z>)<skills>",
                Placeholder.unparsed("type", type.name()),
                Placeholder.unparsed("level", String.valueOf(level)),
                Placeholder.unparsed("world", world.getName()),
                Placeholder.unparsed("x", String.valueOf((int) x)),
                Placeholder.unparsed("y", String.valueOf((int) y)),
                Placeholder.unparsed("z", String.valueOf((int) z)),
                Placeholder.unparsed("skills", skillsStr));
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

    private boolean handleStats(CommandSender sender, String[] args) {
        if (args.length < 2) {
            int count = combatService.getTrackedCount();
            send(sender, "<gold>当前追踪的炒鸡怪数: <white>" + count);
            return true;
        }
        String playerId = resolvePlayerId(args[1]);
        if (playerId == null) {
            send(sender, "<red>未找到玩家，请使用在线玩家名或 UUID 字符串");
            return true;
        }
        int total = killStatsService.getTotalKills(playerId);
        Map<Integer, Integer> byLevel = killStatsService.getKillsByLevel(playerId);

        send(sender, "<gold>━━━ <white>" + args[1] + " <gray>的炒鸡怪击杀统计 <gold>━━━");
        if (byLevel.isEmpty()) {
            send(sender, "<gray>  尚无击杀记录");
        } else {
            // 按等级排序，过滤掉 0 击杀，每行最多 4 个等级
            List<Map.Entry<Integer, Integer>> entries = byLevel.entrySet().stream()
                    .filter(e -> e.getValue() > 0)
                    .sorted(Map.Entry.comparingByKey())
                    .toList();
            StringBuilder row = new StringBuilder();
            int col = 0;
            for (Map.Entry<Integer, Integer> e : entries) {
                row.append(levelColor(e.getKey()))
                   .append("Lv").append(e.getKey())
                   .append(" <white>×").append(e.getValue())
                   .append("  ");
                col++;
                if (col == 4) {
                    send(sender, "  " + row.toString().stripTrailing());
                    row.setLength(0);
                    col = 0;
                }
            }
            if (!row.isEmpty()) send(sender, "  " + row.toString().stripTrailing());
        }
        send(sender, "<gray>  合计: <yellow>" + total + " <gray>击杀");
        return true;
    }

    /** 根据等级返回 MiniMessage 颜色标签，3 级一档对应五阶稀有度。 */
    private String levelColor(int level) {
        if (level >= 13) return "<red>";          // infernal
        if (level >= 10) return "<gold>";         // legendary
        if (level >= 7)  return "<dark_purple>";  // epic
        if (level >= 4)  return "<blue>";         // rare
        return "<white>";                         // common
    }

    /** 将玩家名或 UUID 字符串解析为存储用的玩家 id。 */
    private String resolvePlayerId(String arg) {
        if (arg == null || arg.isEmpty()) return null;
        arg = arg.trim();
        if (arg.length() == 36 && arg.contains("-")) {
            try {
                java.util.UUID.fromString(arg);
                return arg;
            } catch (IllegalArgumentException ignored) {}
        }
        Player p = plugin.getServer().getPlayerExact(arg);
        return p != null ? p.getUniqueId().toString() : null;
    }

    private boolean handleClear(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "<red>该指令仅玩家可执行");
            return true;
        }
        int radius = 32;
        if (args.length >= 2) {
            try {
                radius = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                send(sender, "<red>半径必须是数字，例: /im clear 64");
                return true;
            }
        }
        if (radius < 1 || radius > 256) {
            send(sender, "<red>半径请在 1~256 之间");
            return true;
        }
        int count = combatService.clearMobsInRadius(player.getLocation(), radius);
        send(sender, "<green>已清除周围 <radius> 格内 <count> 只炒鸡怪",
                Placeholder.unparsed("radius", String.valueOf(radius)),
                Placeholder.unparsed("count", String.valueOf(count)));
        return true;
    }

    private boolean handleCleanTags(CommandSender sender) {
        int count = combatService.removeOrphanedImLevelEntities();
        send(sender, "<green>已清除 <count> 只有 im_level 标签但非炒鸡怪的孤立实体",
                Placeholder.unparsed("count", String.valueOf(count)));
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        try {
            if (plugin != null) plugin.reloadRuntimeConfig();
            else configLoader.reload();
            send(sender, "<green>已重新加载炒鸡怪插件下所有配置文件");
        } catch (Exception e) {
            send(sender, "<red>重载失败: <err>", Placeholder.unparsed("err", e.getMessage()));
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        send(sender, "<gold>=== InfernalMobs 炒鸡怪指令 ===</gold>");
        send(sender, "<yellow>/im spawn <实体类型> [等级] [技能1,技能2,...]</yellow> <gray>- 在面前生成炒鸡怪（仅玩家）</gray>");
        send(sender, "<gray>  例: /im spawn zombie 5  或  /im spawn creeper 10 poisonous,armoured</gray>");
        send(sender, "<yellow>/im spawnat <x> <y> <z> <世界> <实体类型> [等级] [技能...]</yellow> <gray>- 在指定坐标生成（支持命令方块/控制台）</gray>");
        send(sender, "<gray>  例: /im spawnat 100 64 -200 world zombie 8 morph,ender</gray>");
        send(sender, "<yellow>/im stats [玩家]</yellow> <gray>- 查看追踪数，或指定玩家的击杀统计</gray>");
        send(sender, "<yellow>/im debug [on|off]</yellow> <gray>- 调试：技能日志与 mechanize 区域/等级输出</gray>");
        send(sender, "<yellow>/im reload</yellow> <gray>- 从 config.yml 重新加载技能参数等配置</gray>");
        send(sender, "<yellow>/im clear [半径]</yellow> <gray>- 清除周围指定半径内的炒鸡怪，默认 32</gray>");
        send(sender, "<yellow>/im cleantags</yellow> <gray>- 清除有 im_level 标签但非炒鸡怪的孤立实体</gray>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("spawn", "spawnat", "stats", "debug", "reload", "clear", "cleantags").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        // spawnat <x> <y> <z> <world> <type> [level] [skills...]
        if ("spawnat".equalsIgnoreCase(args[0])) {
            return switch (args.length) {
                case 2, 3, 4 -> List.of("~");   // x y z 提示波浪号
                case 5 -> plugin.getServer().getWorlds().stream()
                        .map(World::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[4].toLowerCase()))
                        .sorted().collect(Collectors.toList());
                case 6 -> {
                    String prefix = args[5].toUpperCase();
                    yield SPAWNABLE_TYPES.stream()
                            .map(Enum::name)
                            .filter(s -> s.startsWith(prefix))
                            .sorted().collect(Collectors.toList());
                }
                case 7 -> Arrays.asList("1", "5", "10", "15", "20");
                default -> filterPrefix(getAllSkillIds(), args[args.length - 1].toLowerCase());
            };
        }
        if (args.length == 2 && "debug".equalsIgnoreCase(args[0])) {
            return Arrays.asList("on", "off").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && "stats".equalsIgnoreCase(args[0])) {
            return plugin.getServer().getOnlinePlayers().stream().map(Player::getName).filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).toList();
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
        if (args.length == 2 && "clear".equalsIgnoreCase(args[0])) {
            return Arrays.asList("16", "32", "64", "128", "256").stream()
                    .filter(s -> s.startsWith(args[1]))
                    .collect(Collectors.toList());
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
