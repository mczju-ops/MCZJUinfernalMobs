package com.infernalmobs.config;

/** 远离玩家的炒鸡动物自动清理配置；动物类型由 Bukkit Animals API 判定。 */
public record AnimalCleanupConfig(boolean enabled, int intervalTicks, double maxDistance) {
    public static AnimalCleanupConfig defaults() {
        return new AnimalCleanupConfig(true, 100, 128.0);
    }

    public static AnimalCleanupConfig disabled() {
        return new AnimalCleanupConfig(false, 100, 128.0);
    }
}
