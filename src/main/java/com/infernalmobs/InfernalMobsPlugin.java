package com.infernalmobs;

import com.infernalmobs.command.InfernalMobCommand;
import com.infernalmobs.config.ConfigLoader;
import com.infernalmobs.config.LootConfig;
import com.infernalmobs.controller.listener.CombatListener;
import com.infernalmobs.controller.listener.CreeperExplodeListener;
import com.infernalmobs.controller.listener.InfernalEyeListener;
import com.infernalmobs.controller.listener.MobSpawnListener;
import com.infernalmobs.external.ItemCreatorApi;
import com.infernalmobs.factory.MobFactory;
import com.infernalmobs.service.AffixRollService;
import com.infernalmobs.service.CombatService;
import com.infernalmobs.service.DeathMessageService;
import com.infernalmobs.service.LootService;
import com.infernalmobs.service.MobLevelService;
import com.infernalmobs.service.RegionService;
import com.infernalmobs.service.SkillService;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * InfernalMobs 主插件类（炒鸡怪）。
 */
public class InfernalMobsPlugin extends JavaPlugin {

    private ConfigLoader configLoader;
    private CombatService combatService;
    private LootConfig lootConfig;
    private LootService lootService;

    @Override
    public void onEnable() {
        configLoader = new ConfigLoader(this);
        configLoader.load();

        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        if (!new File(getDataFolder(), "loot.yml").exists()) saveResource("loot.yml", false);
        File lootDir = new File(getDataFolder(), "loot");
        if (!lootDir.exists()) lootDir.mkdirs();
        saveDefaultLootFiles(lootDir);
        reloadLootConfig();

        MobLevelService levelService = new MobLevelService(configLoader);
        AffixRollService affixRollService = new AffixRollService(configLoader);
        SkillService skillService = new SkillService(this, configLoader);
        combatService = new CombatService(this, configLoader);
        DeathMessageService deathMessageService = new DeathMessageService(configLoader);
        RegionService regionService = new RegionService(configLoader.getRegions(), configLoader.getPresets());

        MobFactory mobFactory = new MobFactory(configLoader, levelService, affixRollService, skillService, combatService, regionService);
        combatService.setMobFactory(mobFactory);

        InfernalMobCommand imCmd = new InfernalMobCommand(this, configLoader, mobFactory, combatService);
        getCommand("im").setExecutor(imCmd);
        getCommand("im").setTabCompleter(imCmd);

        getServer().getPluginManager().registerEvents(new MobSpawnListener(configLoader, mobFactory), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this, combatService, deathMessageService), this);
        getServer().getPluginManager().registerEvents(new InfernalEyeListener(configLoader, combatService), this);
        getServer().getPluginManager().registerEvents(new CreeperExplodeListener(), this);

        combatService.startTickTask();

        getLogger().info("InfernalMobs 已启用");
    }

    /** 遍历插件内 loot/ 下所有 .yml，若数据目录中不存在则写出（支持 jar 与目录运行）。 */
    private void saveDefaultLootFiles(File lootDir) {
        Set<String> paths = new LinkedHashSet<>();
        try {
            URL lootUrl = getClass().getClassLoader().getResource("loot");
            if (lootUrl == null) return;
            if ("file".equals(lootUrl.getProtocol())) {
                File dir = new File(lootUrl.toURI());
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.isFile() && f.getName().toLowerCase().endsWith(".yml"))
                            paths.add("loot/" + f.getName());
                    }
                }
            } else if ("jar".equals(lootUrl.getProtocol())) {
                try (JarFile jar = ((JarURLConnection) lootUrl.openConnection()).getJarFile()) {
                    Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        String name = entries.nextElement().getName();
                        if (name.startsWith("loot/") && name.endsWith(".yml") && !name.endsWith("/"))
                            paths.add(name);
                    }
                }
            }
        } catch (Exception e) {
            getLogger().warning("无法列举 loot 默认文件: " + e.getMessage());
            return;
        }
        for (String path : paths) {
            File out = new File(getDataFolder(), path);
            if (!out.exists()) saveResource(path, false);
        }
    }

    /** 加载或重载 loot 配置（loot.yml + loot/ 下各等级文件），并重新挂接 ItemCreatorApi。 */
    public void reloadLootConfig() {
        lootConfig = LootConfig.load(getDataFolder());
        ItemCreatorApi api = null;
        var rsp = getServer().getServicesManager().getRegistration(ItemCreatorApi.class);
        if (rsp != null) {
            api = rsp.getProvider();
            getLogger().info("已挂接 ItemCreator，炒鸡怪特殊掉落启用");
        } else if (lootConfig.isEnable()) {
            getLogger().warning("未找到 ItemCreator（MCZJUItemCreator），loot.yml 特殊掉落不生效");
        }
        lootService = new LootService(this, lootConfig, api);
    }

    @Override
    public void onDisable() {
        if (combatService != null) combatService.cleanupOnShutdown();
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
}
