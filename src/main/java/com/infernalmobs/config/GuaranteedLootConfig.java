package com.infernalmobs.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

/**
 * 保底掉落配置：从 guaranteed_loot.yml 加载规则。
 */
public class GuaranteedLootConfig {

    private final boolean enable;
    private final Map<String, GuaranteedRule> rules;

    public GuaranteedLootConfig(boolean enable, Map<String, GuaranteedRule> rules) {
        this.enable = enable;
        this.rules = rules != null ? Map.copyOf(rules) : Map.of();
    }

    public boolean isEnable() {
        return enable;
    }

    public Map<String, GuaranteedRule> getRules() {
        return rules;
    }

    public GuaranteedRule getRule(String id) {
        return rules.get(id);
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
        public final int levelMin;
        public final int levelMax;  // -1 表示不限制
        public final int count;
        public final String itemId;
        public final int itemAmount;
        public final boolean resetOnDrop;

        public GuaranteedRule(String id, int levelMin, int levelMax, int count,
                              String itemId, int itemAmount, boolean resetOnDrop) {
            this.id = id;
            this.levelMin = Math.max(1, levelMin);
            this.levelMax = levelMax;
            this.count = Math.max(1, count);
            this.itemId = itemId != null ? itemId : "nether_star";
            this.itemAmount = Math.max(1, itemAmount);
            this.resetOnDrop = resetOnDrop;
        }
    }

    public static GuaranteedLootConfig load(File dataFolder) {
        if (dataFolder == null || !dataFolder.exists()) {
            return new GuaranteedLootConfig(false, Map.of());
        }
        File file = new File(dataFolder, "guaranteed_loot.yml");
        if (!file.isFile()) return new GuaranteedLootConfig(false, Map.of());

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        boolean enable = yml.getBoolean("enable", false);
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
                rules.put(id, new GuaranteedRule(id, levelMin, levelMax, count, itemId, itemAmount, resetOnDrop));
            }
        }
        return new GuaranteedLootConfig(enable, rules);
    }
}
