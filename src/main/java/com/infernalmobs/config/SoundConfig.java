package com.infernalmobs.config;

import net.kyori.adventure.key.Key;

/** 插件内部使用的声音播放配置，不属于对外 API 契约。 */
public record SoundConfig(Key key, float volume, float pitch) {

    public SoundConfig {
        if (key == null) throw new NullPointerException("key");
        volume = sanitize(volume);
        pitch = sanitize(pitch);
    }

    private static float sanitize(float value) {
        return Float.isFinite(value) && value >= 0.0f ? value : 0.0f;
    }
}
