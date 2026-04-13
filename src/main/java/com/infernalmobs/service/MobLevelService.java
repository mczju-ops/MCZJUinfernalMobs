package com.infernalmobs.service;

import com.infernalmobs.config.ConfigLoader;
import com.infernalmobs.config.RegionConfig;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 怪物等级计算服务。
 * 优先级（高 → 低）：
 *   1. level-chances  — 单级权重表（最精细）
 *   2. level-ranges   — 带权重的区间段
 *   3. level-min/max  — 区间内均匀随机
 * 无区域时使用全局 defaults.level.chances，没有则 fallbackMin~fallbackMax 均匀随机。
 */
public class MobLevelService {

    private final ConfigLoader config;

    public MobLevelService(ConfigLoader config) {
        this.config = config;
    }

    public int computeLevel(Location spawnLocation, RegionConfig region) {
        if (region != null) {
            // 1. level-chances（单级权重，优先级最高）
            Map<Integer, Integer> chances = region.getLevelChances();
            if (!chances.isEmpty()) {
                return weightedPickLevel(chances);
            }
            // 2. level-ranges（区间权重）
            List<RegionConfig.LevelRange> ranges = region.getLevelRanges();
            if (!ranges.isEmpty()) {
                RegionConfig.LevelRange picked = weightedPickRange(ranges);
                int min = picked.min();
                int max = Math.max(min, picked.max());
                return min + ThreadLocalRandom.current().nextInt(max - min + 1);
            }
            // 3. level-min/max 均匀随机
            int min = region.getLevelMin();
            int max = Math.max(min, region.getLevelMax());
            return min + ThreadLocalRandom.current().nextInt(max - min + 1);
        }

        // 无区域：全局 level-chances
        Map<Integer, Integer> globalChances = config.getGlobalLevelChances();
        if (!globalChances.isEmpty()) {
            return weightedPickLevel(globalChances);
        }
        // 最终退回：fallbackMin~fallbackMax 均匀随机
        int min = Math.max(1, config.getLevelFallbackMin());
        int max = Math.max(min, config.getLevelFallbackMax());
        return min + ThreadLocalRandom.current().nextInt(max - min + 1);
    }

    /** 从单级权重表中加权随机取一个等级。 */
    private static int weightedPickLevel(Map<Integer, Integer> chances) {
        int total = 0;
        for (int w : chances.values()) total += w;
        if (total <= 0) return chances.keySet().iterator().next();
        int roll = ThreadLocalRandom.current().nextInt(total);
        // 按等级升序遍历，保证结果可复现
        List<Map.Entry<Integer, Integer>> entries = new ArrayList<>(chances.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        for (Map.Entry<Integer, Integer> e : entries) {
            roll -= e.getValue();
            if (roll < 0) return e.getKey();
        }
        return entries.get(entries.size() - 1).getKey();
    }

    private static RegionConfig.LevelRange weightedPickRange(List<RegionConfig.LevelRange> ranges) {
        int total = 0;
        for (RegionConfig.LevelRange r : ranges) total += r.weight();
        if (total <= 0) return ranges.get(0);
        int roll = ThreadLocalRandom.current().nextInt(total);
        for (RegionConfig.LevelRange r : ranges) {
            roll -= r.weight();
            if (roll < 0) return r;
        }
        return ranges.get(ranges.size() - 1);
    }
}
