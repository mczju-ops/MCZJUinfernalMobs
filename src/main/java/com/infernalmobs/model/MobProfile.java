package com.infernalmobs.model;

import com.infernalmobs.affix.Affix;

import java.util.ArrayList;
import java.util.List;

/**
 * 怪物的静态配置视图，在生成时确定，包含等级与词条列表。
 */
public class MobProfile {

    private final int level;
    private final List<Affix> affixes;

    public MobProfile(int level, List<Affix> affixes) {
        this.level = level;
        this.affixes = new ArrayList<>(affixes);
    }

    public int getLevel() {
        return level;
    }

    public List<Affix> getAffixes() {
        return affixes;
    }
}
