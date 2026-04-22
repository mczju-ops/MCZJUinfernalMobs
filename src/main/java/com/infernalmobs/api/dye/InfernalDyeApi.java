package com.infernalmobs.api.dye;

import java.util.Optional;
import java.util.UUID;

/**
 * 由外部 Dye 插件注册的服务接口。
 * InfernalMobs 仅通过该接口请求染色方案与掉落物品 ID，不负责渲染算法。
 */
public interface InfernalDyeApi {

    /**
     * 为指定实体请求一个染色方案。
     *
     * @param request 请求上下文
     * @return 方案结果；为空表示本次不分配方案（调用方可走本地回退）
     */
    Optional<DyeSchemeResult> requestScheme(DyeSchemeRequest request);

    /**
     * 根据方案 ID 解析死亡掉落的 ICA 物品 ID。
     *
     * @param schemeId 方案 ID
     * @param entityId 触发实体 UUID（用于对端校验/统计）
     * @return ICA item id，如 xxx_dye
     */
    Optional<String> resolveDropItemId(String schemeId, UUID entityId);

    /**
     * 可选：通知对端解绑实体记录（死亡/卸载）。
     */
    default void unbindEntity(UUID entityId) {
    }

    /**
     * API 版本号，便于后续演进。
     */
    default int apiVersion() {
        return 1;
    }
}

