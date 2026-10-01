package com.infernalmobs.service;

import com.infernalmobs.model.MobState;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 运行中的炒鸡怪状态注册表。
 * 只负责状态登记、注销和稳定快照，不处理实体查找或技能逻辑。
 */
public final class MobRuntimeRegistry {

    private final Map<UUID, MobState> states = new ConcurrentHashMap<>();

    public void register(UUID entityUuid, MobState state) {
        if (entityUuid == null || state == null) return;
        states.put(entityUuid, state);
    }

    public MobState get(UUID entityUuid) {
        return entityUuid == null ? null : states.get(entityUuid);
    }

    public MobState unregister(UUID entityUuid) {
        if (entityUuid == null) return null;
        MobState removed = states.remove(entityUuid);
        if (removed != null) removed.clearPersistentStateListener();
        return removed;
    }

    public int size() {
        return states.size();
    }

    public Map<UUID, MobState> snapshot() {
        return new HashMap<>(states);
    }

    public List<UUID> idsSnapshot() {
        return new ArrayList<>(states.keySet());
    }

    public void clear() {
        for (MobState state : states.values()) state.clearPersistentStateListener();
        states.clear();
    }
}
