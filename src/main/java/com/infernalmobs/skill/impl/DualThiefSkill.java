package com.infernalmobs.skill.impl;

import com.infernalmobs.InfernalMobsPlugin;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * 缴械：概率召唤悦灵信使追击玩家，命中后夺取玩家当时的主手物品并送回主人。
 */
public class DualThiefSkill implements Skill {

    @Override
    public String getId() {
        return "thief";
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
        Player player = ctx.getTargetPlayer();
        if (player == null || !player.isOnline()) return;

        // 玩家攻击事件在伤害实际结算前触发；若当前最终伤害预计会耗尽怪物生命，跳过缴械。
        // 这里只影响 thief，避免改变其他玩家攻击词条的既有触发时序。
        if (ctx.getTriggerEvent() instanceof EntityDamageEvent damageEvent
                && !damageEvent.isCancelled()
                && ctx.getEntity().getHealth() > 0
                && damageEvent.getFinalDamage() >= ctx.getEntity().getHealth()) {
            return;
        }

        double chance = config.getDouble("chance", 0.10);
        if (Math.random() >= chance) return;
        if (ctx.isWeakened() && Math.random() < 0.5) return;  // 削弱: 概率再减小50%

        if (!(ctx.getPlugin() instanceof InfernalMobsPlugin plugin)) return;
        int cooldownTicks = config.getInt("cooldown-ticks", 80);
        boolean launched = plugin.getThiefCourierTestService().launch(
                player, ctx.getEntity(), ctx.getMobState(), ctx.getOrCreateHandle(),
                ctx.getCurrentTick(), cooldownTicks);
        if (launched) ctx.commitCooldown(getId(), cooldownTicks);
    }
}
