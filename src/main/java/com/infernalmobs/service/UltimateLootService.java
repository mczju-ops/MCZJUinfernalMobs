package com.infernalmobs.service;

import com.infernalmobs.config.UltimateLootConfig;
import com.infernalmobs.config.UltimateLootConfig.LootRule;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/** 终极战利品管理：一次自然判定 + 共享加权累计保底。 */
public final class UltimateLootService {
    private static final String FILE_NAME = "ultimate_loot_stats.yml";

    private final JavaPlugin plugin;
    private final File dataFile;
    private final Object dataLock = new Object();
    private final Object saveLock = new Object();
    private final Map<String, Map<String, BigDecimal>> pityPoints = new HashMap<>();
    private final Map<String, Map<String, Integer>> obtained = new HashMap<>();
    private final Map<String, String> displayNames = new HashMap<>();
    private volatile UltimateLootConfig config = UltimateLootConfig.disabled();
    private long changeVersion;
    private long savedVersion;

    public UltimateLootService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(new File(plugin.getDataFolder(), "data"), FILE_NAME);
    }

    public void setConfig(UltimateLootConfig config) {
        this.config = config != null ? config : UltimateLootConfig.disabled();
    }

    public BigDecimal getPityThreshold() {
        return config.pityThreshold();
    }

    public void load() {
        synchronized (dataLock) {
            pityPoints.clear(); obtained.clear(); displayNames.clear(); changeVersion = savedVersion = 0;
            if (!dataFile.isFile()) return;
            YamlConfiguration yaml = new YamlConfiguration();
            try { yaml.load(dataFile); }
            catch (IOException | InvalidConfigurationException ex) {
                plugin.getLogger().warning("加载 data/ultimate_loot_stats.yml 失败，将使用空数据：" + ex.getMessage());
                return;
            }
            var players = yaml.getConfigurationSection("players");
            if (players == null) return;
            for (String playerId : players.getKeys(false)) {
                var player = players.getConfigurationSection(playerId);
                if (player == null) continue;
                String name = player.getString("name");
                if (name != null && !name.isBlank()) displayNames.put(playerId, name.trim());
                var loot = player.getConfigurationSection("loot");
                if (loot == null) continue;
                Map<String, BigDecimal> points = new HashMap<>();
                Map<String, Integer> counts = new HashMap<>();
                for (String lootId : loot.getKeys(false)) {
                    var lootEntry = loot.getConfigurationSection(lootId);
                    if (lootEntry == null) continue;
                    String rawPoints = lootEntry.getString("total-pity-points", "0");
                    try {
                        BigDecimal value = new BigDecimal(rawPoints);
                        points.put(lootId, value.signum() < 0 ? BigDecimal.ZERO : value);
                    }
                    catch (NumberFormatException ex) { points.put(lootId, BigDecimal.ZERO); }
                    counts.put(lootId, Math.max(0, lootEntry.getInt("total-obtained", 0)));
                }
                if (!points.isEmpty()) pityPoints.put(playerId, points);
                if (!counts.isEmpty()) obtained.put(playerId, counts);
            }
        }
    }

    public void saveIfDirty() {
        synchronized (saveLock) {
            SaveSnapshot snapshot;
            synchronized (dataLock) {
                if (changeVersion == savedVersion) return;
                Map<String, PlayerData> players = new LinkedHashMap<>();
                displayNames.keySet().forEach(id -> players.put(id, snapshotFor(id)));
                pityPoints.keySet().forEach(id -> players.putIfAbsent(id, snapshotFor(id)));
                obtained.keySet().forEach(id -> players.putIfAbsent(id, snapshotFor(id)));
                snapshot = new SaveSnapshot(changeVersion, players);
            }
            try {
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.options().header("每种终极战利品独立保存 total-pity-points 与 total-obtained；保底积分不会因掉落清零。");
                var players = yaml.createSection("players");
                snapshot.players.forEach((id, data) -> {
                    var player = players.createSection(id);
                    if (data.name != null) player.set("name", data.name);
                    var loot = player.createSection("loot");
                    data.points.forEach((lootId, value) -> loot.set(lootId + ".total-pity-points", value.toPlainString()));
                    data.obtained.forEach((lootId, value) -> loot.set(lootId + ".total-obtained", value));
                });
                File parent = dataFile.getParentFile();
                if (!parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) throw new IOException("无法创建数据目录 " + parent);
                yaml.save(dataFile);
                synchronized (dataLock) { savedVersion = Math.max(savedVersion, snapshot.version); }
            } catch (Exception ex) {
                plugin.getLogger().warning("保存 data/ultimate_loot_stats.yml 失败，将在下个周期重试：" + ex.getMessage());
            }
        }
    }

    /** 结算一次有效击杀；自然掉落和补发都作为普通掉落返回。 */
    public List<LootAward> settle(String playerId, String displayName, EntityType type,
                                   int level, boolean mamaChild) {
        if (playerId == null || playerId.isBlank()) return List.of();
        synchronized (dataLock) {
            if (displayName != null && !displayName.isBlank()) {
                String normalized = displayName.trim();
                if (!Objects.equals(displayNames.put(playerId, normalized), normalized)) changeVersion++;
            }
            if (!config.isEnabled()) return List.of();
            LootRule rule = config.findRule(type, level, mamaChild);
            if (rule == null) return List.of();

            BigDecimal points = pityPoints.computeIfAbsent(playerId, ignored -> new HashMap<>())
                    .getOrDefault(rule.id, BigDecimal.ZERO);
            BigDecimal nextPoints = points.add(config.pityThreshold().multiply(rule.naturalChance(level)));
            pityPoints.get(playerId).put(rule.id, nextPoints);

            boolean natural = ThreadLocalRandom.current().nextDouble() < rule.naturalChance(level).doubleValue();
            int oldObtained = obtained.computeIfAbsent(playerId, ignored -> new HashMap<>())
                    .getOrDefault(rule.id, 0);
            int naturalAmount = natural ? rule.reward.amount : 0;
            BigInteger guaranteed = nextPoints.divide(config.pityThreshold(), 0, RoundingMode.FLOOR).toBigInteger();
            long missing = guaranteed.subtract(BigInteger.valueOf((long) oldObtained + naturalAmount)).longValue();
            int compensation = (int) Math.max(0, Math.min(Integer.MAX_VALUE, missing));
            changeVersion++;
            int totalAwarded = naturalAmount + compensation;
            if (totalAwarded == 0) return List.of();
            if (naturalAmount > 0 && compensation > 0) {
                return List.of(new LootAward(rule, naturalAmount, false),
                        new LootAward(rule, compensation, true));
            }
            return List.of(new LootAward(rule, totalAwarded, compensation > 0));
        }
    }

    /** 物品成功创建并加入死亡掉落后，提交本次实际获得数量。 */
    public void recordObtained(String playerId, String lootId, int amount) {
        if (playerId == null || playerId.isBlank() || lootId == null || lootId.isBlank() || amount <= 0) return;
        synchronized (dataLock) {
            Map<String, Integer> counts = obtained.computeIfAbsent(playerId, ignored -> new HashMap<>());
            int old = counts.getOrDefault(lootId, 0);
            int next = safeAdd(old, amount);
            if (next != old) {
                counts.put(lootId, next);
                changeVersion++;
            }
        }
    }

    private PlayerData snapshotFor(String playerId) {
        return new PlayerData(displayNames.get(playerId),
                new LinkedHashMap<>(pityPoints.getOrDefault(playerId, Map.of())),
                new LinkedHashMap<>(obtained.getOrDefault(playerId, Map.of())));
    }

    private static int safeAdd(int a, int b) {
        return (int) Math.min(Integer.MAX_VALUE, (long) Math.max(0, a) + Math.max(0, b));
    }

    /** 兼容现有主插件内部查询；新 API 将改为直接暴露积分和累计数量。 */
    public Map<String, Integer> getProgressById(String playerId) {
        synchronized (dataLock) {
            Map<String, Integer> result = new HashMap<>();
            pityPoints.getOrDefault(playerId, Map.of()).forEach((id, value) ->
                    result.put(id, value.setScale(0, RoundingMode.DOWN).intValue()));
            return Map.copyOf(result);
        }
    }

    public Map<String, BigDecimal> getPityPointsById(String playerId) {
        synchronized (dataLock) {
            return Map.copyOf(pityPoints.getOrDefault(playerId, Map.of()));
        }
    }

    public Map<String, LootStats> getStatsById(String playerId) {
        synchronized (dataLock) {
            Map<String, LootStats> result = new HashMap<>();
            Map<String, BigDecimal> points = pityPoints.getOrDefault(playerId, Map.of());
            Map<String, Integer> counts = obtained.getOrDefault(playerId, Map.of());
            points.keySet().forEach(id -> result.put(id,
                    new LootStats(points.getOrDefault(id, BigDecimal.ZERO), counts.getOrDefault(id, 0))));
            counts.forEach((id, count) -> result.putIfAbsent(id,
                    new LootStats(BigDecimal.ZERO, count)));
            return Map.copyOf(result);
        }
    }

    public List<LootRule> getActiveRules() {
        return config.isEnabled() ? List.copyOf(config.getRules().values()) : List.of();
    }

    public void markDirty() { synchronized (dataLock) { changeVersion++; } }

    public record LootAward(LootRule rule, int amount, boolean compensation) {}
    public record LootStats(BigDecimal totalPityPoints, int totalObtained) {}
    private record PlayerData(String name, Map<String, BigDecimal> points, Map<String, Integer> obtained) {}
    private record SaveSnapshot(long version, Map<String, PlayerData> players) {}
}
