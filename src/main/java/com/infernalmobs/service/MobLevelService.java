package com.infernalmobs.service;

import com.infernalmobs.config.ConfigLoader;
import com.infernalmobs.config.RegionConfig;
import org.bukkit.Location;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 怪物等级计算服务。
 * 有区域时：在 region.levelMin ~ levelMax 间随机；
 * 无区域时：base + floor(distance/scale)，上限 max。
 */
public class MobLevelService {

    private final ConfigLoader config;

    public MobLevelService(ConfigLoader config) {
        this.config = config;
    }

    /**
     * 计算怪物等级。
     * 有区域：在 levelMin~levelMax 间随机；
     * 无区域：在 fallback-min~fallback-max 间随机（避免世界名不匹配时全部同等级）。
     */
    public int computeLevel(Location spawnLocation, RegionConfig region) {
        if (region != null) {
            int min = region.getLevelMin();
            int max = Math.max(min, region.getLevelMax());
            return min + ThreadLocalRandom.current().nextInt(max - min + 1);
        }
        int min = Math.max(1, config.getLevelFallbackMin());
        int max = Math.max(min, config.getLevelFallbackMax());
        return min + ThreadLocalRandom.current().nextInt(max - min + 1);
    }
}
