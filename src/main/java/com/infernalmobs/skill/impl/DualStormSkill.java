package com.infernalmobs.skill.impl;

import com.infernalmobs.api.event.affix.triggered.InfernalMobStormEvent;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import com.infernalmobs.util.Keys;
import com.infernalmobs.util.PdcHandleCodec;
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
        var pdc = lightning.getPersistentDataContainer();
        pdc.set(Keys.STORM_SKILL_ID, org.bukkit.persistence.PersistentDataType.STRING, getId());
        pdc.set(Keys.STORM_SOURCE, org.bukkit.persistence.PersistentDataType.STRING,
                ctx.getEntity().getUniqueId().toString());
        pdc.set(Keys.STORM_LEVEL, org.bukkit.persistence.PersistentDataType.INTEGER, event.getLevel());
        pdc.set(Keys.STORM_DAMAGE, org.bukkit.persistence.PersistentDataType.DOUBLE, event.getDamage());
        PdcHandleCodec.write(pdc, ctx.getOrCreateHandle(), Keys.STORM_HANDLE_AFFIXES,
                Keys.STORM_HANDLE_SUPPRESSED, Keys.STORM_HANDLE_DISPLAY_NAME);
    }
}
