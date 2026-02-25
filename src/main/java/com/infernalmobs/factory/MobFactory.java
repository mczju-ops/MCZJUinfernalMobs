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
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
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

    private void setImLevelTag(LivingEntity entity, int level) {
        entity.getPersistentDataContainer().set(
                new NamespacedKey(plugin, IM_LEVEL),
                PersistentDataType.INTEGER,
                level);
    }

    /**
     * 对已生成的怪物进行炒鸡怪改造。
     */
    public void mechanize(LivingEntity entity, Location spawnLocation) {
        RegionConfig region = regionService.getRegionAt(spawnLocation);
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

        MobProfile profile = new MobProfile(level, affixes);
        MobState mobState = new MobState(entity.getUniqueId(), profile);

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
        int affixCount = affixRollService.computeAffixCount(fixedLevel, region);
        List<Affix> affixes = affixRollService.rollAffixes(fixedLevel, affixCount, region);

        MobProfile profile = new MobProfile(fixedLevel, affixes);
        MobState mobState = new MobState(entity.getUniqueId(), profile);

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
        int affixCount = affixRollService.computeAffixCount(level, region);
        List<Affix> affixes = affixRollService.rollAffixesWithRequired(level, affixCount, region, requiredSkillIds);
        if (affixes.isEmpty()) return;

        MobProfile profile = new MobProfile(level, affixes);
        MobState mobState = new MobState(entity.getUniqueId(), profile);

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
        int affixCount = affixRollService.computeAffixCount(level, region);
        List<Affix> affixes = affixRollService.rollAffixesWithExcluded(level, affixCount, region, excludedSkillIds);
        if (affixes.isEmpty()) return;

        MobProfile profile = new MobProfile(level, affixes);
        MobState mobState = new MobState(entity.getUniqueId(), profile);

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
        List<Affix> affixes = affixRollService.buildAffixesFromIds(skillIds);
        if (affixes.isEmpty()) return;

        MobProfile profile = new MobProfile(level, affixes);
        MobState mobState = new MobState(entity.getUniqueId(), profile);

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

        combatService.unregisterMob(oldEntity.getUniqueId());
        oldEntity.remove();

        LivingEntity newEntity = (LivingEntity) loc.getWorld().spawnEntity(loc, targetType);
        MobState newState = new MobState(newEntity.getUniqueId(), oldState.getProfile());

        skillService.equip(newEntity, newState, affixes, this);
        combatService.applyStats(newEntity, newState);
        setMobDisplayName(newEntity, newState);
        setImLevelTag(newEntity, oldState.getProfile().getLevel());

        double maxHp = newEntity.getMaxHealth();
        // 直接沿用变形前的绝对生命值，避免因为新生物血量上限不同而“回血”或“掉血”
        newEntity.setHealth(Math.min(maxHp, Math.max(0.1, health)));
        combatService.registerMob(newEntity.getUniqueId(), newState);
    }

    private static final String NAME_TEMPLATE = "<white>Lv <level> <level_prefix> <mob_name></white>";

    /**
     * 设置怪物头顶显示名（MiniMessage），悬停显示词条列表。
     */
    private void setMobDisplayName(LivingEntity entity, MobState mobState) {
        DeathMessageConfig dm = configLoader.getDeathMessageConfig();
        if (dm == null) return;

        int level = mobState.getProfile().getLevel();
        String levelPrefix = dm.getLevelPrefix(level);
        String mobName = dm.getMobDisplayName(entity.getType());
        Component nameComponent = MiniMessageHelper.deserialize(NAME_TEMPLATE,
                Placeholder.unparsed("level", String.valueOf(level)),
                Placeholder.parsed("level_prefix", levelPrefix),
                Placeholder.unparsed("mob_name", mobName));

        List<Component> affixLines = mobState.getProfile().getAffixes().stream()
                .map(a -> {
                    SkillConfig sc = configLoader.getSkillConfig(a.getSkillId());
                    String display = sc != null ? sc.getDisplay() : a.getSkillId();
                    return MiniMessageHelper.fromLegacy(display);
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
}
