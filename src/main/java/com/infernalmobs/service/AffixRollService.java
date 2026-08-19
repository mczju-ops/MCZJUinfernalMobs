package com.infernalmobs.service;

import com.infernalmobs.affix.Affix;
import com.infernalmobs.config.ConfigLoader;
import com.infernalmobs.config.RegionConfig;
import com.infernalmobs.registry.SkillRegistry;
import com.infernalmobs.skill.Skill;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 词条抽取服务。
 * 词条数量：count-formula=level 时 n级n条；否则用 tier 公式。
 * 技能池：区域有 skill-pool 则用区域，否则用全局。
 */
public class AffixRollService {

    /** 词条难度分级：0=简单 1=中等 2=困难；未收录的默认归简单（0）。 */
    private static final Map<String, Integer> DIFFICULTY = Map.ofEntries(

            Map.entry("1up",         0),
            Map.entry("archer",      0),
            Map.entry("armoured",    0),
            Map.entry("blinding",    0),
            Map.entry("bullwark",    0),
            Map.entry("cloaked",     0),
            Map.entry("confusing",   0),
            Map.entry("ender",       0),
            Map.entry("firework",    0),
            Map.entry("ghastly",     0),
            Map.entry("ghost",       0),
            Map.entry("lifesteal",   0),
            Map.entry("molten",      0),
            Map.entry("necromancer", 0),
            Map.entry("poisonous",   0),
            Map.entry("quicksand",   0),
            Map.entry("sapper",      0),
            Map.entry("sprint",      0),
            Map.entry("webber",      0),
            Map.entry("withering",   0),

            Map.entry("berserk",     1),
            Map.entry("gravity",     1),
            Map.entry("mama",        1),
            Map.entry("morph",       1),
            Map.entry("mounted",     1),
            Map.entry("refrigerate", 1),
            Map.entry("storm",       1),
            Map.entry("thief",       1),
            Map.entry("tosser",      1),
            Map.entry("vengeance",   1),
            Map.entry("weakness",    1),

            Map.entry("rust",        2),
            Map.entry("swap",        2),
            Map.entry("vexsummoner", 2),
            Map.entry("wardenwrath", 2),
            Map.entry("spear",       2),
            Map.entry("sulfur",      2)
    );

    /** 按难度（简单→困难）再按 ID 字母序对词条列表排序，返回新列表。 */
    public static List<Affix> sorted(List<Affix> affixes) {
        return affixes.stream()
                .sorted(Comparator
                        .comparingInt((Affix a) -> DIFFICULTY.getOrDefault(a.getSkillId(), 0))
                        .thenComparing(Affix::getSkillId))
                .collect(java.util.stream.Collectors.toList());
    }

    private final ConfigLoader config;

    public AffixRollService(ConfigLoader config) {
        this.config = config;
    }

    /**
     * 根据等级与区域计算词条数量。
     * "level" = n 级 n 条；"tier" = min + floor(level/tier-threshold)。
     */
    public int computeAffixCount(int level, RegionConfig region) {
        String formula = config.getAffixCountFormula();
        if ("level".equalsIgnoreCase(formula)) {
            return Math.max(config.getAffixMin(), Math.min(level, config.getAffixMax()));
        }
        int tier = level / config.getAffixTierThreshold();
        int count = config.getAffixMin() + tier;
        return Math.min(count, config.getAffixMax());
    }

    /**
     * 从技能池中抽取指定数量的词条。
     * region 有 skillPool 则用区域池，否则用全局 skillWeights。
     * 保证：同一只怪物内不重复（n 级最多 n 个不同词条，受全局 max 与池大小限制）。
     */
    public List<Affix> rollAffixes(int level, int count, RegionConfig region) {
        Map<String, Integer> weights = region != null && !region.getSkillPool().isEmpty()
                ? region.getSkillPool()
                : config.getSkillWeights();
        if (weights.isEmpty()) return Collections.emptyList();

        // 鏋勫缓鍙敤鎶?鑳芥睜
        List<String> ids = new ArrayList<>();
        List<Integer> weightList = new ArrayList<>();
        for (String id : weights.keySet()) {
            if (SkillRegistry.has(id)) {
                ids.add(id);
                weightList.add(weights.getOrDefault(id, 10));
            }
        }
        if (ids.isEmpty()) return Collections.emptyList();

        // 瀹為檯鏁伴噺涓嶈兘瓒呰繃姹犱腑鍙敤鎶?鑳芥暟锛岄伩鍏嶉噸澶?
        int actualCount = Math.min(count, ids.size());

        List<Affix> result = new ArrayList<>();
        for (int i = 0; i < actualCount; i++) {
            String skillId = rollOne(ids, weightList);
            Skill skill = SkillRegistry.get(skillId);
            if (skill == null) continue;
            result.add(new Affix(skillId, skill));

            // 涓嶅啀鍏佽閫夊埌鍚屼竴涓妧鑳斤細绉婚櫎璇ユ潯鐩紝瀹炵幇鈥滄棤鏀惧洖鎶藉彇鈥?
            int idx = ids.indexOf(skillId);
            if (idx >= 0) {
                ids.remove(idx);
                weightList.remove(idx);
            }

            if (ids.isEmpty()) break;
        }
        return sorted(result);
    }

    /** 从技能 ID 列表构建固定词条（用于召唤物等）。 */
    public List<Affix> buildAffixesFromIds(java.util.List<String> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) return Collections.emptyList();
        List<Affix> result = new ArrayList<>();
        for (String id : skillIds) {
            Skill skill = SkillRegistry.get(id);
            if (skill != null) result.add(new Affix(id, skill));
        }
        return sorted(result);
    }

    /** 抽取词条，结果中一定包含指定的技能 ID。 */
    public List<Affix> rollAffixesWithRequired(int level, int count, RegionConfig region, List<String> requiredSkillIds) {
        List<Affix> result = new ArrayList<>();
        Set<String> used = new HashSet<>();

        if (requiredSkillIds != null && !requiredSkillIds.isEmpty()) {
            for (String id : requiredSkillIds) {
                if (used.contains(id)) continue;
                Skill skill = SkillRegistry.get(id);
                if (skill == null) continue;
                result.add(new Affix(id, skill));
                used.add(id);
            }
        }

        int remaining = count - result.size();
        if (remaining <= 0) return result;

        Map<String, Integer> weights = region != null && !region.getSkillPool().isEmpty()
                ? region.getSkillPool()
                : config.getSkillWeights();
        if (weights.isEmpty()) return result;

        List<String> ids = new ArrayList<>();
        List<Integer> weightList = new ArrayList<>();
        for (String id : weights.keySet()) {
            if (SkillRegistry.has(id) && !used.contains(id)) {
                ids.add(id);
                weightList.add(weights.getOrDefault(id, 10));
            }
        }
        if (ids.isEmpty()) return result;

        int actualRemaining = Math.min(remaining, ids.size());
        for (int i = 0; i < actualRemaining; i++) {
            String skillId = rollOne(ids, weightList);
            Skill skill = SkillRegistry.get(skillId);
            if (skill == null) continue;
            result.add(new Affix(skillId, skill));
            int idx = ids.indexOf(skillId);
            if (idx >= 0) {
                ids.remove(idx);
                weightList.remove(idx);
            }
            if (ids.isEmpty()) break;
        }
        return sorted(result);
    }

    /** 抽取词条，结果中一定不包含指定的技能 ID。 */
    public List<Affix> rollAffixesWithExcluded(int level, int count, RegionConfig region, List<String> excludedSkillIds) {
        Set<String> excluded = excludedSkillIds != null && !excludedSkillIds.isEmpty()
                ? new HashSet<>(excludedSkillIds)
                : Collections.emptySet();

        Map<String, Integer> weights = region != null && !region.getSkillPool().isEmpty()
                ? region.getSkillPool()
                : config.getSkillWeights();
        if (weights.isEmpty()) return Collections.emptyList();

        List<String> ids = new ArrayList<>();
        List<Integer> weightList = new ArrayList<>();
        for (String id : weights.keySet()) {
            if (SkillRegistry.has(id) && !excluded.contains(id)) {
                ids.add(id);
                weightList.add(weights.getOrDefault(id, 10));
            }
        }
        if (ids.isEmpty()) return Collections.emptyList();

        int actualCount = Math.min(count, ids.size());
        List<Affix> result = new ArrayList<>();
        for (int i = 0; i < actualCount; i++) {
            String skillId = rollOne(ids, weightList);
            Skill skill = SkillRegistry.get(skillId);
            if (skill == null) continue;
            result.add(new Affix(skillId, skill));
            int idx = ids.indexOf(skillId);
            if (idx >= 0) {
                ids.remove(idx);
                weightList.remove(idx);
            }
            if (ids.isEmpty()) break;
        }
        return sorted(result);
    }

    /** 从预设构建固定词条列表。 */
    public List<Affix> fromPreset(com.infernalmobs.config.PresetConfig preset) {
        List<Affix> result = new ArrayList<>();
        for (com.infernalmobs.config.PresetConfig.SkillEntry e : preset.getSkills()) {
            Skill skill = SkillRegistry.get(e.getId());
            if (skill == null) continue;
            result.add(new Affix(e.getId(), skill));
        }
        return sorted(result);
    }

    private String rollOne(List<String> ids, List<Integer> weights) {
        int total = weights.stream().mapToInt(Integer::intValue).sum();
        if (total <= 0) return ids.get(0);
        int r = ThreadLocalRandom.current().nextInt(total);
        for (int i = 0; i < ids.size(); i++) {
            r -= weights.get(i);
            if (r < 0) return ids.get(i);
        }
        return ids.get(ids.size() - 1);
    }
}
