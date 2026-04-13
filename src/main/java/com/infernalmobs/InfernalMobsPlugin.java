package com.infernalmobs;

import com.infernalmobs.command.InfernalMobCommand;
import com.infernalmobs.config.ConfigLoader;
import com.infernalmobs.config.LootConfig;
import com.infernalmobs.controller.listener.CombatListener;
import com.infernalmobs.controller.listener.CreeperExplodeListener;
import com.infernalmobs.controller.listener.MagicItemListener;
import com.infernalmobs.controller.listener.MobSpawnListener;
import io.mczju.mczjuitemcreator.api.ItemCreatorApi;
import com.infernalmobs.factory.MobFactory;
import com.infernalmobs.service.AffixRollService;
import com.infernalmobs.service.CombatService;
import com.infernalmobs.service.DeathMessageService;
import com.infernalmobs.config.GuaranteedLootConfig;
import com.infernalmobs.service.GuaranteedLootService;
import com.infernalmobs.service.LootService;
import com.infernalmobs.service.KillStatsService;
import com.infernalmobs.service.MagicKingArmorService;
import com.infernalmobs.service.MobLevelService;
import com.infernalmobs.service.RegionService;
import com.infernalmobs.service.SkillService;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * InfernalMobs 主插件类（炒鸡怪）。
 */
public class InfernalMobsPlugin extends JavaPlugin {

    private ConfigLoader configLoader;
    private CombatService combatService;
    private MagicKingArmorService magicKingArmorService;
    private KillStatsService killStatsService;
    private GuaranteedLootService guaranteedLootService;
    private LootConfig lootConfig;
    private LootService lootService;
    private MobFactory mobFactory;

    @Override
    public void onEnable() {
        configLoader = new ConfigLoader(this);
        configLoader.load();

        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        if (!new File(getDataFolder(), "loot.yml").exists()) saveResource("loot.yml", false);
        if (!new File(getDataFolder(), "loot_name.yml").exists()) saveResource("loot_name.yml", false);
        if (!new File(getDataFolder(), "special_loot.yml").exists()) saveResource("special_loot.yml", false);
        if (!new File(getDataFolder(), "guaranteed_loot.yml").exists()) saveResource("guaranteed_loot.yml", false);
        File lootDir = new File(getDataFolder(), "loot");
        if (!lootDir.exists()) lootDir.mkdirs();
        saveDefaultLootFiles(lootDir);
        reloadLootConfig();

        MobLevelService levelService = new MobLevelService(configLoader);
        AffixRollService affixRollService = new AffixRollService(configLoader);
        SkillService skillService = new SkillService(this, configLoader);
        magicKingArmorService = new MagicKingArmorService();
        combatService = new CombatService(this, configLoader);
        combatService.setMagicKingArmorService(magicKingArmorService);
        killStatsService = new KillStatsService(this);
        killStatsService.load();
        DeathMessageService deathMessageService = new DeathMessageService(configLoader);
        deathMessageService.setCombatService(combatService);
        RegionService regionService = new RegionService(configLoader.getRegions(), configLoader.getPresets());

        mobFactory = new MobFactory(this, configLoader, levelService, affixRollService, skillService, combatService, regionService);
        combatService.setMobFactory(mobFactory);

        InfernalMobCommand imCmd = new InfernalMobCommand(this, configLoader, mobFactory, combatService, killStatsService);
        getCommand("im").setExecutor(imCmd);
        getCommand("im").setTabCompleter(imCmd);

        getServer().getPluginManager().registerEvents(new MobSpawnListener(configLoader, mobFactory), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this, combatService, deathMessageService, killStatsService), this);
        getServer().getPluginManager().registerEvents(new MagicItemListener(this, configLoader, combatService), this);
        getServer().getPluginManager().registerEvents(new CreeperExplodeListener(), this);

        combatService.startTickTask();

        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            killStatsService.saveIfDirty();
            if (guaranteedLootService != null) guaranteedLootService.saveIfDirty();
        }, 20 * 60, 20 * 60);

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
            org.bukkit.plugin.Plugin ic = getServer().getPluginManager().getPlugin("MCZJUItemCreator");
            if (ic != null && ic.isEnabled()) {
                getLogger().warning("MCZJUItemCreator 已加载但未注册 ItemCreatorApi，loot 特殊掉落不生效。请在该插件的 onEnable 中调用 ServicesManager.register(ItemCreatorApi.class, 你的实现, plugin, ServicePriority.Normal)");
            } else {
                getLogger().warning("未找到插件 MCZJUItemCreator（或未启用），loot 特殊掉落不生效。请确保 plugin.yml 中 name 为 MCZJUItemCreator，且该插件在 onEnable 中注册 ItemCreatorApi");
            }
        }

        final ItemCreatorApi apiRef = api;
        if (apiRef != null) {
            lootConfig.validateEntries(msg -> getLogger().warning(msg), id -> isItemDefined(apiRef, id));
        }

        lootService = new LootService(this, lootConfig, api);
        guaranteedLootService = new GuaranteedLootService(this);
        guaranteedLootService.load();
        guaranteedLootService.setConfig(GuaranteedLootConfig.load(getDataFolder()));
    }

    /** 运行时重载：配置 + 区域/预设快照 + 掉落配置。 */
    public void reloadRuntimeConfig() {
        configLoader.reload();
        if (mobFactory != null) {
            mobFactory.reloadRuntimeConfig();
        }
        reloadLootConfig();
    }

    private boolean isItemDefined(ItemCreatorApi api, String itemId) {
        if (itemId == null || itemId.isBlank()) return false;
        if (api == null) return false;
        try {
            var m = api.getClass().getMethod("hasItem", String.class);
            Object ret = m.invoke(api, itemId);
            if (ret instanceof Boolean b) return b;
        } catch (Exception ignored) {
            // ????? hasItem ???? API???? createItem(1) ???????
        }
        try {
            Optional<?> opt = api.createItem(itemId, 1);
            return opt != null && opt.isPresent();
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public void onDisable() {
        if (guaranteedLootService != null) guaranteedLootService.saveIfDirty();
        if (killStatsService != null) killStatsService.saveIfDirty();
        if (combatService != null) combatService.cleanupOnShutdown();
        getLogger().info("InfernalMobs 已禁用");
    }

    public ConfigLoader getConfigLoader() {
        return configLoader;
    }

    public CombatService getCombatService() {
        return combatService;
    }

    public MagicKingArmorService getMagicKingArmorService() {
        return magicKingArmorService;
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
}
