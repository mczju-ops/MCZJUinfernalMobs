package com.infernalmobs.service;

import com.infernalmobs.config.GuaranteedLootConfig;
import com.infernalmobs.config.GuaranteedLootConfig.GuaranteedRule;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 保底进度内存服务。配置可替换，服务实例和已加载进度在插件生命周期内保持稳定。 */
public final class GuaranteedLootService {

    private static final String FILE_NAME = "guaranteed_loot_progress.yml";
    private static final String KEY_PLAYERS = "players";
    private static final String KEY_DISPLAY_NAME = "name";

    private final JavaPlugin plugin;
    private final File dataFile;
    private final Object dataLock = new Object();
    private final Object saveLock = new Object();
    private final Map<String, Map<String, Integer>> progress = new HashMap<>();
    private final Map<String, String> displayNames = new HashMap<>();
    private volatile GuaranteedLootConfig config;
    private long changeVersion;
    private long savedVersion;

    public GuaranteedLootService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(new File(plugin.getDataFolder(), "data"), FILE_NAME);
    }

    public void setConfig(GuaranteedLootConfig config) {
        this.config = config;
    }

    /** 启动时调用一次。配置 reload 不允许再次读取或清空进度。 */
    public void load() {
        synchronized (dataLock) {
            progress.clear();
            displayNames.clear();
            changeVersion = 0;
            savedVersion = 0;
            if (!dataFile.isFile()) return;

            YamlConfiguration yaml = new YamlConfiguration();
            try {
                yaml.load(dataFile);
            } catch (IOException | InvalidConfigurationException ex) {
                plugin.getLogger().warning("加载 data/guaranteed_loot_progress.yml 失败，将使用空的内存数据："
                        + ex.getMessage());
                return;
            }
            var players = yaml.getConfigurationSection(KEY_PLAYERS);
            if (players == null) return;
            for (String playerId : players.getKeys(false)) {
                var rules = players.getConfigurationSection(playerId);
                if (rules == null) continue;
                String storedName = rules.getString(KEY_DISPLAY_NAME);
                if (storedName != null && !storedName.isBlank()) {
                    displayNames.put(playerId, storedName.trim());
                }
                Map<String, Integer> byProgressId = new HashMap<>();
                for (String progressId : rules.getKeys(false)) {
                    if (KEY_DISPLAY_NAME.equalsIgnoreCase(progressId)) continue;
                    byProgressId.put(progressId, rules.getInt(progressId, 0));
                }
                if (!byProgressId.isEmpty()) progress.put(playerId, byProgressId);
            }
        }
    }

    /** 稳定快照保存；失败保留脏版本，同一服务的定时保存和关闭保存不会重叠。 */
    public void saveIfDirty() {
        synchronized (saveLock) {
            SaveSnapshot snapshot;
            synchronized (dataLock) {
                if (changeVersion == savedVersion) return;
                LinkedHashMap<String, PlayerData> players = new LinkedHashMap<>();
                progress.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                    Map<String, Integer> values = new LinkedHashMap<>();
                    entry.getValue().entrySet().stream().sorted(Map.Entry.comparingByKey())
                            .filter(value -> value.getValue() != 0)
                            .forEach(value -> values.put(value.getKey(), value.getValue()));
                    players.put(entry.getKey(), new PlayerData(displayNames.get(entry.getKey()), values));
                });
                snapshot = new SaveSnapshot(changeVersion, players);
            }

            try {
                writeSnapshot(snapshot);
                synchronized (dataLock) {
                    savedVersion = Math.max(savedVersion, snapshot.version());
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("保存 data/guaranteed_loot_progress.yml 失败，将在下个周期重试："
                        + ex.getMessage());
            }
        }
    }

    private void writeSnapshot(SaveSnapshot snapshot) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().header("""
                players 下主键为玩家 UUID。
                每位玩家下: name 为最后已知游戏内名称；其余键为保底进度 id -> 累计进度。
                进度按死亡时等级池的实际抽取次数累计。
                """);
        var players = yaml.createSection(KEY_PLAYERS);
        snapshot.players().forEach((playerId, playerData) -> {
            var rules = players.createSection(playerId);
            if (playerData.displayName() != null) rules.set(KEY_DISPLAY_NAME, playerData.displayName());
            playerData.progress().forEach(rules::set);
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

    public List<GuaranteedRule> collectTriggered(String playerUuid, String displayName,
                                                  int level, int lootRollCount) {
        if (playerUuid == null || playerUuid.isBlank()) return List.of();
        GuaranteedLootConfig currentConfig = config;
        List<GuaranteedRule> triggered = new ArrayList<>();
        synchronized (dataLock) {
            boolean changed = false;
            if (displayName != null && !displayName.isBlank()) {
                String normalizedName = displayName.trim();
                if (!Objects.equals(displayNames.put(playerUuid, normalizedName), normalizedName)) changed = true;
            }
            if (currentConfig == null || !currentConfig.isEnable() || currentConfig.getRules().isEmpty()) {
                if (changed) changeVersion++;
                return List.of();
            }

            int add = Math.max(0, lootRollCount);
            if (add == 0) {
                if (changed) changeVersion++;
                return List.of();
            }

            Set<String> processedProgressIds = new HashSet<>();
            for (GuaranteedRule rule : currentConfig.getRules().values()) {
                if (!currentConfig.isRuleActiveNow(rule) || !currentConfig.appliesToLevel(rule, level)) continue;
                String progressId = rule.progressId == null || rule.progressId.isBlank()
                        ? rule.id : rule.progressId;
                if (!processedProgressIds.add(progressId)) continue;

                Map<String, Integer> playerProgress = progress.computeIfAbsent(
                        playerUuid, ignored -> new HashMap<>());
                int current = playerProgress.getOrDefault(progressId, 0);
                if (current < 0) continue;

                long sum = (long) current + add;
                int next = sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
                playerProgress.put(progressId, next);
                changed = true;
                if (next >= rule.count) {
                    playerProgress.put(progressId, rule.resetOnDrop ? 0 : -1);
                    triggered.add(rule);
                }
            }
            if (changed) changeVersion++;
        }
        return List.copyOf(triggered);
    }

    public Map<String, Integer> getProgressById(String playerId) {
        if (playerId == null || playerId.isBlank()) return Map.of();
        synchronized (dataLock) {
            Map<String, Integer> values = progress.get(playerId);
            return values != null ? Map.copyOf(values) : Map.of();
        }
    }

    public List<GuaranteedRule> getActiveRules() {
        GuaranteedLootConfig currentConfig = config;
        if (currentConfig == null || !currentConfig.isEnable() || currentConfig.getRules().isEmpty()) {
            return List.of();
        }
        return currentConfig.getRules().values().stream()
                .filter(currentConfig::isRuleActiveNow)
                .toList();
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

    private record PlayerData(String displayName, Map<String, Integer> progress) {
        private PlayerData {
            displayName = displayName != null && !displayName.isBlank() ? displayName.trim() : null;
            progress = Collections.unmodifiableMap(new LinkedHashMap<>(progress));
        }
    }
}
