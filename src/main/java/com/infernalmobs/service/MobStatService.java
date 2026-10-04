package com.infernalmobs.service;

import com.infernalmobs.model.MobState;
import com.infernalmobs.model.StatMap;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;

/** 负责炒鸡怪的基础属性装配和治疗上限计算。 */
public final class MobStatService {

    /**
     * 忽略原版领头僵尸额外生命后的恢复上限。
     * 普通僵尸的基础生命为 20；领头僵尸可能额外获得 20 至 80 点生命，若随炒鸡等级一起放大，
     * 再生、吸血和 1up 会把这部分随机加成也恢复满，因此统一按 20 × 等级限制恢复目标。
     *
     * @return 不会获得领头僵尸加成的类型返回正无穷，表示不施加这项限制
     */
    public static double zombieRecoveryCapWithoutLeaderBonus(LivingEntity entity, MobState state) {
        if (entity == null || state == null) return Double.POSITIVE_INFINITY;
        int level = Math.max(1, state.getProfile().getLevel());
        return switch (entity.getType()) {
            case ZOMBIE, ZOMBIE_VILLAGER, HUSK, DROWNED -> 20.0 * level;
            default -> Double.POSITIVE_INFINITY;
        };
    }

    /** 获取治疗可达到的生命值上限，同时考虑 MAX_HEALTH 属性与领头僵尸加成限制。 */
    public static double healCeiling(LivingEntity entity, MobState state) {
        var attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        double zombieCap = zombieRecoveryCapWithoutLeaderBonus(entity, state);
        if (attribute == null) return zombieCap;
        return Math.min(attribute.getValue(), zombieCap);
    }

    /** 应用等级生命值、额外生命值和移动速度倍率，保留当前生命值比例。 */
    public void applyStats(LivingEntity entity, MobState mobState) {
        var attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        if (attribute != null) {
            int level = Math.max(1, mobState.getProfile().getLevel());
            double baseMax = attribute.getBaseValue();
            double baseCurrent = entity.getHealth();
            double newMax = baseMax * level + mobState.getStatMap().get(StatMap.HP_BONUS);
            attribute.setBaseValue(newMax);

            // Paper 的 setHealth 上限可能受原生 1024 上限限制，先按属性实际最大生命值截断。
            double effectiveCap = attribute.getValue();
            double newCurrent = baseCurrent * level;
            entity.setHealth(Math.max(0.1, Math.min(effectiveCap, newCurrent)));
        }

        double speedBonus = mobState.getStatMap().get(StatMap.SPEED_MULTIPLIER);
        var movement = entity.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedBonus != 0 && movement != null) {
            movement.setBaseValue(movement.getBaseValue() * (1 + speedBonus));
        }
    }
}
