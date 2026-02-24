package com.infernalmobs.config;

import com.infernalmobs.skill.SkillType;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.List;

/**
 * 技能配置的只读视图，从 config.yml 中解析。
 * 每种技能可定义自己的参数，此处提供通用存取与类型。
 */
public class SkillConfig {

    private final String skillId;
    private final SkillType type;
    private final String display;
    private final ConfigurationSection section;

    public SkillConfig(String skillId, SkillType type, String display, ConfigurationSection section) {
        this.skillId = skillId;
        this.type = type;
        this.display = display;
        this.section = section;
    }

    public static SkillConfig from(org.bukkit.configuration.file.FileConfiguration config, String path, String skillId) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) return null;

        String typeStr = section.getString("type", "STAT");
        SkillType type = SkillType.valueOf(typeStr.toUpperCase());
        String display = section.getString("display", skillId);

        return new SkillConfig(skillId, type, display, section);
    }

    public String getSkillId() {
        return skillId;
    }

    public SkillType getType() {
        return type;
    }

    public String getDisplay() {
        return display;
    }

    /** 获取原始配置节点，技能实现可自行读取参数 */
    public ConfigurationSection getSection() {
        return section;
    }

    public int getInt(String key, int def) {
        return section != null ? section.getInt(key, def) : def;
    }

    public double getDouble(String key, double def) {
        return section != null ? section.getDouble(key, def) : def;
    }

    public String getString(String key, String def) {
        return section != null ? section.getString(key, def) : def;
    }

    public List<String> getStringList(String key) {
        return section != null ? section.getStringList(key) : Collections.emptyList();
    }

    public boolean getBoolean(String key, boolean def) {
        return section != null ? section.getBoolean(key, def) : def;
    }

    /**
     * 统一的时长读取：支持 -1 表示无限（使用 PotionEffect.INFINITE_DURATION）。
     */
    public int getDurationTicks(String key, int def) {
        int v = getInt(key, def);
        if (v < 0) {
            return org.bukkit.potion.PotionEffect.INFINITE_DURATION;
        }
        return v;
    }
}
