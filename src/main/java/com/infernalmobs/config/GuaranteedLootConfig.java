package com.infernalmobs.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Calendar;
import java.util.*;

/**
 * 保底掉落配置：从 guaranteed_loot.yml 加载规则。
 */
public class GuaranteedLootConfig {

    private final boolean enable;
    private final boolean rotationEnable;
    private final int rotationSets;
    private final Map<String, GuaranteedRule> rules;

    public GuaranteedLootConfig(boolean enable, boolean rotationEnable, int rotationSets, Map<String, GuaranteedRule> rules) {
        this.enable = enable;
        this.rotationEnable = rotationEnable;
        this.rotationSets = Math.max(1, rotationSets);
        this.rules = rules != null ? Map.copyOf(rules) : Map.of();
    }

    public boolean isEnable() {
        return enable;
    }

    public boolean isRotationEnable() {
        return rotationEnable;
    }

    public int getRotationSets() {
        return rotationSets;
    }

    public Map<String, GuaranteedRule> getRules() {
        return rules;
    }

    public GuaranteedRule getRule(String id) {
        return rules.get(id);
    }

    /** 某条保底规则在当前激活的轮换套里是否生效。 */
    public boolean isRuleActiveNow(GuaranteedRule rule) {
        if (rule == null) return false;
        if (!rotationEnable) return true;
        // 未配置 rotation-set：默认“始终生效”
        if (rule.rotationSets == null || rule.rotationSets.isEmpty()) return true;
        int activeSet = (Calendar.getInstance().get(Calendar.MONTH) % rotationSets) + 1;
        return rule.rotationSets.contains(activeSet);
    }

    /** 某等级是否被该规则统计 */
    public boolean appliesToLevel(GuaranteedRule rule, int level) {
        if (rule == null) return false;
        if (level < rule.levelMin) return false;
        if (rule.levelMax >= 0 && level > rule.levelMax) return false;
        return true;
    }

    public static final class GuaranteedRule {
        public final String id;
        /** 进度归属（保底条 id）。相同 progressId 的多条规则共享同一份累计进度。 */
        public final String progressId;
        public final int levelMin;
        public final int levelMax;  // -1 表示不限制
        public final int count;
        public final String itemId;
        public final int itemAmount;
        public final boolean resetOnDrop;
        /** 轮换套号集合：null 表示不受轮换影响（始终生效） */
        public final Set<Integer> rotationSets;

        public GuaranteedRule(String id, String progressId, int levelMin, int levelMax, int count,
                              String itemId, int itemAmount, boolean resetOnDrop,
                              Set<Integer> rotationSets) {
            this.id = id;
            this.progressId = (progressId != null && !progressId.isBlank()) ? progressId.trim() : id;
            this.levelMin = Math.max(1, levelMin);
            this.levelMax = levelMax;
            this.count = Math.max(1, count);
            this.itemId = itemId != null ? itemId : "nether_star";
            this.itemAmount = Math.max(1, itemAmount);
            this.resetOnDrop = resetOnDrop;
            this.rotationSets = rotationSets != null && !rotationSets.isEmpty() ? Set.copyOf(rotationSets) : null;
        }
    }

    public static GuaranteedLootConfig load(File dataFolder) {
        if (dataFolder == null || !dataFolder.exists()) {
            return new GuaranteedLootConfig(false, false, 1, Map.of());
        }
        File file = new File(dataFolder, "guaranteed_loot.yml");
        if (!file.isFile()) return new GuaranteedLootConfig(false, false, 1, Map.of());

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        boolean enable = yml.getBoolean("enable", false);

        boolean rotationEnable = false;
        int rotationSets = 3;
        ConfigurationSection rot = yml.getConfigurationSection("rotation");
        if (rot != null) {
            rotationEnable = rot.getBoolean("enable", false);
            rotationSets = rot.getInt("sets", 3);
        }

        Map<String, GuaranteedRule> rules = new LinkedHashMap<>();
        ConfigurationSection rulesSec = yml.getConfigurationSection("rules");
        if (rulesSec != null) {
            for (String id : rulesSec.getKeys(false)) {
                ConfigurationSection r = rulesSec.getConfigurationSection(id);
                if (r == null) continue;
                int levelMin = r.getInt("level-min", 1);
                int levelMax = r.contains("level-max") ? r.getInt("level-max", -1) : -1;
                int count = r.getInt("count", 100);
                String itemId = r.getString("item-id", "nether_star");
                int itemAmount = r.getInt("item-amount", 1);
                boolean resetOnDrop = r.getBoolean("reset-on-drop", true);

                String progressId = null;
                if (r.contains("progress-id")) progressId = r.getString("progress-id");
                else if (r.contains("pity-group")) progressId = r.getString("pity-group");
                else if (r.contains("group-id")) progressId = r.getString("group-id");

                Set<Integer> ruleRotation = null;
                if (r.contains("rotation-set")) {
                    ruleRotation = parseRotationSets(r.get("rotation-set"));
                }

                rules.put(id, new GuaranteedRule(id, progressId, levelMin, levelMax, count,
                        itemId, itemAmount, resetOnDrop, ruleRotation));
            }
        }
        return new GuaranteedLootConfig(enable, rotationEnable, rotationSets, rules);
    }

    /**
     * 解析 rotation-set 节点为整数集合。
     * 支持：int（单值）、List（数组）、string（"1,2" 或 "1 2"）。
     */
    private static Set<Integer> parseRotationSets(Object raw) {
        if (raw == null) return null;
        Set<Integer> out = new HashSet<>();
        if (raw instanceof Number n) {
            out.add(n.intValue());
            return out;
        }
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o == null) continue;
                Integer v = parseRotationSetInt(o);
                if (v != null) out.add(v);
            }
            return out.isEmpty() ? null : out;
        }
        String s = raw.toString().trim();
        if (s.isEmpty()) return null;
        for (String part : s.split("[,\\s]+")) {
            Integer v = parseRotationSetInt(part);
            if (v != null) out.add(v);
        }
        return out.isEmpty() ? null : out;
    }

    private static Integer parseRotationSetInt(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Number n) return n.intValue();
        String s = raw.toString().trim();
        if (s.isEmpty()) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
