package com.infernalmobs.skill.impl;

import com.infernalmobs.api.event.affix.triggered.InfernalMobStormEvent;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.Location;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.Player;

/**
 * 风暴：怪物攻击玩家或玩家攻击怪物时（DUAL），概率在玩家位置召唤闪电（参考原版 InfernalMobs）。
 */
public class DualStormSkill implements Skill {

    @Override
    public String getId() {
        return "storm";
    }

    @Override
    public SkillType getType() {
        return SkillType.DUAL;
    }

    @Override
    public void onEquip(SkillContext ctx, SkillConfig config) {}

    @Override
    public void onUnequip(SkillContext ctx) {}

    @Override
    public void onTrigger(SkillContext ctx, SkillConfig config) {
        Player target = ctx.getTargetPlayer();
        if (target == null || !target.isOnline()) return;
        if (ctx.getEntity().isDead()) return;

        double chance = config.getDouble("chance", 0.22);
        if (Math.random() >= chance) return;
        if (ctx.isWeakened() && Math.random() < 0.5) return;  // 削弱: 概率减小50%

        Location strikeLocation = target.getLocation().clone();
        double damage = config.getDouble("damage", 5.0);
        InfernalMobStormEvent event = new InfernalMobStormEvent(
                ctx.getEntity(), target, ctx.getHandle(), ctx.getMobState().getProfile().getLevel(),
                strikeLocation, damage, false);
        if (!ctx.fire(event)) return;

        Location finalLocation = event.getStrikeLocation().clone();
        if (finalLocation.getWorld() == null) return;

        if (event.isEffectOnly()) {
            finalLocation.getWorld().strikeLightningEffect(finalLocation);
            return;
        }

        LightningStrike lightning = finalLocation.getWorld().strikeLightning(finalLocation);
        lightning.setMetadata("infernalmobs_skill_id",
                new org.bukkit.metadata.FixedMetadataValue(ctx.getPlugin(), getId()));
        lightning.setMetadata("infernalmobs_source",
                new org.bukkit.metadata.FixedMetadataValue(ctx.getPlugin(), ctx.getEntity().getUniqueId()));
        lightning.setMetadata("infernalmobs_storm_handle",
                new org.bukkit.metadata.FixedMetadataValue(ctx.getPlugin(), ctx.getHandle()));
        lightning.setMetadata("infernalmobs_storm_level",
                new org.bukkit.metadata.FixedMetadataValue(ctx.getPlugin(), event.getLevel()));
        lightning.setMetadata("infernalmobs_damage",
                new org.bukkit.metadata.FixedMetadataValue(ctx.getPlugin(), event.getDamage()));
    }
}
