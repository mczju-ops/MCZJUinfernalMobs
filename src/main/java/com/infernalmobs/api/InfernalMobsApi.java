package com.infernalmobs.api;

import org.bukkit.entity.LivingEntity;

import java.util.Optional;

/**
 * InfernalMobs 对外 API。由 InfernalMobsPlugin 通过 {@link org.bukkit.plugin.ServicesManager} 注册，
 * 外部插件（如 MagicItems）软依赖本插件后获取：
 * <pre>{@code
 * RegisteredServiceProvider<InfernalMobsApi> rsp =
 *         Bukkit.getServicesManager().getRegistration(InfernalMobsApi.class);
 * InfernalMobsApi api = rsp != null ? rsp.getProvider() : null;
 * }</pre>
 *
 * <p>事件（{@link com.infernalmobs.api.event.InfernalAffixTriggerEvent} 等）由本插件直接
 * 通过 {@link org.bukkit.plugin.PluginManager#callEvent} 广播，无需经此接口。
 */
public interface InfernalMobsApi {

    /** 实体是否已被炒鸡化。 */
    boolean isInfernal(LivingEntity entity);

    /** 获取炒鸡怪的门面句柄（实体未炒鸡化时为空）。 */
    Optional<InfernalMobHandle> getHandle(LivingEntity entity);

    /** API 版本，供依赖方做兼容判断。 */
    default int apiVersion() {
        return 1;
    }
}
