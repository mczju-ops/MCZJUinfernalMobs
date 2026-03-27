package com.infernalmobs.skill.impl;

import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import com.infernalmobs.util.HotbarCharmHelper;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * 投掷：玩家在范围内时，将玩家拉向怪物（参考原版 InfernalMobs）。
 * 条件：非潜行、非创造、非旁观。
 */
public class RangeTosserSkill implements Skill {

    @Override
    public String getId() {
        return "tosser";
    }

    @Override
    public SkillType getType() {
        return SkillType.RANGE;
    }

    @Override
    public void onEquip(SkillContext ctx, SkillConfig config) {}

    @Override
    public void onUnequip(SkillContext ctx) {}

    @Override
    public void onTrigger(SkillContext ctx, SkillConfig config) {
        Player player = ctx.getTargetPlayer();
        if (player == null || !player.isOnline()) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.isSneaking()) return;

        var mobLoc = ctx.getEntity().getLocation();
        Vector toMob = mobLoc.toVector().subtract(player.getLocation().toVector()).setY(0);
        if (toMob.lengthSquared() < 0.01) return;
        toMob.normalize();
        double force = config.getDouble("force", 1.2);
        double up = config.getDouble("upward", 0.2);
        if (ctx.isWeakened()) {  // 削弱: 概率减小50%，力道减小50%
            if (Math.random() < 0.5) return;
            force *= 0.5;
            up *= 0.5;
        }
        // 快捷栏 gravity_charm 抵抗：1/2/3 个 = 30%/60%/100%
        // 放在后面，优先让便宜判定（在线/模式/潜行/距离/削弱随机）先过滤
        if (HotbarCharmHelper.resistedByGravityCharm(player)) return;

        player.setVelocity(toMob.multiply(force).setY(up));

        String soundKey = config.getString("sound", "ENTITY_BREEZE_JUMP");
        try {
            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundKey.toUpperCase().replace(".", "_"));
            player.getWorld().playSound(player.getLocation(), sound, 1f, 0.8f);
        } catch (IllegalArgumentException ignored) {}
    }
}
