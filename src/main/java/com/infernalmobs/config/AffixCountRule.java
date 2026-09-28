package com.infernalmobs.config;

/** 新版词条数量规则。数量恒等于等级，再受最小值和最大值限制。 */
public record AffixCountRule(int min, int max) {

    public AffixCountRule {
        min = Math.max(0, min);
        max = Math.max(min, max);
    }

    public int countForLevel(int level) {
        return Math.max(min, Math.min(level, max));
    }
}
