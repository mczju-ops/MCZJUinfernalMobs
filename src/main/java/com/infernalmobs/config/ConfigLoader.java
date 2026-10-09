package com.infernalmobs.config;

import com.infernalmobs.registry.SkillRegistry;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 核心配置生命周期入口。解析发生在临时对象中，成功后只通过一次 volatile 写入提交。
 */
public final class ConfigLoader {

    private static final List<String> CORE_FILES = List.of(
            "config.yml", "skills.yml", "regions.yml", "messages.yml");

    private final JavaPlugin plugin;
    private volatile ConfigSnapshot currentSnapshot = safeSnapshot();
    private volatile Boolean debugOverride;

    public ConfigLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 首次安装时写出新版默认文件，随后尝试加载完整快照。 */
    public ConfigLoadResult load() {
        File dataFolder = plugin.getDataFolder();
        boolean freshInstall = !dataFolder.exists();
        if (freshInstall && !dataFolder.mkdirs()) {
            plugin.getLogger().warning("无法创建插件配置目录，将使用安全降级配置");
        }
        if (freshInstall) {
            for (String resource : CORE_FILES) {
                File target = new File(dataFolder, resource);
                if (target.exists()) continue;
                try {
                    plugin.saveResource(resource, false);
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().severe("无法写出默认配置 " + resource + "：" + ex.getMessage());
                }
            }
        }
        return reload();
    }

    /**
     * 完整解析四个核心配置文件。任意根本性错误都会保留旧快照，不提交部分结果。
     */
    public ConfigLoadResult reload() {
        ConfigParser.ParseResult parsed;
        try {
            parsed = new ConfigParser(plugin).parse();
        } catch (RuntimeException ex) {
            ConfigDiagnostic diagnostic = ConfigDiagnostic.error(
                    "核心配置", "解析期间发生未预期错误：" + ex.getMessage());
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "核心配置解析失败，继续使用上一份配置快照", ex);
            return new ConfigLoadResult(false, currentSnapshot.degraded(), List.of(diagnostic));
        }
        if (parsed.snapshot() == null) {
            logDiagnostics(parsed.diagnostics());
            plugin.getLogger().severe("核心配置重载失败，继续使用上一份配置快照");
            return new ConfigLoadResult(false, currentSnapshot.degraded(), parsed.diagnostics());
        }

        currentSnapshot = parsed.snapshot();
        debugOverride = null;
        logDiagnostics(parsed.diagnostics());
        if (parsed.snapshot().degraded()) {
            plugin.getLogger().warning("核心配置已加载，但存在可降级问题；共 "
                    + parsed.diagnostics().size() + " 条诊断");
        } else {
            plugin.getLogger().info("核心配置快照加载完成");
        }
        return new ConfigLoadResult(true, parsed.snapshot().degraded(), parsed.diagnostics());
    }

    public ConfigSnapshot currentSnapshot() {
        return currentSnapshot;
    }

    public boolean isWorldEnabled(String worldName) {
        return currentSnapshot.global().enabledWorlds().contains(worldName);
    }

    public List<String> getEnabledWorlds() {
        return currentSnapshot.global().enabledWorlds();
    }

    public int getLevelFallbackMin() {
        return currentSnapshot.baseSpawnRules().levelWeights().keySet().stream()
                .min(Integer::compareTo).orElse(1);
    }

    public int getLevelFallbackMax() {
        return currentSnapshot.baseSpawnRules().levelWeights().keySet().stream()
                .max(Integer::compareTo).orElse(1);
    }

    public SkillConfig getSkillConfig(String skillId) {
        if (skillId == null) return null;
        return currentSnapshot.skills().get(skillId.toLowerCase(java.util.Locale.ROOT));
    }

    public Map<String, SkillConfig> getSkillConfigs() {
        return currentSnapshot.skills();
    }

    public String getSkillDisplay(String skillId, SkillConfig skillConfig) {
        if (skillConfig != null) return skillConfig.getDisplay();
        return skillId != null ? skillId : "";
    }

    public List<RegionConfig> getRegions() {
        return currentSnapshot.regions();
    }

    public DeathMessageConfig getDeathMessageConfig() {
        return currentSnapshot.deathMessages();
    }

    public ProtectedAnimalsConfig getProtectedAnimalsConfig() {
        return currentSnapshot.protectedAnimals();
    }

    public double getExpMultiplier() {
        return currentSnapshot.global().expMultiplier();
    }

    public AnimalCleanupConfig getAnimalCleanupConfig() {
        return currentSnapshot.global().animalCleanup();
    }

    public Set<CreatureSpawnEvent.SpawnReason> getInfernalSpawnReasons() {
        return currentSnapshot.global().spawnReasons();
    }

    public boolean canInfernalizeInDefaults(EntityType type) {
        Set<EntityType> allowTypes = currentSnapshot.global().infernalAllowTypes();
        return type != null && (allowTypes.isEmpty() || allowTypes.contains(type));
    }

    public boolean isDebug() {
        Boolean override = debugOverride;
        return override != null ? override : currentSnapshot.global().debug();
    }

    public void setDebug(boolean debug) {
        debugOverride = debug;
    }

    private void logDiagnostics(List<ConfigDiagnostic> diagnostics) {
        for (ConfigDiagnostic diagnostic : diagnostics) {
            String message = "[配置] " + diagnostic.path() + " - " + diagnostic.message();
            if (diagnostic.severity() == ConfigDiagnostic.Severity.ERROR) {
                plugin.getLogger().severe(message);
            } else {
                plugin.getLogger().warning(message);
            }
        }
    }

    private static ConfigSnapshot safeSnapshot() {
        LinkedHashMap<String, SkillConfig> skills = new LinkedHashMap<>();
        SkillRegistry.getAll().forEach(skill -> skills.put(
                skill.getId(), new SkillConfig(skill.getId(), skill.getId(), Map.of())));
        GlobalConfig global = new GlobalConfig(
                ConfigParser.CONFIG_VERSION, false, 0, List.of(),
                Set.of(CreatureSpawnEvent.SpawnReason.NATURAL,
                        CreatureSpawnEvent.SpawnReason.SPAWNER),
                Set.of(), AnimalCleanupConfig.disabled());
        return new ConfigSnapshot(global, skills, Map.of("default", Map.of()),
                ConfigParser.safeSpawnRules(), List.of(), ConfigParser.safeDeathMessages(),
                ProtectedAnimalsConfig.disabled(), true,
                List.of(ConfigDiagnostic.warning("核心配置", "当前使用启动安全降级快照")));
    }
}
