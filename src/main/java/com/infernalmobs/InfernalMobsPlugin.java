package com.infernalmobs;

import com.infernalmobs.api.InfernalMobsApi;
import com.infernalmobs.api.impl.InfernalMobsApiImpl;
import com.infernalmobs.command.InfernalMobCommand;
import com.infernalmobs.config.ConfigLoader;
import com.infernalmobs.config.ConfigLoadResult;
import com.infernalmobs.config.ConfigDiagnostic;
import com.infernalmobs.config.LootConfig;
import com.infernalmobs.config.LootConfigParser;
import com.infernalmobs.controller.listener.CombatListener;
import com.infernalmobs.controller.listener.CreeperExplodeListener;
import com.infernalmobs.controller.listener.MobSpawnListener;
import com.infernalmobs.controller.listener.MobPersistenceListener;
import com.infernalmobs.controller.listener.MorphSuppressListener;
import com.infernalmobs.controller.listener.ThiefResistanceListener;
import com.infernalmobs.factory.MobFactory;
import com.infernalmobs.service.AffixRollService;
import com.infernalmobs.service.CombatService;
import com.infernalmobs.service.DeathMessageService;
import com.infernalmobs.config.GuaranteedLootConfig;
import com.infernalmobs.service.GuaranteedLootService;
import com.infernalmobs.service.LootService;
import com.infernalmobs.service.KillStatsService;
import com.infernalmobs.service.MobLevelService;
import com.infernalmobs.service.RegionService;
import com.infernalmobs.service.SkillService;
import com.infernalmobs.util.ItemCreatorBridge;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * InfernalMobs 主插件类（炒鸡怪）。
 */
public class InfernalMobsPlugin extends JavaPlugin {

    private ConfigLoader configLoader;
    private CombatService combatService;
    private KillStatsService killStatsService;
    private GuaranteedLootService guaranteedLootService;
    private LootConfig lootConfig;
    private LootService lootService;
    private MobFactory mobFactory;
    private SkillService skillService;
    private InfernalMobsApi infernalMobsApi;
    private final Object dataSaveLock = new Object();
    private BukkitTask dataSaveTask;

    @Override
    public void onEnable() {
        boolean freshInstall = !getDataFolder().exists();
        configLoader = new ConfigLoader(this);
        configLoader.load();

        if (freshInstall) saveDefaultLootFiles();
        guaranteedLootService = new GuaranteedLootService(this);
        guaranteedLootService.load();
        loadInitialLootConfig();

        MobLevelService levelService = new MobLevelService(configLoader);
        AffixRollService affixRollService = new AffixRollService(configLoader);
        skillService = new SkillService(this, configLoader);
        combatService = new CombatService(this, configLoader);
        combatService.setSkillService(skillService);
        killStatsService = new KillStatsService(this);
        killStatsService.load();
        DeathMessageService deathMessageService = new DeathMessageService(configLoader);
        deathMessageService.setCombatService(combatService);
        RegionService regionService = new RegionService(configLoader);

        mobFactory = new MobFactory(this, configLoader, levelService, affixRollService, skillService, combatService, regionService);
        combatService.setMobFactory(mobFactory);

        InfernalMobCommand imCmd = new InfernalMobCommand(this, configLoader, mobFactory, combatService, killStatsService);
        getCommand("im").setExecutor(imCmd);
        getCommand("im").setTabCompleter(imCmd);

        getServer().getPluginManager().registerEvents(new MobSpawnListener(configLoader, mobFactory, combatService, this), this);
        getServer().getPluginManager().registerEvents(new MobPersistenceListener(mobFactory), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this, combatService, deathMessageService, killStatsService), this);
        getServer().getPluginManager().registerEvents(new MorphSuppressListener(), this);
        getServer().getPluginManager().registerEvents(new ThiefResistanceListener(), this);
        getServer().getPluginManager().registerEvents(new CreeperExplodeListener(), this);

        int restoredMobs = mobFactory.restoreLoadedEntities();
        if (restoredMobs > 0) getLogger().info("已从 PDC 恢复 " + restoredMobs + " 只炒鸡怪");

        combatService.startTickTask();

        // 注册对外 API，供 MagicItems 等插件通过 ServicesManager 获取
        infernalMobsApi = new InfernalMobsApiImpl(
                combatService,
                mobFactory,
                configLoader,
                this::getLootService,
                killStatsService,
                this::getGuaranteedLootService
        );
        getServer().getServicesManager().register(InfernalMobsApi.class, infernalMobsApi, this, ServicePriority.Normal);

        dataSaveTask = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            synchronized (dataSaveLock) {
                killStatsService.saveIfDirty();
                guaranteedLootService.saveIfDirty();
            }
        }, 20 * 60, 20 * 60);

        getLogger().info("InfernalMobs 已启用");
    }

    /** 只在整个插件目录首次创建时写出新版掉落配置。 */
    private void saveDefaultLootFiles() {
        List<String> paths = new ArrayList<>(List.of("loot/settings.yml", "loot/guaranteed.yml"));
        for (int level = 1; level <= 15; level++) paths.add("loot/levels/" + level + ".yml");
        for (String path : paths) {
            File out = new File(getDataFolder(), path);
            if (!out.exists()) saveResource(path, false);
        }
    }

    /** 解析并提交掉落配置。启动失败时使用关闭掉落的安全快照。 */
    private ConfigLoadResult loadInitialLootConfig() {
        LootConfigParser.ParseResult parsed = new LootConfigParser(getDataFolder()).parse();
        if (!parsed.successful()) {
            logLootDiagnostics(parsed.diagnostics());
            if (lootService == null) commitSafeLootConfig();
            getLogger().severe("掉落配置加载失败，继续使用上一份掉落快照");
            return new ConfigLoadResult(false, true, parsed.diagnostics());
        }
        List<ConfigDiagnostic> diagnostics = new ArrayList<>(parsed.diagnostics());
        diagnostics.addAll(validateLootItemIds(parsed));
        logLootDiagnostics(diagnostics);
        commitLootConfig(parsed);
        boolean degraded = !diagnostics.isEmpty();
        if (degraded) getLogger().warning("掉落配置已加载，但存在可降级问题");
        else getLogger().info("掉落配置快照加载完成");
        return new ConfigLoadResult(true, degraded, diagnostics);
    }

    private void commitLootConfig(LootConfigParser.ParseResult parsed) {
        lootConfig = parsed.lootConfig();
        boolean itemCreatorAvailable = ItemCreatorBridge.isAvailable(this);
        if (itemCreatorAvailable) {
            getLogger().info("已挂接 ItemCreator");
        } else if (lootConfig.isEnable() || lootConfig.getSpecialLootConfig().enable()
                || parsed.guaranteedConfig().isEnable()) {
            org.bukkit.plugin.Plugin ic = getServer().getPluginManager().getPlugin("MCZJUItemCreator");
            if (ic != null && ic.isEnabled()) {
                getLogger().warning("MCZJUItemCreator 已加载但未注册 ItemCreatorApi，loot 特殊掉落不生效。请在该插件的 onEnable 中调用 ServicesManager.register(ItemCreatorApi.class, 你的实现, plugin, ServicePriority.Normal)");
            } else {
                getLogger().warning("未找到插件 MCZJUItemCreator（或未启用），loot 特殊掉落不生效。请确保 plugin.yml 中 name 为 MCZJUItemCreator，且该插件在 onEnable 中注册 ItemCreatorApi");
            }
        }

        lootService = new LootService(this, lootConfig, itemCreatorAvailable);
        guaranteedLootService.setConfig(parsed.guaranteedConfig());
    }

    private void commitSafeLootConfig() {
        lootConfig = LootConfig.disabled();
        lootService = new LootService(this, lootConfig, false);
        guaranteedLootService.setConfig(GuaranteedLootConfig.disabled());
    }

    private List<ConfigDiagnostic> validateLootItemIds(LootConfigParser.ParseResult parsed) {
        if (!ItemCreatorBridge.isAvailable(this)) return List.of();
        LinkedHashSet<String> ids = new LinkedHashSet<>(parsed.lootConfig().configuredItemIds());
        ids.addAll(parsed.guaranteedConfig().configuredItemIds());
        List<ConfigDiagnostic> diagnostics = new ArrayList<>();
        ids.stream().sorted(Comparator.naturalOrder()).forEach(id -> {
            if (ItemCreatorBridge.createItem(this, id, 1).isEmpty()) {
                diagnostics.add(ConfigDiagnostic.warning("loot:物品." + id,
                        "ItemCreator 无法创建该物品，相关奖励运行时会跳过"));
            }
        });
        return List.copyOf(diagnostics);
    }

    private void logLootDiagnostics(List<ConfigDiagnostic> diagnostics) {
        for (var diagnostic : diagnostics) {
            String message = "[配置] " + diagnostic.path() + " - " + diagnostic.message();
            if (diagnostic.severity() == ConfigDiagnostic.Severity.ERROR) {
                getLogger().severe(message);
            } else {
                getLogger().warning(message);
            }
        }
    }

    /** 完整解析核心与掉落配置；任一必需域失败时均保留其旧快照。 */
    public ConfigLoadResult reloadRuntimeConfig() {
        LootConfigParser.ParseResult lootParsed = new LootConfigParser(getDataFolder()).parse();
        if (!lootParsed.successful()) {
            logLootDiagnostics(lootParsed.diagnostics());
            getLogger().severe("配置重载失败，核心与掉落配置均继续使用旧快照");
            return new ConfigLoadResult(false, true, lootParsed.diagnostics());
        }

        ConfigLoadResult coreResult = configLoader.reload();
        List<ConfigDiagnostic> lootDiagnostics = new ArrayList<>(lootParsed.diagnostics());
        lootDiagnostics.addAll(validateLootItemIds(lootParsed));
        List<ConfigDiagnostic> diagnostics = new ArrayList<>(coreResult.diagnostics());
        diagnostics.addAll(lootDiagnostics);
        if (!coreResult.committed()) {
            getLogger().severe("配置重载失败，掉落配置继续使用旧快照");
            return new ConfigLoadResult(false, coreResult.degraded(), diagnostics);
        }

        logLootDiagnostics(lootDiagnostics);
        commitLootConfig(lootParsed);
        return new ConfigLoadResult(true,
                coreResult.degraded() || !lootDiagnostics.isEmpty(), diagnostics);
    }

    @Override
    public void onDisable() {
        if (dataSaveTask != null) dataSaveTask.cancel();
        synchronized (dataSaveLock) {
            if (guaranteedLootService != null) guaranteedLootService.saveIfDirty();
            if (killStatsService != null) killStatsService.saveIfDirty();
        }
        if (mobFactory != null) mobFactory.persistLoadedStates();
        if (combatService != null) combatService.shutdown();
        getLogger().info("InfernalMobs 已禁用");
    }

    public ConfigLoader getConfigLoader() {
        return configLoader;
    }

    public CombatService getCombatService() {
        return combatService;
    }

    public LootService getLootService() {
        return lootService;
    }

    public KillStatsService getKillStatsService() {
        return killStatsService;
    }

    public GuaranteedLootService getGuaranteedLootService() {
        return guaranteedLootService;
    }

    public MobFactory getMobFactory() {
        return mobFactory;
    }

    public SkillService getSkillService() {
        return skillService;
    }

    /** 对外 API（已注册到 ServicesManager）。 */
    public InfernalMobsApi getInfernalMobsApi() {
        return infernalMobsApi;
    }
}
