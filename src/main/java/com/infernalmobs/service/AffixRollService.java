package com.infernalmobs.service;

import com.infernalmobs.affix.Affix;
import com.infernalmobs.config.ConfigLoader;
import com.infernalmobs.config.RegionConfig;
import com.infernalmobs.registry.SkillRegistry;
import com.infernalmobs.skill.Skill;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 璇嶆潯鎶藉彇鏈嶅姟銆?
 * 璇嶆潯鏁伴噺锛歝ount-formula=level 鏃?n绾鏉★紱鍚﹀垯鐢?tier 鍏紡銆?
 * 鎶?鑳芥睜锛氬尯鍩熸湁 skill-pool 鍒欑敤鍖哄煙锛屽惁鍒欑敤鍏ㄥ眬銆?
 */
public class AffixRollService {

    /** 璇嶆潯闅惧害鍒嗙骇锛?=绠?鍗?1=涓瓑 2=鍥伴毦锛涙湭鏀跺綍鐨勯粯璁ゅ綊绠?鍗曪紙0锛夈??*/
    private static final Map<String, Integer> DIFFICULTY = Map.ofEntries(
            // 绠?鍗?
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
            // 涓瓑
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
            // 鍥伴毦
            Map.entry("rust",        2),
            Map.entry("swap",        2),
            Map.entry("vexsummoner", 2),
            Map.entry("wardenwrath", 2)
    );

    /** 鎸夐毦搴︼紙绠?鍗曗啋鍥伴毦锛夊啀鎸?ID 瀛楁瘝搴忓璇嶆潯鍒楄〃鎺掑簭锛岃繑鍥炴柊鍒楄〃銆?*/
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
     * 鏍规嵁绛夌骇涓庡尯鍩熻绠楄瘝鏉℃暟閲忋??
     * "level" = n 绾?n 鏉★紱"tier" = min + floor(level/tier-threshold)銆?
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
     * 浠庢妧鑳芥睜涓娊鍙栨寚瀹氭暟閲忕殑璇嶆潯銆?
     * region 鏈?skillPool 鍒欑敤鍖哄煙姹狅紝鍚﹀垯鐢ㄥ叏灞? skillWeights銆?
     * 淇濊瘉锛氬悓涓?鍙?墿鍐呬笉閲嶅锛坣 绾ф渶澶?n 涓笉鍚岃瘝鏉★紝鍙楀叏灞? max 涓庢睜澶у皬闄愬埗锛夈??
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

    /**
     * 浠庢妧鑳?ID 鍒楄〃鏋勫缓鍥哄畾璇嶆潯锛堢敤浜庡彫鍞ょ墿绛夛級銆?
     */
    public List<Affix> buildAffixesFromIds(java.util.List<String> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) return Collections.emptyList();
        List<Affix> result = new ArrayList<>();
        for (String id : skillIds) {
            Skill skill = SkillRegistry.get(id);
            if (skill != null) result.add(new Affix(id, skill));
        }
        return sorted(result);
    }

    /**
     * 鎶藉彇璇嶆潯锛岀粨鏋滀腑涓?瀹氬寘鍚寚瀹氱殑鎶?鑳?ID銆?
     *
     * @param requiredSkillIds 蹇呴』鍖呭惈鐨勬妧鑳?ID锛屾棤鏁堟垨閲嶅鐨勪細琚拷鐣?
     */
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

    /**
     * 鎶藉彇璇嶆潯锛岀粨鏋滀腑涓?瀹氫笉鍖呭惈鎸囧畾鐨勬妧鑳?ID銆?
     *
     * @param excludedSkillIds 蹇呴』鎺掗櫎鐨勬妧鑳?ID
     */
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

    /**
     * 浠庨璁炬瀯寤哄浐瀹氳瘝鏉″垪琛ㄣ??
     */
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
