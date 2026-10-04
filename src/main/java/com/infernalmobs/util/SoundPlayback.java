package com.infernalmobs.util;

import com.infernalmobs.config.SoundConfig;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** 统一声音播放入口；声音 ID 由 Adventure Key 表示，不依赖 Bukkit Sound 枚举。 */
public final class SoundPlayback {

    private SoundPlayback() {}

    /** 在玩家当前位置向该玩家播放声音。 */
    public static void play(Player player, SoundConfig sound) {
        if (player == null || !player.isOnline() || sound == null) return;
        player.playSound(player.getLocation(), sound.key().asString(), sound.volume(), sound.pitch());
    }

    /** 在世界指定位置广播声音，范围由服务端客户端声音距离规则决定。 */
    public static void broadcast(Location location, SoundConfig sound) {
        if (location == null || location.getWorld() == null || sound == null) return;
        location.getWorld().playSound(location, sound.key().asString(), sound.volume(), sound.pitch());
    }
}
