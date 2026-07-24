package com.infernalmobs.api.dye;

/**
 * Dye 插件返回的方案结果。
 */
public final class DyeSchemeResult {
    private final String schemeId;
    private final String dropItemId;
    private final String hexColor;

    public DyeSchemeResult(String schemeId, String dropItemId, String hexColor) {
        this.schemeId = schemeId != null ? schemeId : "";
        this.dropItemId = dropItemId != null ? dropItemId : "";
        this.hexColor = hexColor != null ? hexColor : "";
    }

    public String getSchemeId() {
        return schemeId;
    }

    public String getDropItemId() {
        return dropItemId;
    }

    public String getHexColor() {
        return hexColor;
    }
}

