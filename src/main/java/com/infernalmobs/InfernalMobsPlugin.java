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

        if (!new File(getDataFolder(), "loot.yml").exists()) {
            saveResource("loot.yml", false);
        }
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

    /** 加载或重载 loot.yml，并重新挂接 ItemCreatorApi。 */
    public void reloadLootConfig() {
        File lootFile = new File(getDataFolder(), "loot.yml");
        lootConfig = LootConfig.load(lootFile);
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
