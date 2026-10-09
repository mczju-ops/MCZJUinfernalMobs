package com.infernalmobs.config;

import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.List;
import java.util.Set;

/** config.yml 中与具体技能、区域和消息无关的全局行为。 */
public record GlobalConfig(
        int configVersion,
        boolean debug,
        double expMultiplier,
        List<String> enabledWorlds,
        Set<CreatureSpawnEvent.SpawnReason> spawnReasons,
        Set<EntityType> infernalAllowTypes,
        AnimalCleanupConfig animalCleanup
) {
    public GlobalConfig {
        enabledWorlds = List.copyOf(enabledWorlds);
        spawnReasons = Set.copyOf(spawnReasons);
        infernalAllowTypes = Set.copyOf(infernalAllowTypes);
        animalCleanup = animalCleanup != null ? animalCleanup : AnimalCleanupConfig.disabled();
    }
}
