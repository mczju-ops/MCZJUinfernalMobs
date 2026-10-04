package com.infernalmobs.service;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 统一管理绑定到炒鸡怪实体的临时技能会话。
 *
 * 会话不属于 PDC 持久状态；实体注销、区块卸载或插件关闭时都必须结束。
 * 所有调用目前均发生在服务器主线程，因此这里不引入额外的并发模型。
 */
public final class SkillSessionManager {

    private final JavaPlugin plugin;
    private final Map<UUID, Map<String, Runnable>> sessions = new HashMap<>();

    public SkillSessionManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 登记一个实体技能会话；同一实体同一技能只能有一个活动会话。 */
    public void register(UUID entityUuid, String skillId, Runnable cancelAction) {
        if (entityUuid == null || skillId == null || cancelAction == null) return;
        sessions.computeIfAbsent(entityUuid, ignored -> new HashMap<>())
                .put(skillId, cancelAction);
    }

    /** 移除一个已正常结束的会话。 */
    public void unregister(UUID entityUuid, String skillId) {
        if (entityUuid == null || skillId == null) return;
        Map<String, Runnable> entitySessions = sessions.get(entityUuid);
        if (entitySessions == null) return;
        entitySessions.remove(skillId);
        if (entitySessions.isEmpty()) sessions.remove(entityUuid);
    }

    /** 结束指定实体的全部临时技能会话。 */
    public void cancel(UUID entityUuid) {
        if (entityUuid == null) return;
        Map<String, Runnable> entitySessions = sessions.remove(entityUuid);
        if (entitySessions == null) return;
        for (Runnable cancelAction : entitySessions.values()) {
            try {
                cancelAction.run();
            } catch (RuntimeException ex) {
                // 单个技能会话清理失败不能阻断其他技能和实体注销。
                plugin.getLogger().warning("清理实体技能会话失败：「" + ex.getMessage() + "」");
            }
        }
    }

    /** 插件关闭时结束全部临时技能会话。 */
    public void cancelAll() {
        for (UUID entityUuid : sessions.keySet().toArray(UUID[]::new)) {
            cancel(entityUuid);
        }
    }
}
