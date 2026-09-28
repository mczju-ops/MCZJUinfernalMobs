package com.infernalmobs.service;

import com.infernalmobs.config.ConfigLoader;
import com.infernalmobs.config.RegionConfig;
import org.bukkit.Location;

/** 位置到当前配置快照中最终区域规则的查询服务。 */
public final class RegionService {

    private final ConfigLoader config;

    public RegionService(ConfigLoader config) {
        this.config = config;
    }

    public RegionConfig getRegionAt(Location location) {
        for (RegionConfig region : config.currentSnapshot().regions()) {
            if (region.contains(location)) return region;
        }
        return null;
    }
}
