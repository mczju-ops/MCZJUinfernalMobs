package com.infernalmobs.service;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 击杀统计服务：按等级、按玩家（玩家用 id 字符串表示）记录击杀数。
 * 内存存储，每分钟定时落盘 + onDisable 落盘。
 */
public class KillStatsService {

    private static final String FILE_NAME = "kill_stats.yml";
    private static final String KEY_PLAYERS = "players";

    private final JavaPlugin plugin;
    private final File dataFile;
    /** 玩家 id -> (等级 -> 击杀数) */
    private final Map<String, Map<Integer, Integer>> data = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    public KillStatsService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), FILE_NAME);
    }

    /** 从 YAML 加载到内存，主类 onEnable 时调用。 */
    public void load() {
        data.clear();
        dirty = false;
        if (!dataFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        var playersSec = yaml.getConfigurationSection(KEY_PLAYERS);
        if (playersSec == null) return;
        for (String playerId : playersSec.getKeys(false)) {
            var levelSec = playersSec.getConfigurationSection(playerId);
            if (levelSec == null) continue;
            Map<Integer, Integer> byLevel = new ConcurrentHashMap<>();
            for (String levelKey : levelSec.getKeys(false)) {
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
        var playersSec = yaml.createSection(KEY_PLAYERS);
        for (Map.Entry<String, Map<Integer, Integer>> e : data.entrySet()) {
            Map<Integer, Integer> byLevel = e.getValue();
            if (byLevel.isEmpty()) continue;
            var levelSec = playersSec.createSection(e.getKey());
            for (Map.Entry<Integer, Integer> le : byLevel.entrySet()) {
                if (le.getValue() > 0) levelSec.set(String.valueOf(le.getKey()), le.getValue());
            }
        }
        try {
            yaml.save(dataFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("保存 kill_stats.yml 失败: " + ex.getMessage());
        }
    }

    /** 记录一次击杀。玩家击杀炒鸡怪时调用。 */
    public void addKill(String playerId, int level) {
        if (playerId == null || playerId.isEmpty()) return;
        data.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
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
