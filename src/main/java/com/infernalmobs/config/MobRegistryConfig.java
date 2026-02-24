package com.infernalmobs.config;

/**
 * 炒鸡怪注册表/清理配置。
 */
public record MobRegistryConfig(
        boolean cleanupEnabled,
        int cleanupIntervalSeconds,
        double inactiveRadius,
        int inactiveSeconds,
        boolean killOnShutdown
) {}
