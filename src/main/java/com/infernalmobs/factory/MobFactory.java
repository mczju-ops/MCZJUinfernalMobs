package com.infernalmobs.factory;

import com.infernalmobs.affix.Affix;
import com.infernalmobs.config.ConfigLoader;
import com.infernalmobs.config.DeathMessageConfig;
import com.infernalmobs.config.PresetConfig;
import com.infernalmobs.config.RegionConfig;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.model.MobProfile;
import com.infernalmobs.model.MobState;
import com.infernalmobs.service.AffixRollService;
import com.infernalmobs.service.CombatService;
import com.infernalmobs.service.MobLevelService;
import com.infernalmobs.service.RegionService;
import com.infernalmobs.service.SkillService;
import com.infernalmobs.util.MiniMessageHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 炒鸡怪工厂。根据生成位置匹配区域，计算等级、抽取词条或使用预设，装配技能。
 */
public class MobFactory {

    private static final String IM_LEVEL = "im_level";

    private final JavaPlugin plugin;
    private final ConfigLoader configLoader;
    private final MobLevelService levelService;
    private final AffixRollService affixRollService;
    private final SkillService skillService;
    private final CombatService combatService;
    private final RegionService regionService;

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
    }

    /** 让区域、预设、morph 池在 /im reload 后即时生效。 */
    public void reloadRuntimeConfig() {
        regionService.reload(configLoader.getRegions(), configLoader.getPresets());
    }

    private void setImLevelTag(LivingEntity entity, int level) {
        entity.getPersistentDataContainer().set(
                new NamespacedKey(plugin, IM_LEVEL),
                PersistentDataType.INTEGER,
                level);
    }

    /**
     * 调试：区域匹配与最终等级/词条数（需 config debug: true 或 /im debug on）。
     */
    private void logMechanizeDebug(String path, LivingEntity entity, Location loc,
                                   RegionConfig region, PresetConfig preset, int level, List<Affix> affixes) {
        if (!configLoader.isDebug()) return;
        String world = loc.getWorld() != null ? loc.getWorld().getName() : "?";
        boolean worldOk = loc.getWorld() != null && configLoader.isWorldEnabled(world);
        String regionStr = region != null
                ? region.getId() + " Lv" + region.getLevelMin() + "-" + region.getLevelMax()
                : "(none → fallback " + configLoader.getLevelFallbackMin() + "-" + configLoader.getLevelFallbackMax() + ")";
        String presetStr = preset != null ? preset.getId() : "-";
        int affixCount = affixes != null ? affixes.size() : 0;
        boolean hasMounted = affixes != null && affixes.stream().anyMatch(a -> "mounted".equalsIgnoreCase(a.getSkillId()));
        plugin.getLogger().info(String.format(
                "[InfernalMobs:debug:mechanize] path=%s type=%s world=%s world-enabled=%s block=%d,%d,%d region=%s preset=%s final-level=%d affixes=%d has-mounted=%s",
                path, entity.getType(), world, worldOk,
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                regionStr, presetStr, level, affixCount, hasMounted));
    }

    /**
     * 对已生成的怪物进行炒鸡怪改造。
     */
    public void mechanize(LivingEntity entity, Location spawnLocation) {
        RegionConfig region = regionService.getRegionAt(spawnLocation);
        if (region != null) {
            if (!region.canInfernalize(entity.getType())) return;
        } else {
            // 无区域匹配时，使用 defaults 下的黑白名单
            if (!configLoader.canInfernalizeInDefaults(entity.getType())) return;
        }
        List<EntityType> morphTargets = region != null ? region.getMorphTargetTypes() : null;
        PresetConfig preset = null;
        if (region != null) {
            String worldName = spawnLocation.getWorld() != null ? spawnLocation.getWorld().getName() : "";
            preset = regionService.rollPreset(region.getId(), worldName);
        }

        int level;
        List<Affix> affixes;

        if (preset != null) {
            level = preset.getLevel();
            affixes = affixRollService.fromPreset(preset);
        } else {
            level = levelService.computeLevel(spawnLocation, region);
            int affixCount = affixRollService.computeAffixCount(level, region);
            affixes = affixRollService.rollAffixes(level, affixCount, region);
        }

        logMechanizeDebug("natural", entity, spawnLocation, region, preset, level, affixes);

        MobProfile profile = new MobProfile(level, affixes);
        MobState mobState = new MobState(entity.getUniqueId(), profile, morphTargets);

        skillService.equip(entity, mobState, affixes, this);
        combatService.applyStats(entity, mobState);
        setMobDisplayName(entity, mobState);
        setImLevelTag(entity, level);
        combatService.registerMob(entity.getUniqueId(), mobState);
    }

    /**
     * 使用固定等级炒鸡怪化实体（用于召唤物等）。
     */
    public void mechanizeWithLevel(LivingEntity entity, Location spawnLocation, int fixedLevel) {
        RegionConfig region = regionService.getRegionAt(spawnLocation);
        List<EntityType> morphTargets = region != null ? region.getMorphTargetTypes() : null;
        int affixCount = affixRollService.computeAffixCount(fixedLevel, region);
        List<Affix> affixes = affixRollService.rollAffixes(fixedLevel, affixCount, region);

        logMechanizeDebug("fixed-level", entity, spawnLocation, region, null, fixedLevel, affixes);

        MobProfile profile = new MobProfile(fixedLevel, affixes);
        MobState mobState = new MobState(entity.getUniqueId(), profile, morphTargets);

        skillService.equip(entity, mobState, affixes, this);
        combatService.applyStats(entity, mobState);
        setMobDisplayName(entity, mobState);
        setImLevelTag(entity, fixedLevel);
        combatService.registerMob(entity.getUniqueId(), mobState);
    }

    /**
     * 等级为 n、一定包含指定词条的炒鸡怪。先加入必选词条，其余由池子随机抽取。
     *
     * @param level            等级
     * @param requiredSkillIds 必须包含的技能 ID（无效或未注册的会被忽略）
     */
    public void mechanizeWithRequiredAffixes(LivingEntity entity, Location spawnLocation, int level, List<String> requiredSkillIds) {
        RegionConfig region = regionService.getRegionAt(spawnLocation);
        List<EntityType> morphTargets = region != null ? region.getMorphTargetTypes() : null;
        int affixCount = affixRollService.computeAffixCount(level, region);
        List<Affix> affixes = affixRollService.rollAffixesWithRequired(level, affixCount, region, requiredSkillIds);
        if (affixes.isEmpty()) return;

        logMechanizeDebug("required-affixes", entity, spawnLocation, region, null, level, affixes);

        MobProfile profile = new MobProfile(level, affixes);
        MobState mobState = new MobState(entity.getUniqueId(), profile, morphTargets);

        skillService.equip(entity, mobState, affixes, this);
        combatService.applyStats(entity, mobState);
        setMobDisplayName(entity, mobState);
        setImLevelTag(entity, level);
        combatService.registerMob(entity.getUniqueId(), mobState);
    }

    /**
     * 等级为 n、一定不包含指定词条的炒鸡怪。从技能池排除这些词条后随机抽取。
     *
     * @param level            等级
     * @param excludedSkillIds 必须排除的技能 ID
     */
    public void mechanizeWithExcludedAffixes(LivingEntity entity, Location spawnLocation, int level, List<String> excludedSkillIds) {
        RegionConfig region = regionService.getRegionAt(spawnLocation);
        List<EntityType> morphTargets = region != null ? region.getMorphTargetTypes() : null;
        int affixCount = affixRollService.computeAffixCount(level, region);
        List<Affix> affixes = affixRollService.rollAffixesWithExcluded(level, affixCount, region, excludedSkillIds);
        if (affixes.isEmpty()) return;

        logMechanizeDebug("excluded-affixes", entity, spawnLocation, region, null, level, affixes);

        MobProfile profile = new MobProfile(level, affixes);
        MobState mobState = new MobState(entity.getUniqueId(), profile, morphTargets);

        skillService.equip(entity, mobState, affixes, this);
        combatService.applyStats(entity, mobState);
        setMobDisplayName(entity, mobState);
        setImLevelTag(entity, level);
        combatService.registerMob(entity.getUniqueId(), mobState);
    }

    /**
     * 使用固定技能 ID 列表炒鸡怪化实体（用于 ghost 等召唤物）。
     */
    public void mechanizeWithAffixes(LivingEntity entity, Location spawnLocation, int level, List<String> skillIds) {
        RegionConfig region = regionService.getRegionAt(spawnLocation);
        List<EntityType> morphTargets = region != null ? region.getMorphTargetTypes() : null;

        List<Affix> affixes = affixRollService.buildAffixesFromIds(skillIds);
        if (affixes.isEmpty()) return;

        logMechanizeDebug("fixed-affix-ids", entity, spawnLocation, region, null, level, affixes);

        MobProfile profile = new MobProfile(level, affixes);
        MobState mobState = new MobState(entity.getUniqueId(), profile, morphTargets);

        skillService.equip(entity, mobState, affixes, this);
        combatService.applyStats(entity, mobState);
        setMobDisplayName(entity, mobState);
        setImLevelTag(entity, level);
        combatService.registerMob(entity.getUniqueId(), mobState);
    }

    /**
     * 形态转换：用新类型替换旧实体，保留等级、词条与当前生命值。
     */
    public void morphEntity(LivingEntity oldEntity, MobState oldState, EntityType targetType, double health) {
        if (oldEntity == null || !oldEntity.isValid() || oldState == null) return;
        Location loc = oldEntity.getLocation();
        List<Affix> affixes = oldState.getProfile().getAffixes();

        // 仅抛出“后天拾取”的装备，避免把原生自带装备也掉出来。
        dropPickedUpEquippedItemIfPresent(oldEntity, oldEntity.getUniqueId(), loc, EquipmentSlot.HAND);
        dropPickedUpEquippedItemIfPresent(oldEntity, oldEntity.getUniqueId(), loc, EquipmentSlot.OFF_HAND);
        dropPickedUpEquippedItemIfPresent(oldEntity, oldEntity.getUniqueId(), loc, EquipmentSlot.HEAD);
        dropPickedUpEquippedItemIfPresent(oldEntity, oldEntity.getUniqueId(), loc, EquipmentSlot.CHEST);
        dropPickedUpEquippedItemIfPresent(oldEntity, oldEntity.getUniqueId(), loc, EquipmentSlot.LEGS);
        dropPickedUpEquippedItemIfPresent(oldEntity, oldEntity.getUniqueId(), loc, EquipmentSlot.FEET);

        combatService.unregisterMob(oldEntity.getUniqueId());
        oldEntity.remove();

        LivingEntity newEntity = (LivingEntity) loc.getWorld().spawnEntity(loc, targetType);
        MobState newState = new MobState(newEntity.getUniqueId(), oldState.getProfile(), oldState.getMorphTargetTypesOverride());
        // 继承跨形态持久化状态：1up 使用记录、morph_controller 禁用状态等
        newState.inheritPersistentState(oldState);

        skillService.equip(newEntity, newState, affixes, this);
        combatService.applyStats(newEntity, newState);
        setMobDisplayName(newEntity, newState);
        setImLevelTag(newEntity, oldState.getProfile().getLevel());

        double maxHp = newEntity.getMaxHealth();
        // 直接沿用变形前的绝对生命值，避免因为新生物血量上限不同而“回血”或“掉血”
        newEntity.setHealth(Math.min(maxHp, Math.max(0.1, health)));
        combatService.registerMob(newEntity.getUniqueId(), newState);
    }

    private void dropPickedUpEquippedItemIfPresent(LivingEntity entity, java.util.UUID entityUuid, Location loc, EquipmentSlot slot) {
        EntityEquipment eq = entity.getEquipment();
        if (eq == null || loc.getWorld() == null) return;

        ItemStack inHand = switch (slot) {
            case HAND -> eq.getItemInMainHand();
            case OFF_HAND -> eq.getItemInOffHand();
            case HEAD -> eq.getHelmet();
            case CHEST -> eq.getChestplate();
            case LEGS -> eq.getLeggings();
            case FEET -> eq.getBoots();
            default -> null;
        };
        if (inHand == null || inHand.getType().isAir() || inHand.getAmount() <= 0) return;

        ItemStack toDrop = combatService.consumeMatchedPickedUpItem(entityUuid, inHand);
        if (toDrop == null || toDrop.getType().isAir() || toDrop.getAmount() <= 0) return;

        Item dropped = loc.getWorld().dropItemNaturally(loc, toDrop);
        if (dropped != null) {
            dropped.setInvulnerable(true);
        }
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
        String mobName = dm.getMobDisplayName(entity.getType());
        String color = dm.getLevelTierColor(level);
        String tagName = color.replaceAll("[<>]", "");
        String template = color + "[Lv" + level + "]" + prefix + mobName + "</" + tagName + ">";
        Component nameComponent = MiniMessageHelper.deserialize(template);

        List<Component> affixLines = mobState.getProfile().getAffixes().stream()
                .map(a -> {
                    SkillConfig sc = configLoader.getSkillConfig(a.getSkillId());
                    String display = configLoader.getSkillDisplay(a.getSkillId(), sc);
                    return MiniMessageHelper.parseSkillDisplay(display);
                })
                .collect(Collectors.toList());
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
