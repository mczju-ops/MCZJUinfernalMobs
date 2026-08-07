package com.infernalmobs.api;

import com.infernalmobs.affix.Affix;
import com.infernalmobs.model.MobState;
import org.bukkit.entity.LivingEntity;

import java.util.List;

/**
 * 炒鸡怪 API 门面：包装内部 {@link MobState}，对外提供稳定视图，避免依赖方直接绑定内部模型。
 *
 * <p>本提交提供只读访问；编辑方法（{@code setDisplayName} / {@code setAffixes} / {@code setLevel}）
 * 在 beta 后续提交（生成事件里程碑）中补充。
 */
public final class InfernalMobHandle {

    private final LivingEntity entity;
    private final MobState mobState;

    public InfernalMobHandle(LivingEntity entity, MobState mobState) {
        this.entity = entity;
        this.mobState = mobState;
    }

    /** 炒鸡怪实体。 */
    public LivingEntity getEntity() {
        return entity;
    }

    /** 炒鸡怪等级。 */
    public int getLevel() {
        return mobState.getProfile().getLevel();
    }

    /** 词条 skillId 列表（只读，顺序与生成时一致）。 */
    public List<String> getAffixIds() {
        return mobState.getProfile().getAffixes().stream()
                .map(Affix::getSkillId)
                .toList();
    }

    /** 是否包含指定词条（大小写不敏感）。 */
    public boolean hasAffix(String skillId) {
        if (skillId == null) return false;
        return mobState.getProfile().getAffixes().stream()
                .anyMatch(a -> a.getSkillId().equalsIgnoreCase(skillId));
    }
}
