package com.infernalmobs.model;

import com.infernalmobs.affix.Affix;

import java.util.ArrayList;
import java.util.List;

/**
 * 怪物的静态配置视图，在生成时确定，包含等级与词条列表。
 */
public class MobProfile {

    private int level;
    private List<Affix> affixes;

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

    /** 生成事件编辑等级时调用（装配前）。 */
    public void setLevel(int level) {
        this.level = level;
    }

    /** 生成事件编辑词条时调用（装配前）。 */
    public void setAffixes(List<Affix> affixes) {
        this.affixes = new ArrayList<>(affixes);
    }
}
