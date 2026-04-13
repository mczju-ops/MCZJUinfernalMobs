package com.infernalmobs.controller.listener;

import com.infernalmobs.config.ConfigLoader;
import com.infernalmobs.factory.MobFactory;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

/**
 * 监听 {@link CreatureSpawnEvent}，在启用世界且生成原因匹配配置时，对已生成的生物调用 {@link MobFactory#mechanize}。
 * 是否炒鸡化完全由区域 {@code infernal-allow-types} 与 {@code defaults.infernal.allow-types} 白名单决定，无额外硬编码生物表。
 */
public class MobSpawnListener implements Listener {

    private final ConfigLoader config;
    private final MobFactory mobFactory;

    public MobSpawnListener(ConfigLoader config, MobFactory mobFactory) {
        this.config = config;
        this.mobFactory = mobFactory;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;
        LivingEntity entity = (LivingEntity) event.getEntity();
        if (!config.isWorldEnabled(event.getLocation().getWorld().getName())) return;

        // 生成原因可配置（config.yml: infernal-spawn-reasons）
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (!config.getInfernalSpawnReasons().contains(reason)) return;

        // 不取消事件；白名单在 mechanize 内与区域/defaults 一致
        mobFactory.mechanize(entity, event.getLocation());
    }
}
