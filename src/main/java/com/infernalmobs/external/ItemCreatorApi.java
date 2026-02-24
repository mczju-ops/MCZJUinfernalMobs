package com.infernalmobs.external;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * ItemCreator（MCZJUItemCreator）提供的 API。
 * 由 MCZJUItemCreator 在 ServicesManager 中注册实现。
 */
public interface ItemCreatorApi {

    /**
     * 按物品 ID 与数量构建物品（用于 loot 权重抽中后发放）。
     *
     * @param id     ItemCreator 中配置的物品 ID
     * @param amount 数量
     * @return 物品，若不存在或失败则 empty
     */
    Optional<ItemStack> createItem(String id, int amount);

    /**
     * 根据池子 ID 与等级抽取掉落物（可选实现；未实现时用 createItem + loot.yml 的 rewards 权重即可）。
     *
     * @param poolId ItemCreator 中配置的池子 ID
     * @param level  炒鸡怪等级
     * @return 本次抽取到的物品列表，可为空列表，不可为 null
     */
    default List<ItemStack> rollFromPool(String poolId, int level) {
        return List.of();
    }
}
