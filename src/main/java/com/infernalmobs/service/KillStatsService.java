package com.infernalmobs.service;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 击杀统计服务：按等级、按玩家 UUID 记录击杀数；落盘时在每位玩家下写入 {@code name} 便于阅读。
 * 内存存储，每分钟定时落盘 + onDisable 落盘。
 */
public class KillStatsService {

    private static final String FILE_NAME = "kill_stats.yml";
    private static final String KEY_PLAYERS = "players";
    /** YAML 中与等级统计并列的显示名键（非数字，不参与等级解析） */
    private static final String KEY_DISPLAY_NAME = "name";

    private final JavaPlugin plugin;
    private final File dataFile;
    /** 玩家 UUID 字符串 -> (等级 -> 击杀数) */
    private final Map<String, Map<Integer, Integer>> data = new ConcurrentHashMap<>();
    /** 玩家 UUID -> 最近一次击杀时的游戏内名称（落盘写入 name） */
    private final Map<String, String> displayNames = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    public KillStatsService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), FILE_NAME);
    }

    /** 从 YAML 加载到内存，主类 onEnable 时调用。 */
    public void load() {
        data.clear();
        displayNames.clear();
        dirty = false;
        if (!dataFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        var playersSec = yaml.getConfigurationSection(KEY_PLAYERS);
        if (playersSec == null) return;
        for (String playerId : playersSec.getKeys(false)) {
            var levelSec = playersSec.getConfigurationSection(playerId);
            if (levelSec == null) continue;
            String storedName = levelSec.getString(KEY_DISPLAY_NAME);
            if (storedName != null && !storedName.isBlank()) {
                displayNames.put(playerId, storedName.trim());
            }
            Map<Integer, Integer> byLevel = new ConcurrentHashMap<>();
            for (String levelKey : levelSec.getKeys(false)) {
                if (KEY_DISPLAY_NAME.equalsIgnoreCase(levelKey)) continue;
                try {
                    int level = Integer.parseInt(levelKey);
                    int count = levelSec.getInt(levelKey, 0);
                    if (count > 0) byLevel.put(level, count);
                } catch (NumberFormatException ignored) {}
            }
            if (!byLevel.isEmpty()) data.put(playerId, byLevel);
        }
    }

    /** 将脏数据落盘到 YAML。调度器与 onDisable 时调用。 */
    public void saveIfDirty() {
        if (!dirty) return;
        dirty = false;
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().header("""
                players 下主键为玩家 UUID。
                每位玩家下: name 为落盘时最后已知的游戏内名称（便于阅读）；数字键为怪物等级 -> 击杀数。
                """);
        var playersSec = yaml.createSection(KEY_PLAYERS);
        for (Map.Entry<String, Map<Integer, Integer>> e : data.entrySet()) {
            Map<Integer, Integer> byLevel = e.getValue();
            if (byLevel.isEmpty()) continue;
            var levelSec = playersSec.createSection(e.getKey());
            String nm = displayNames.get(e.getKey());
            if (nm != null && !nm.isBlank()) {
                levelSec.set(KEY_DISPLAY_NAME, nm.trim());
            }
            for (Map.Entry<Integer, Integer> le : byLevel.entrySet()) {
                if (le.getValue() > 0) levelSec.set(String.valueOf(le.getKey()), le.getValue());
            }
        }
        try {
            if (!dataFile.getParentFile().exists()) dataFile.getParentFile().mkdirs();
            if (!dataFile.exists()) dataFile.createNewFile();
            yaml.save(dataFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("保存 kill_stats.yml 失败: " + ex.getMessage());
        }
    }

    /**
     * 记录一次击杀。玩家击杀炒鸡怪时调用。
     *
     * @param playerUuid   玩家 UUID 字符串
     * @param displayName  当前游戏内名称，用于写入 YAML 的 name 字段（可 null）
     * @param level        怪物等级
     */
    public void addKill(String playerUuid, String displayName, int level) {
        if (playerUuid == null || playerUuid.isEmpty()) return;
        if (displayName != null && !displayName.isBlank()) {
            displayNames.put(playerUuid, displayName.trim());
        }
        data.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>())
            .merge(Math.max(1, level), 1, Integer::sum);
        dirty = true;
    }

    /** 获取某玩家某等级的击杀数。 */
    public int getKills(String playerId, int level) {
        Map<Integer, Integer> byLevel = data.get(playerId);
        if (byLevel == null) return 0;
        return byLevel.getOrDefault(level, 0);
    }

    /** 获取某玩家各等级击杀数（只读视图）。 */
    public Map<Integer, Integer> getKillsByLevel(String playerId) {
        Map<Integer, Integer> byLevel = data.get(playerId);
        if (byLevel == null) return Map.of();
        return Collections.unmodifiableMap(new HashMap<>(byLevel));
    }

    /** 获取某玩家总击杀数。 */
    public int getTotalKills(String playerId) {
        Map<Integer, Integer> byLevel = data.get(playerId);
        if (byLevel == null) return 0;
        return byLevel.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** 标记为脏，用于外部需要强制保存时。 */
    public void markDirty() {
        dirty = true;
    }
}
