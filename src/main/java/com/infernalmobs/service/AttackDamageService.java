package com.infernalmobs.service;

import com.infernalmobs.affix.Affix;
import com.infernalmobs.model.MobState;
import com.infernalmobs.model.StatMap;
import com.infernalmobs.skill.impl.RangeSpearSkill;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/** 负责怪物攻击玩家时的基础伤害修正和近战特殊命中前置处理。 */
public final class AttackDamageService {

    /**
     * 处理攻击伤害前置逻辑。
     *
     * @return 是否继续触发 ACTIVE/DUAL 技能
     */
    public boolean prepare(EntityDamageByEntityEvent event, LivingEntity damager,
                           Player victim, MobState state) {
        if (event.getCause() == EntityDamageEvent.DamageCause.THORNS) return false;

        double damageBonus = state.getStatMap().get(StatMap.DAMAGE_BONUS);
        if (damageBonus > 0) {
            // getDamage/setDamage 操作原始伤害，保持在护甲结算前应用加成。
            event.setDamage(event.getDamage() + damageBonus);
        }

        for (Affix affix : state.getProfile().getAffixes()) {
            if (affix.getSkill() instanceof RangeSpearSkill spear
                    && spear.handleMeleeHit(event, damager, victim)) {
                break;
            }
        }
        return !event.isCancelled();
    }
}
