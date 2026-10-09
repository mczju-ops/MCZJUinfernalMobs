package com.infernalmobs.factory;

import com.infernalmobs.affix.Affix;
import com.infernalmobs.api.InfernalMobHandle;
import com.infernalmobs.api.event.mob.InfernalMobSpawnEvent;
import com.infernalmobs.config.ConfigLoader;
import com.infernalmobs.config.DeathMessageConfig;
import com.infernalmobs.config.RegionConfig;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.model.MobProfile;
import com.infernalmobs.model.MobState;
import com.infernalmobs.persistence.InfernalMobPdc;
import com.infernalmobs.service.AffixRollService;
import com.infernalmobs.service.CombatService;
import com.infernalmobs.service.MobLevelService;
import com.infernalmobs.service.RegionService;
import com.infernalmobs.service.SkillService;
import com.infernalmobs.util.MiniMessageHelper;
import com.infernalmobs.util.GuaranteedEquipmentDrops;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * 炒鸡怪工厂。根据生成位置匹配规则、装配新实体，并恢复已有实体的持久状态。
 */
public class MobFactory {

    private final JavaPlugin plugin;
    private final ConfigLoader configLoader;
    private final MobLevelService levelService;
    private final AffixRollService affixRollService;
    private final SkillService skillService;
    private final CombatService combatService;
    private final RegionService regionService;
    private final InfernalMobPdc persistence;

    public MobFactory(JavaPlugin plugin,
                      ConfigLoader configLoader,
                      MobLevelService levelService,
                      AffixRollService affixRollService,
                      SkillService skillService,
                      CombatService combatService,
                      RegionService regionService) {
        this.plugin = plugin;
        this.configLoader = configLoader;
        this.levelService = levelService;
        this.affixRollService = affixRollService;
        this.skillService = skillService;
        this.combatService = combatService;
        this.regionService = regionService;
        this.persistence = new InfernalMobPdc(plugin);
    }

    /** 按生成位置命中的最终规则，从逐等级权重表抽取等级。 */
    public int computeLevelAt(Location spawnLocation) {
        RegionConfig region = null;
        if (spawnLocation != null && spawnLocation.getWorld() != null) {
            region = regionService.getRegionAt(spawnLocation);
        }
        return levelService.computeLevel(spawnLocation, region);
    }

    /**
     * 调试：区域匹配与最终等级/词条数（需 config debug: true 或 /im debug on）。
     */
    private void logMechanizeDebug(String path, LivingEntity entity, Location loc,
                                   RegionConfig region, int level, List<Affix> affixes) {
        if (!configLoader.isDebug()) return;
        String world = loc.getWorld() != null ? loc.getWorld().getName() : "?";
        boolean worldOk = loc.getWorld() != null && configLoader.isWorldEnabled(world);
        String regionStr = region != null
                ? region.getId() + " Lv" + region.getLevelMin() + "-" + region.getLevelMax()
                : "(none → fallback " + configLoader.getLevelFallbackMin() + "-" + configLoader.getLevelFallbackMax() + ")";
        int affixCount = affixes != null ? affixes.size() : 0;
        boolean hasMounted = affixes != null && affixes.stream().anyMatch(a -> "mounted".equalsIgnoreCase(a.getSkillId()));
        String affixIds = affixes != null
                ? affixes.stream().map(Affix::getSkillId).collect(java.util.stream.Collectors.joining(","))
                : "";
        plugin.getLogger().info(String.format(
                "[InfernalMobs:debug:mechanize] path=%s type=%s world=%s world-enabled=%s block=%d,%d,%d region=%s final-level=%d affixes=%d has-mounted=%s affix-ids=[%s]",
                path, entity.getType(), world, worldOk,
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                regionStr, level, affixCount, hasMounted, affixIds));
    }

    /** 新版只保留全局实体白名单，第一版不提供区域级实体过滤。 */
    private boolean passesInfernalWhitelist(EntityType type) {
        return configLoader.canInfernalizeInDefaults(type);
    }

    /**
     * 对已生成的怪物进行炒鸡怪改造。
     */
    public void mechanize(LivingEntity entity, Location spawnLocation) {
        RegionConfig region = regionService.getRegionAt(spawnLocation);
        if (!passesInfernalWhitelist(entity.getType())) return;
        List<EntityType> morphTargets = getMorphTargets(region);
        int level = levelService.computeLevel(spawnLocation, region);
        int affixCount = affixRollService.computeAffixCount(level, region);
        List<String> excluded = getSkillExclusionsFor(entity.getType());
        List<Affix> affixes;
        if (excluded.isEmpty()) {
            affixes = affixRollService.rollAffixes(level, affixCount, region);
        } else {
            affixes = affixRollService.rollAffixesWithExcluded(level, affixCount, region, excluded);
        }

        logMechanizeDebug("natural", entity, spawnLocation, region, level, affixes);

        completeMechanize(entity, spawnLocation, level, affixes, morphTargets);
    }

    /**
     * 使用固定等级炒鸡怪化实体（用于召唤物等）。
     * 同样受全局白名单约束，不在白名单内的类型直接跳过。
     */
    public void mechanizeWithLevel(LivingEntity entity, Location spawnLocation, int fixedLevel) {
        RegionConfig region = regionService.getRegionAt(spawnLocation);
        if (!passesInfernalWhitelist(entity.getType())) return;
        doMechanizeWithLevel(entity, spawnLocation, fixedLevel, region);
    }

    /**
     * 使用固定等级炒鸡怪化实体，跳过区域白名单检查（用于 infernal-mounts 等已明确指定类型的场景）。
     */
    public void mechanizeWithLevelForced(LivingEntity entity, Location spawnLocation, int fixedLevel) {
        RegionConfig region = regionService.getRegionAt(spawnLocation);
        doMechanizeWithLevel(entity, spawnLocation, fixedLevel, region);
    }

    private void doMechanizeWithLevel(LivingEntity entity, Location spawnLocation, int fixedLevel, RegionConfig region) {
        List<EntityType> morphTargets = getMorphTargets(region);
        int affixCount = affixRollService.computeAffixCount(fixedLevel, region);
        List<String> excluded = getSkillExclusionsFor(entity.getType());
        List<Affix> affixes;
        if (excluded.isEmpty()) {
            affixes = affixRollService.rollAffixes(fixedLevel, affixCount, region);
        } else {
            affixes = affixRollService.rollAffixesWithExcluded(fixedLevel, affixCount, region, excluded);
        }

        logMechanizeDebug("fixed-level", entity, spawnLocation, region, fixedLevel, affixes);

        completeMechanize(entity, spawnLocation, fixedLevel, affixes, morphTargets);
    }

    /**
     * 等级为 n、一定包含指定词条的炒鸡怪。先加入必选词条，其余由池子随机抽取。
     *
     * @param level            等级
     * @param requiredSkillIds 必须包含的技能 ID（无效或未注册的会被忽略）
     */
    public void mechanizeWithRequiredAffixes(LivingEntity entity, Location spawnLocation, int level, List<String> requiredSkillIds) {
        RegionConfig region = regionService.getRegionAt(spawnLocation);
        List<EntityType> morphTargets = getMorphTargets(region);
        int affixCount = affixRollService.computeAffixCount(level, region);
        List<Affix> affixes = filterSupportedAffixes(entity,
                affixRollService.rollAffixesWithRequired(level, affixCount, region, requiredSkillIds));
        if (affixes.isEmpty()) return;

        logMechanizeDebug("required-affixes", entity, spawnLocation, region, level, affixes);

        completeMechanize(entity, spawnLocation, level, affixes, morphTargets);
    }

    /**
     * 等级为 n、一定不包含指定词条的炒鸡怪。从技能池排除这些词条后随机抽取。
     *
     * @param level            等级
     * @param excludedSkillIds 必须排除的技能 ID
     */
    public void mechanizeWithExcludedAffixes(LivingEntity entity, Location spawnLocation, int level, List<String> excludedSkillIds) {
        RegionConfig region = regionService.getRegionAt(spawnLocation);
        List<EntityType> morphTargets = getMorphTargets(region);
        int affixCount = affixRollService.computeAffixCount(level, region);
        List<Affix> affixes = filterSupportedAffixes(entity,
                affixRollService.rollAffixesWithExcluded(level, affixCount, region, excludedSkillIds));
        if (affixes.isEmpty()) return;

        logMechanizeDebug("excluded-affixes", entity, spawnLocation, region, level, affixes);

        completeMechanize(entity, spawnLocation, level, affixes, morphTargets);
    }

    private List<String> getSkillExclusionsFor(EntityType type) {
        List<String> excluded = new ArrayList<>();
        if (type == null) return excluded;
        for (SkillConfig skillConfig : configLoader.getSkillConfigs().values()) {
            if (!skillConfig.isHolderAllowed(type)) {
                excluded.add(skillConfig.getSkillId());
            }
        }
        return excluded;
    }

    private List<Affix> filterSupportedAffixes(LivingEntity entity, List<Affix> affixes) {
        if (entity == null || affixes == null || affixes.isEmpty()) return List.of();
        return affixes.stream()
                .filter(affix -> {
                    SkillConfig skillConfig = configLoader.getSkillConfig(affix.getSkillId());
                    return skillConfig == null || skillConfig.isHolderAllowed(entity.getType());
                })
                .toList();
    }

    private List<EntityType> getMorphTargets(RegionConfig region) {
        return region != null
                ? region.rules().morphPool()
                : configLoader.currentSnapshot().baseSpawnRules().morphPool();
    }

    /**
     * 使用固定技能 ID 列表炒鸡怪化实体（用于 ghost 等召唤物）。
     */
    public void mechanizeWithAffixes(LivingEntity entity, Location spawnLocation, int level, List<String> skillIds) {
        RegionConfig region = regionService.getRegionAt(spawnLocation);
        List<EntityType> morphTargets = getMorphTargets(region);

        List<Affix> affixes = filterSupportedAffixes(entity,
                affixRollService.buildAffixesFromIds(skillIds));
        if (affixes.isEmpty()) return;

        logMechanizeDebug("fixed-affix-ids", entity, spawnLocation, region, level, affixes);

        completeMechanize(entity, spawnLocation, level, affixes, morphTargets);
    }

    /**
     * 炒鸡怪化收尾：在「等级/词条已计算」之后、装配/数值/命名/注册之前广播
     * {@link InfernalMobSpawnEvent}。监听器可取消（阻止炒鸡化，实体保持普通怪），
     * 或通过 handle 修改等级 / 词条 / 显示名——修改会在后续装配与数值计算中生效。
     */
    private void completeMechanize(LivingEntity entity, Location loc, int level, List<Affix> affixes,
                                   List<EntityType> morphTargets) {
        MobProfile profile = new MobProfile(level, affixes);
        MobState mobState = new MobState(entity.getUniqueId(), profile, morphTargets);

        InfernalMobHandle handle = new InfernalMobHandle(entity,
                mobState.getProfile().getLevel(), mobState.getProfile().getAffixIds());
        InfernalMobSpawnEvent spawnEvent = new InfernalMobSpawnEvent(entity, handle, loc, level);
        plugin.getServer().getPluginManager().callEvent(spawnEvent);
        if (spawnEvent.isCancelled()) return;

        // 应用监听器修改（buildAffixesFromIds 与 roll 结果同样按难度+ID 排序，未编辑时顺序不变）
        profile.setLevel(handle.getLevel());
        profile.setAffixes(filterSupportedAffixes(entity,
                affixRollService.buildAffixesFromIds(handle.getAffixIds())));

        skillService.equip(entity, mobState, profile.getAffixes(), this);
        combatService.applyStats(entity, mobState);
        if (handle.getDisplayName() != null) {
            entity.customName(MiniMessageHelper.deserialize(handle.getDisplayName()));
            entity.setCustomNameVisible(true);
        } else {
            setMobDisplayName(entity, mobState);
        }
        attachState(entity, mobState, true);
    }

    /**
     * 形态转换：用新类型替换旧实体，保留等级、词条与当前生命值。
     */
    public void morphEntity(LivingEntity oldEntity, MobState oldState, EntityType targetType, double health) {
        if (oldEntity == null || !oldEntity.isValid() || oldState == null) return;
        Location loc = oldEntity.getLocation();
        List<Affix> affixes = oldState.getProfile().getAffixes();

        // 只抛出原版判定为 100% 掉落的装备，避免变形时玩家拾取物丢失。
        GuaranteedEquipmentDrops.drop(oldEntity, loc);

        combatService.unequipAndUnregister(oldEntity, oldState, SkillService.UnequipReason.MORPH);
        oldEntity.remove();

        LivingEntity newEntity = (LivingEntity) loc.getWorld().spawnEntity(loc, targetType);
        MobState newState = new MobState(newEntity.getUniqueId(), oldState.getProfile(), oldState.getMorphTargetTypes());
        // 继承跨形态持久化状态：1up 使用记录、morph_controller 禁用状态等
        newState.inheritPersistentState(oldState);
        // 幻形任务在下一 tick 执行，此时攻击入口已将本次 morph 冷却写入旧状态。
        newState.inheritCooldowns(oldState);

        skillService.equip(newEntity, newState, affixes, this);
        combatService.applyStats(newEntity, newState);
        setMobDisplayName(newEntity, newState);
        var attribute = newEntity.getAttribute(Attribute.MAX_HEALTH);
        double maxHp = attribute == null ? 20 : attribute.getValue();
        // 直接沿用变形前的绝对生命值，避免因为新生物血量上限不同而“回血”或“掉血”
        newEntity.setHealth(Math.min(maxHp, Math.max(0.1, health)));
        attachState(newEntity, newState, true);
    }

    /** 恢复 PDC 中的静态与跨加载状态，不重复执行技能装配或数值应用。 */
    public boolean restoreFromPdc(LivingEntity entity) {
        if (entity == null || entity instanceof Player || !entity.isValid()) return false;
        if (combatService.getMobState(entity.getUniqueId()) != null) return false;

        InfernalMobPdc.ReadResult result = persistence.read(entity);
        if (result.status() != InfernalMobPdc.ReadResult.Status.VALID) return false;
        InfernalMobPdc.StoredState stored = result.state();
        List<Affix> affixes = filterSupportedAffixes(entity,
                affixRollService.buildAffixesFromIds(stored.affixes()));
        MobProfile profile = new MobProfile(stored.level(), affixes);
        MobState state = new MobState(entity.getUniqueId(), profile, stored.morphTargets());
        state.restorePersistentState(stored.suppressedAffixes(), stored.usedOneTime());
        attachState(entity, state, false);
        return true;
    }

    /** 插件启用时恢复当前已经加载的实体。 */
    public int restoreLoadedEntities() {
        int restored = 0;
        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (restoreFromPdc(entity)) restored++;
            }
        }
        return restored;
    }

    /** 区块卸载前确保状态已同步，然后仅注销内存状态。 */
    public void unregisterForUnload(LivingEntity entity) {
        if (entity == null) return;
        MobState state = combatService.getMobState(entity.getUniqueId());
        if (state == null) return;
        persistence.write(entity, state);
        combatService.unregisterMob(entity.getUniqueId());
    }

    /** 插件关闭前对仍加载的实体再写一次完整快照。 */
    public void persistLoadedStates() {
        combatService.getTrackedMobs().forEach((uuid, state) -> {
            org.bukkit.entity.Entity entity = plugin.getServer().getEntity(uuid);
            if (entity instanceof LivingEntity living) persistence.write(living, state);
        });
    }

    private void attachState(LivingEntity entity, MobState state, boolean writeImmediately) {
        state.setPersistentStateListener(() -> {
            if (entity.isValid()) persistence.write(entity, state);
        });
        if (writeImmediately) persistence.write(entity, state);
        combatService.registerMob(entity.getUniqueId(), state);
    }

    /**
     * 设置怪物头顶显示名：[LvN]前缀+名，按档位着色，悬停显示词条。
     * 被 suppressedAffixes 禁用的词条在悬停文本中加删除线。
     */
    private void setMobDisplayName(LivingEntity entity, MobState mobState) {
        DeathMessageConfig dm = configLoader.getDeathMessageConfig();
        if (dm == null) return;

        int level = mobState.getProfile().getLevel();
        String prefix = dm.getLevelPrefix(level);
        String color = dm.getLevelTierColor(level);
        String tagName = color.replaceAll("[<>]", "");
        String template = color + "[Lv" + level + "]" + prefix + "<mob></" + tagName + ">";
        Component nameComponent = MiniMessageHelper.deserialize(template,
                Placeholder.component("mob", Component.translatable(entity.getType().translationKey())));

        List<Component> affixLines = mobState.getProfile().getAffixes().stream()
                .map(a -> {
                    SkillConfig sc = configLoader.getSkillConfig(a.getSkillId());
                    String display = configLoader.getSkillDisplay(a.getSkillId(), sc);
                    return MiniMessageHelper.parseSkillDisplay(display);
                })
                .toList();
        if (!affixLines.isEmpty()) {
            Component hoverLine = Component.text("词条：");
            for (int i = 0; i < affixLines.size(); i++) {
                if (i > 0) hoverLine = hoverLine.append(Component.text(", "));
                hoverLine = hoverLine.append(affixLines.get(i));
            }
            nameComponent = nameComponent.hoverEvent(HoverEvent.showText(hoverLine));
        }
        entity.customName(nameComponent);
        entity.setCustomNameVisible(true);
    }

    /** 供技能在运行时（如 morph_controller）主动刷新头顶名。 */
    public void refreshDisplayName(LivingEntity entity, MobState mobState) {
        setMobDisplayName(entity, mobState);
    }
}
