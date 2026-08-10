package com.infernalmobs.api;

import java.util.Optional;

/**
 * 炒鸡怪词条（技能）枚举：对外暴露的全部可用词条 ID，与 config.yml 中 {@code skills.<id>} 及
 * {@link com.infernalmobs.api.event.InfernalAffixTriggerEvent#getAffixId()} 的值一一对应。
 *
 * <p>外部插件可用 {@link #id()} 配合 {@link InfernalMobsApi#spawnInfernalMob} 指定词条，
 * 或在监听事件时与 {@code affixId} 比较（如 {@code event.getAffixId().equals(InfernalAffix.GRAVITY.id())}）。
 */
public enum InfernalAffix {

    ONE_UP("1up"),
    ARMOURED("armoured"),
    BLINDING("blinding"),
    WITHERING("withering"),
    QUICKSAND("quicksand"),
    BULLWARK("bullwark"),
    CLOAKED("cloaked"),
    ENDER("ender"),
    GHASTLY("ghastly"),
    LIFESTEAL("lifesteal"),
    SPRINT("sprint"),
    SAPPER("sapper"),
    WEBBER("webber"),
    MOLTEN("molten"),
    ARCHER("archer"),
    NECROMANCER("necromancer"),
    FIREWORK("firework"),
    GHOST("ghost"),
    DYE("dye"),
    CONFUSING("confusing"),
    THIEF("thief"),
    TOSSER("tosser"),
    STORM("storm"),
    VENGEANCE("vengeance"),
    WEAKNESS("weakness"),
    BERSERK("berserk"),
    MAMA("mama"),
    GRAVITY("gravity"),
    MOUNTED("mounted"),
    SPEAR("spear"),
    SULFUR("sulfur"),
    MORPH("morph"),
    REFRIGERATE("refrigerate"),
    RUST("rust"),
    VEXSUMMONER("vexsummoner"),
    WARDENWRATH("wardenwrath"),
    SWAP("swap");

    private final String id;

    InfernalAffix(String id) {
        this.id = id;
    }

    /** 词条 skillId（与 config.yml 中 skills.&lt;id&gt; 及事件 affixId 一致）。 */
    public String id() {
        return id;
    }

    /** 按 skillId 查找（大小写不敏感）；未找到返回空。 */
    public static Optional<InfernalAffix> fromId(String id) {
        if (id == null) return Optional.empty();
        for (InfernalAffix a : values()) {
            if (a.id.equalsIgnoreCase(id)) return Optional.of(a);
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        return id;
    }
}
