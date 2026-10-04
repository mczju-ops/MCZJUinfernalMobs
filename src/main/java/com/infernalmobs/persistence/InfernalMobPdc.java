package com.infernalmobs.persistence;

import com.infernalmobs.model.MobState;
import com.infernalmobs.registry.SkillRegistry;
import com.infernalmobs.util.Keys;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** 炒鸡怪 PDC 状态的唯一编解码入口。 */
public final class InfernalMobPdc {

    public static final int DATA_VERSION = 1;

    private final JavaPlugin plugin;
    private final Set<UUID> warnedEntities = ConcurrentHashMap.newKeySet();

    public InfernalMobPdc(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void write(LivingEntity entity, MobState state) {
        if (entity == null || state == null) return;
        PersistentDataContainer root = entity.getPersistentDataContainer();
        PersistentDataContainer data = root.getAdapterContext().newPersistentDataContainer();
        data.set(Keys.INFERNAL_DATA_VERSION, PersistentDataType.INTEGER, DATA_VERSION);
        data.set(Keys.INFERNAL_LEVEL, PersistentDataType.INTEGER, state.getProfile().getLevel());
        data.set(Keys.INFERNAL_AFFIXES, PersistentDataType.STRING,
                joinIds(state.getProfile().getAffixIds()));
        data.set(Keys.INFERNAL_SUPPRESSED_AFFIXES, PersistentDataType.STRING,
                joinIds(state.getSuppressedAffixes()));
        data.set(Keys.INFERNAL_USED_ONE_TIME, PersistentDataType.STRING,
                joinIds(state.getUsedOneTime()));
        data.set(Keys.INFERNAL_MORPH_TARGETS, PersistentDataType.STRING,
                state.getMorphTargetTypes().stream()
                        .map(type -> type.name().toLowerCase(Locale.ROOT))
                        .sorted()
                        .reduce((left, right) -> left + "," + right)
                        .orElse(""));
        root.set(Keys.INFERNAL_MOB, PersistentDataType.TAG_CONTAINER, data);
    }

    public ReadResult read(LivingEntity entity) {
        PersistentDataContainer root = entity.getPersistentDataContainer();
        if (!root.has(Keys.INFERNAL_MOB, PersistentDataType.TAG_CONTAINER)) {
            return root.has(Keys.INFERNAL_MOB)
                    ? invalid(entity, "根标记类型不是 TAG_CONTAINER")
                    : ReadResult.absent();
        }

        PersistentDataContainer data = root.get(Keys.INFERNAL_MOB, PersistentDataType.TAG_CONTAINER);
        if (data == null) return invalid(entity, "状态容器无法读取");

        Integer version = data.get(Keys.INFERNAL_DATA_VERSION, PersistentDataType.INTEGER);
        Integer level = data.get(Keys.INFERNAL_LEVEL, PersistentDataType.INTEGER);
        String affixesRaw = data.get(Keys.INFERNAL_AFFIXES, PersistentDataType.STRING);
        String suppressedRaw = data.get(Keys.INFERNAL_SUPPRESSED_AFFIXES, PersistentDataType.STRING);
        String usedRaw = data.get(Keys.INFERNAL_USED_ONE_TIME, PersistentDataType.STRING);
        String morphRaw = data.get(Keys.INFERNAL_MORPH_TARGETS, PersistentDataType.STRING);
        if (version == null || level == null || affixesRaw == null || suppressedRaw == null
                || usedRaw == null || morphRaw == null) {
            return invalid(entity, "状态容器缺少必需字段");
        }
        if (version != DATA_VERSION) {
            return invalid(entity, "不支持的 data-version：" + version);
        }
        if (level < 1) return invalid(entity, "等级必须大于 0");

        List<String> affixes = parseIds(affixesRaw, SkillRegistry::has);
        if (!affixesRaw.isBlank() && affixes.isEmpty()) {
            return invalid(entity, "没有可恢复的有效词条");
        }
        Set<String> affixSet = Set.copyOf(affixes);
        Set<String> suppressed = new LinkedHashSet<>(parseIds(suppressedRaw, affixSet::contains));
        Set<String> usedOneTime = new LinkedHashSet<>(parseIds(usedRaw, _ -> true));
        List<EntityType> morphTargets = parseEntityTypes(morphRaw);

        if (affixes.size() != splitIds(affixesRaw).size()
                || suppressed.size() != splitIds(suppressedRaw).size()
                || usedOneTime.size() != splitIds(usedRaw).size()
                || morphTargets.size() != splitIds(morphRaw).size()) {
            warnOnce(entity, "状态中包含无法识别或重复的 ID，已忽略无效项");
        }
        return ReadResult.valid(new StoredState(level, affixes, suppressed, usedOneTime, morphTargets));
    }

    private ReadResult invalid(LivingEntity entity, String reason) {
        warnOnce(entity, reason + "，实体将保留但不会注册为炒鸡怪");
        return ReadResult.invalid();
    }

    private void warnOnce(LivingEntity entity, String message) {
        if (!warnedEntities.add(entity.getUniqueId())) return;
        plugin.getLogger().warning("炒鸡怪 PDC 异常：" + entity.getType() + "@"
                + entity.getUniqueId() + " - " + message);
    }

    private static String joinIds(Iterable<String> values) {
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            String id = normalizeId(value);
            if (!id.isEmpty()) normalized.add(id);
        }
        return normalized.stream().distinct().sorted().reduce((left, right) -> left + "," + right).orElse("");
    }

    private static List<String> parseIds(String raw, Function<String, Boolean> validator) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String id : splitIds(raw)) {
            if (id.matches("[a-z0-9._-]+") && validator.apply(id)) result.add(id);
        }
        return List.copyOf(result);
    }

    private static List<String> splitIds(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        for (String value : raw.split(",", -1)) {
            String id = normalizeId(value);
            if (!id.isEmpty()) result.add(id);
        }
        return result;
    }

    private static List<EntityType> parseEntityTypes(String raw) {
        LinkedHashSet<EntityType> result = new LinkedHashSet<>();
        for (String id : splitIds(raw)) {
            try {
                EntityType type = EntityType.valueOf(id.toUpperCase(Locale.ROOT));
                if (type.isSpawnable() && type.getEntityClass() != null
                        && LivingEntity.class.isAssignableFrom(type.getEntityClass())) {
                    result.add(type);
                }
            } catch (IllegalArgumentException ignored) {
                // 调用方统一输出一次实体级诊断。
            }
        }
        return List.copyOf(result);
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record StoredState(int level, List<String> affixes, Set<String> suppressedAffixes,
                              Set<String> usedOneTime, List<EntityType> morphTargets) {
        public StoredState {
            affixes = List.copyOf(affixes);
            suppressedAffixes = Set.copyOf(suppressedAffixes);
            usedOneTime = Set.copyOf(usedOneTime);
            morphTargets = List.copyOf(morphTargets);
        }
    }

    public record ReadResult(Status status, StoredState state) {
        public enum Status { ABSENT, VALID, INVALID }

        static ReadResult absent() { return new ReadResult(Status.ABSENT, null); }
        static ReadResult valid(StoredState state) { return new ReadResult(Status.VALID, state); }
        static ReadResult invalid() { return new ReadResult(Status.INVALID, null); }
    }
}
