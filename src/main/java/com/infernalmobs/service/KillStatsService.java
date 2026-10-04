package com.infernalmobs.service;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 击杀统计内存服务。磁盘只在启动加载、定时快照保存和关闭保存时访问。 */
public final class KillStatsService {

    private static final String FILE_NAME = "kill_stats.yml";
    private static final String KEY_PLAYERS = "players";
    private static final String KEY_DISPLAY_NAME = "name";

    private final JavaPlugin plugin;
    private final File dataFile;
    private final Object dataLock = new Object();
    private final Object saveLock = new Object();
    private final Map<String, Map<Integer, Integer>> data = new HashMap<>();
    private final Map<String, String> displayNames = new HashMap<>();
    private long changeVersion;
    private long savedVersion;

    public KillStatsService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(new File(plugin.getDataFolder(), "data"), FILE_NAME);
    }

    /** 启动时调用一次。新版不会探测或迁移根目录下的旧数据文件。 */
    public void load() {
        synchronized (dataLock) {
            data.clear();
            displayNames.clear();
            changeVersion = 0;
            savedVersion = 0;
            if (!dataFile.isFile()) return;

            YamlConfiguration yaml = new YamlConfiguration();
            try {
                yaml.load(dataFile);
            } catch (IOException | InvalidConfigurationException ex) {
                plugin.getLogger().warning("加载 data/kill_stats.yml 失败，将使用空的内存数据：" + ex.getMessage());
                return;
            }
            var players = yaml.getConfigurationSection(KEY_PLAYERS);
            if (players == null) return;
            for (String playerId : players.getKeys(false)) {
                var levels = players.getConfigurationSection(playerId);
                if (levels == null) continue;
                String storedName = levels.getString(KEY_DISPLAY_NAME);
                if (storedName != null && !storedName.isBlank()) {
                    displayNames.put(playerId, storedName.trim());
                }
                Map<Integer, Integer> byLevel = new HashMap<>();
                for (String levelKey : levels.getKeys(false)) {
                    if (KEY_DISPLAY_NAME.equalsIgnoreCase(levelKey)) continue;
                    try {
                        int level = Integer.parseInt(levelKey);
                        int count = levels.getInt(levelKey, 0);
                        if (level > 0 && count > 0) byLevel.put(level, count);
                    } catch (NumberFormatException ignored) {
                        // 非数字键不属于等级统计。
                    }
                }
                if (!byLevel.isEmpty()) data.put(playerId, byLevel);
            }
        }
    }

    /**
     * 保存当前稳定快照。可从异步定时任务或主线程关闭流程调用；同一服务的保存不会重叠。
     * 只有写盘成功才确认快照版本，失败后下一次调用会重试。
     */
    public void saveIfDirty() {
        synchronized (saveLock) {
            SaveSnapshot snapshot;
            synchronized (dataLock) {
                if (changeVersion == savedVersion) return;
                LinkedHashMap<String, PlayerData> players = new LinkedHashMap<>();
                data.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                    Map<Integer, Integer> levels = new LinkedHashMap<>();
                    entry.getValue().entrySet().stream().sorted(Map.Entry.comparingByKey())
                            .filter(level -> level.getValue() > 0)
                            .forEach(level -> levels.put(level.getKey(), level.getValue()));
                    if (!levels.isEmpty()) {
                        players.put(entry.getKey(), new PlayerData(displayNames.get(entry.getKey()), levels));
                    }
                });
                snapshot = new SaveSnapshot(changeVersion, players);
            }

            try {
                writeSnapshot(snapshot);
                synchronized (dataLock) {
                    savedVersion = Math.max(savedVersion, snapshot.version());
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("保存 data/kill_stats.yml 失败，将在下个周期重试：" + ex.getMessage());
            }
        }
    }

    private void writeSnapshot(SaveSnapshot snapshot) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().header("""
                players 下主键为玩家 UUID。
                每位玩家下: name 为最后已知游戏内名称；数字键为怪物等级 -> 击杀数。
                """);
        var players = yaml.createSection(KEY_PLAYERS);
        snapshot.players().forEach((playerId, playerData) -> {
            var levels = players.createSection(playerId);
            if (playerData.displayName() != null) levels.set(KEY_DISPLAY_NAME, playerData.displayName());
            playerData.levels().forEach((level, count) -> levels.set(String.valueOf(level), count));
        });
        ensureParentDirectory();
        yaml.save(dataFile);
    }

    private void ensureParentDirectory() throws IOException {
        File parent = dataFile.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("无法创建数据目录 " + parent);
        }
    }

    public void addKill(String playerUuid, String displayName, int level) {
        if (playerUuid == null || playerUuid.isBlank()) return;
        synchronized (dataLock) {
            if (displayName != null && !displayName.isBlank()) {
                displayNames.put(playerUuid, displayName.trim());
            }
            data.computeIfAbsent(playerUuid, ignored -> new HashMap<>())
                    .merge(Math.max(1, level), 1, Integer::sum);
            changeVersion++;
        }
    }

    public int getKills(String playerId, int level) {
        synchronized (dataLock) {
            Map<Integer, Integer> levels = data.get(playerId);
            return levels != null ? levels.getOrDefault(level, 0) : 0;
        }
    }

    public Map<Integer, Integer> getKillsByLevel(String playerId) {
        synchronized (dataLock) {
            Map<Integer, Integer> levels = data.get(playerId);
            return levels != null ? Map.copyOf(levels) : Map.of();
        }
    }

    public int getTotalKills(String playerId) {
        synchronized (dataLock) {
            Map<Integer, Integer> levels = data.get(playerId);
            return levels != null ? levels.values().stream().mapToInt(Integer::intValue).sum() : 0;
        }
    }

    public List<PlayerStatsSnapshot> getAllPlayerStats() {
        synchronized (dataLock) {
            List<PlayerStatsSnapshot> snapshots = new ArrayList<>(data.size());
            data.forEach((playerId, levels) -> snapshots.add(new PlayerStatsSnapshot(
                    playerId, displayNames.get(playerId), levels)));
            return List.copyOf(snapshots);
        }
    }

    public void markDirty() {
        synchronized (dataLock) {
            changeVersion++;
        }
    }

    private record SaveSnapshot(long version, Map<String, PlayerData> players) {
        private SaveSnapshot {
            players = Collections.unmodifiableMap(new LinkedHashMap<>(players));
        }
    }

    private record PlayerData(String displayName, Map<Integer, Integer> levels) {
        private PlayerData {
            displayName = displayName != null && !displayName.isBlank() ? displayName.trim() : null;
            levels = Collections.unmodifiableMap(new LinkedHashMap<>(levels));
        }
    }

    public record PlayerStatsSnapshot(String playerId, String displayName,
                                      Map<Integer, Integer> killsByLevel) {
        public PlayerStatsSnapshot {
            playerId = Objects.requireNonNull(playerId, "playerId");
            displayName = displayName != null && !displayName.isBlank() ? displayName.trim() : null;
            killsByLevel = killsByLevel != null ? Map.copyOf(killsByLevel) : Map.of();
        }
    }
}
