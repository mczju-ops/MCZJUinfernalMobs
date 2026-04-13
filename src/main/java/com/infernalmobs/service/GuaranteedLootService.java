package com.infernalmobs.service;

import com.infernalmobs.config.GuaranteedLootConfig;
import com.infernalmobs.config.GuaranteedLootConfig.GuaranteedRule;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 保底掉落服务：按规则累计进度（与单次击杀时等级池 drop-times 的抽取次数一致），达到阈值时发放奖励。
 * 落盘时主键仍为玩家 UUID，每位玩家下额外写入 {@code name} 便于阅读。
 */
public class GuaranteedLootService {

    private static final String PROGRESS_FILE = "guaranteed_loot_progress.yml";
    private static final String KEY_PLAYERS = "players";
    private static final String KEY_DISPLAY_NAME = "name";

    private final JavaPlugin plugin;
    private final File progressFile;
    private GuaranteedLootConfig config;
    /** 玩家 UUID -> (规则 id -> 当前进度)，-1 表示已触发且不重置 */
    private final Map<String, Map<String, Integer>> progress = new ConcurrentHashMap<>();
    /** 玩家 UUID -> 最近一次击杀时的游戏内名称 */
    private final Map<String, String> displayNames = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    public GuaranteedLootService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.progressFile = new File(plugin.getDataFolder(), PROGRESS_FILE);
    }

    public void setConfig(GuaranteedLootConfig config) {
        this.config = config;
    }

    public void load() {
        progress.clear();
        displayNames.clear();
        dirty = false;
        if (!progressFile.exists()) return;
        var yml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(progressFile);
        var playersSec = yml.getConfigurationSection(KEY_PLAYERS);
        if (playersSec == null) return;
        for (String playerId : playersSec.getKeys(false)) {
            var ruleSec = playersSec.getConfigurationSection(playerId);
            if (ruleSec == null) continue;
            String storedName = ruleSec.getString(KEY_DISPLAY_NAME);
            if (storedName != null && !storedName.isBlank()) {
                displayNames.put(playerId, storedName.trim());
            }
            Map<String, Integer> byRule = new ConcurrentHashMap<>();
            for (String ruleId : ruleSec.getKeys(false)) {
                if (KEY_DISPLAY_NAME.equalsIgnoreCase(ruleId)) continue;
                int v = ruleSec.getInt(ruleId, 0);
                byRule.put(ruleId, v);
            }
            if (!byRule.isEmpty()) progress.put(playerId, byRule);
        }
    }

    public void saveIfDirty() {
        if (!dirty) return;
        dirty = false;
        var yml = new org.bukkit.configuration.file.YamlConfiguration();
        yml.options().header("""
                players 下主键为玩家 UUID。
                每位玩家下: name 为落盘时最后已知的游戏内名称（便于阅读）；其余键为保底规则 id -> 累计进度。
                进度按每次击杀时等级池战利品的 drop-times 抽取次数累加（与死亡当次 roll 出的次数一致，未发生等级池抽取时为 0）。
                """);
        var playersSec = yml.createSection(KEY_PLAYERS);
        for (Map.Entry<String, Map<String, Integer>> e : progress.entrySet()) {
            var ruleSec = playersSec.createSection(e.getKey());
            String nm = displayNames.get(e.getKey());
            if (nm != null && !nm.isBlank()) {
                ruleSec.set(KEY_DISPLAY_NAME, nm.trim());
            }
            for (Map.Entry<String, Integer> re : e.getValue().entrySet()) {
                if (re.getValue() != 0) ruleSec.set(re.getKey(), re.getValue());
            }
        }
        try {
            if (!progressFile.getParentFile().exists()) progressFile.getParentFile().mkdirs();
            if (!progressFile.exists()) progressFile.createNewFile();
            yml.save(progressFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("保存 guaranteed_loot_progress.yml 失败: " + ex.getMessage());
        }
    }

    /**
     * 玩家击杀炒鸡怪时调用。检测并累计保底进度，返回本次触发的所有规则列表（可能为空）。
     * 调用方负责根据返回的规则实际生成并掉落物品（通过 LootService.processGuaranteedDrop）。
     *
     * @param playerUuid      玩家 UUID 字符串
     * @param displayName     当前游戏内名称，写入 YAML 的 name 字段（可 null）
     * @param level           怪物等级
     * @param lootRollCount   本次死亡时与等级池战利品抽取次数一致的 roll 结果（未发生抽取时为 0）；累加到保底进度
     * @return 本次触发保底的规则列表（通常为空；多条规则同时触发时包含多个元素）
     */
    public List<GuaranteedRule> collectTriggered(String playerUuid, String displayName, int level, int lootRollCount) {
        List<GuaranteedRule> triggered = new ArrayList<>();
        if (displayName != null && !displayName.isBlank()) {
            displayNames.put(playerUuid, displayName.trim());
        }
        if (config == null || !config.isEnable() || config.getRules().isEmpty()) return triggered;

        int add = Math.max(0, lootRollCount);
        if (add == 0) return triggered;

        // 同一个保底条(progressId)在一次击杀里最多只累计一次，避免配置重复导致进度被叠加。
        Set<String> processedProgressIds = new HashSet<>();
        for (GuaranteedRule rule : config.getRules().values()) {
            if (!config.isRuleActiveNow(rule)) continue;
            if (!config.appliesToLevel(rule, level)) continue;

            String pid = rule.progressId;
            if (pid == null || pid.isBlank()) pid = rule.id;
            if (!processedProgressIds.add(pid)) continue;

            int current = progress.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>())
                    .getOrDefault(pid, 0);
            if (current < 0) continue;  // 已触发且不重置

            int next = current + add;
            progress.get(playerUuid).put(pid, next);
            dirty = true;

            if (next >= rule.count) {
                if (rule.resetOnDrop) {
                    progress.get(playerUuid).put(pid, 0);
                } else {
                    progress.get(playerUuid).put(pid, -1);
                }
                triggered.add(rule);
            }
        }
        return triggered;
    }

    public void markDirty() {
        dirty = true;
    }
}
