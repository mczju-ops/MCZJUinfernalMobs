package com.infernalmobs.api.dye;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 向 Dye 插件请求方案时的上下文。
 */
public final class DyeSchemeRequest {
    private final UUID entityId;
    private final EntityType entityType;
    private final Location location;
    private final int infernalLevel;
    private final List<String> affixIds;

    public DyeSchemeRequest(UUID entityId, EntityType entityType, Location location, int infernalLevel, List<String> affixIds) {
        this.entityId = entityId;
        this.entityType = entityType;
        this.location = location;
        this.infernalLevel = infernalLevel;
        this.affixIds = affixIds != null ? List.copyOf(affixIds) : List.of();
    }

    public UUID getEntityId() {
        return entityId;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public Location getLocation() {
        return location;
    }

    public int getInfernalLevel() {
        return infernalLevel;
    }

    public List<String> getAffixIds() {
        return Collections.unmodifiableList(affixIds);
    }
}

