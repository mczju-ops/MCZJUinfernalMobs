package com.infernalmobs.service;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** 全服炒鸡怪实体类型击杀统计；按等级记录，并维护所有等级汇总。 */
public final class MobKillStatsService {

    private static final int DATA_VERSION = 1;
    private static final String FILE_NAME = "mob_kill_stats.yml";
    private static final String KEY_ALL_LEVELS = "all-levels";
    private static final String KEY_LEVELS = "levels";

    private final JavaPlugin plugin;
    private final File dataFile;
    private final Object dataLock = new Object();
    private final Object saveLock = new Object();
    private final Map<Integer, Map<String, Long>> killsByLevel = new TreeMap<>();
    private final Map<String, Long> allLevels = new TreeMap<>();
    private long changeVersion;
    private long savedVersion;

    public MobKillStatsService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(new File(plugin.getDataFolder(), "data"), FILE_NAME);
    }

    /** 启动时加载等级明细，并以等级明细重新计算总表。 */
    public void load() {
        synchronized (dataLock) {
            killsByLevel.clear();
            allLevels.clear();
            changeVersion = 0;
            savedVersion = 0;
            if (!dataFile.isFile()) return;

            YamlConfiguration yaml = new YamlConfiguration();
            try {
                yaml.load(dataFile);
            } catch (IOException | InvalidConfigurationException ex) {
                plugin.getLogger().warning("加载 data/mob_kill_stats.yml 失败，将使用空的内存数据：" + ex.getMessage());
                return;
            }
            int version = yaml.getInt("data-version", DATA_VERSION);
            if (version != DATA_VERSION) {
                plugin.getLogger().warning("data/mob_kill_stats.yml 的数据版本不受支持：" + version
                        + "，将使用空的内存数据");
                return;
            }

            ConfigurationSection levelsSection = yaml.getConfigurationSection(KEY_LEVELS);
            if (levelsSection != null) {
                for (String levelKey : levelsSection.getKeys(false)) {
                    int level;
                    try {
                        level = Integer.parseInt(levelKey);
                    } catch (NumberFormatException ignored) {
                        continue;
                    }
                    if (level <= 0) continue;
                    ConfigurationSection typeSection = levelsSection.getConfigurationSection(levelKey);
                    if (typeSection == null) continue;
                    Map<String, Long> byType = readTypeCounts(typeSection);
                    if (!byType.isEmpty()) killsByLevel.put(level, byType);
                }
            }

            killsByLevel.values().forEach(byType -> byType.forEach(
                    (entityType, count) -> allLevels.merge(entityType, count, Long::sum)));
            Map<String, Long> storedTotal = readTypeCounts(yaml.getConfigurationSection(KEY_ALL_LEVELS));
            if (!storedTotal.equals(allLevels)) {
                plugin.getLogger().warning("data/mob_kill_stats.yml 的 all-levels 与等级明细不一致，"
                        + "已按 levels 重新计算并将在下次保存时修正");
                changeVersion = 1;
            }
        }
    }

    private Map<String, Long> readTypeCounts(ConfigurationSection section) {
        Map<String, Long> result = new TreeMap<>();
        if (section == null) return result;
        for (String entityType : section.getKeys(false)) {
            long count = section.getLong(entityType, 0L);
            if (!entityType.isBlank() && count > 0) result.put(entityType, count);
        }
        return result;
    }

    /** 记录一次由玩家造成的炒鸡怪击杀。 */
    public void addKill(EntityType entityType, int level) {
        if (entityType == null) return;
        String typeId = entityType.getKey().toString();
        int normalizedLevel = Math.max(1, level);
        synchronized (dataLock) {
            killsByLevel.computeIfAbsent(normalizedLevel, ignored -> new TreeMap<>())
                    .merge(typeId, 1L, Long::sum);
            allLevels.merge(typeId, 1L, Long::sum);
            changeVersion++;
        }
    }

    /** 获取指定等级的实体类型击杀数快照。 */
    public Map<String, Long> getKillsByLevel(int level) {
        synchronized (dataLock) {
            Map<String, Long> counts = killsByLevel.get(level);
            return counts != null ? Map.copyOf(counts) : Map.of();
        }
    }

    /** 获取所有等级合计的实体类型击杀数快照。 */
    public Map<String, Long> getAllLevels() {
        synchronized (dataLock) {
            return Map.copyOf(allLevels);
        }
    }

    /** 保存稳定快照；写盘失败时保留脏状态，供下一周期重试。 */
    public void saveIfDirty() {
        synchronized (saveLock) {
            SaveSnapshot snapshot;
            synchronized (dataLock) {
                if (changeVersion == savedVersion) return;
                Map<Integer, Map<String, Long>> levels = new LinkedHashMap<>();
                killsByLevel.forEach((level, byType) -> {
                    Map<String, Long> counts = positiveCounts(byType);
                    if (!counts.isEmpty()) levels.put(level, counts);
                });
                snapshot = new SaveSnapshot(changeVersion, summarize(levels), levels);
            }

            try {
                writeSnapshot(snapshot);
                synchronized (dataLock) {
                    savedVersion = Math.max(savedVersion, snapshot.version());
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("保存 data/mob_kill_stats.yml 失败，将在下个周期重试：" + ex.getMessage());
            }
        }
    }

    private Map<String, Long> positiveCounts(Map<String, Long> source) {
        Map<String, Long> result = new LinkedHashMap<>();
        source.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .filter(entry -> entry.getValue() > 0)
                .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    private Map<String, Long> summarize(Map<Integer, Map<String, Long>> levels) {
        Map<String, Long> result = new TreeMap<>();
        levels.values().forEach(byType -> byType.forEach(
                (entityType, count) -> result.merge(entityType, count, Long::sum)));
        return new LinkedHashMap<>(result);
    }

    private void writeSnapshot(SaveSnapshot snapshot) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of("全服由玩家击杀的炒鸡怪数量；实体类型使用完整命名空间 ID。", "八周目从 2026.10.4 开始统计。"));
        yaml.set("data-version", DATA_VERSION);
        ConfigurationSection totalSection = yaml.createSection(KEY_ALL_LEVELS);
        snapshot.allLevels().forEach(totalSection::set);
        ConfigurationSection levelsSection = yaml.createSection(KEY_LEVELS);
        snapshot.levels().forEach((level, byType) -> {
            ConfigurationSection levelSection = levelsSection.createSection(String.valueOf(level));
            byType.forEach(levelSection::set);
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

    private record SaveSnapshot(long version, Map<String, Long> allLevels,
                                Map<Integer, Map<String, Long>> levels) {
        private SaveSnapshot {
            allLevels = Collections.unmodifiableMap(new LinkedHashMap<>(allLevels));
            Map<Integer, Map<String, Long>> copy = new LinkedHashMap<>();
            levels.forEach((level, counts) -> copy.put(level,
                    Collections.unmodifiableMap(new LinkedHashMap<>(counts))));
            levels = Collections.unmodifiableMap(copy);
        }
    }
}
