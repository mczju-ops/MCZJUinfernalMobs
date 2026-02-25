# MCZJUItemCreator 与 InfernalMobs 对接说明（loot 特殊掉落）

## 提示语含义

- **「未找到插件 MCZJUItemCreator（或未启用）」**  
  服务器里没有加载名为 `MCZJUItemCreator` 的插件，或该插件被禁用。  
  - 和 jar 文件名无关，例如 `MCZJUItemCreator-1.0.jar` 可以不变。  
  - 关键是在 **MCZJUItemCreator 的 plugin.yml** 里要有：`name: MCZJUItemCreator`（必须一致）。

- **「MCZJUItemCreator 已加载但未注册 ItemCreatorApi」**  
  插件已加载，但没有向 Bukkit 的 ServicesManager 注册 `ItemCreatorApi`，InfernalMobs 拿不到接口，loot 特殊掉落不会生效。

---

## 你需要做的

### 1. 确认 plugin.yml 中的名字

在 **MCZJUItemCreator** 的 `plugin.yml` 里，插件名必须是：

```yaml
name: MCZJUItemCreator
```

jar 文件名可以随意（如 `MCZJUItemCreator-1.0.jar`），不需要改名。

### 2. 在 MCZJUItemCreator 里注册 ItemCreatorApi（必须）

InfernalMobs 通过 **Bukkit ServicesManager** 获取 `io.mczju.mczjuitemcreator.api.ItemCreatorApi`，所以 MCZJUItemCreator 必须在自己的 `onEnable` 里向 ServicesManager 注册该接口的实现。

- InfernalMobs 依赖 MCZJUItemCreator 提供的 `ItemCreatorApi`，调用 `createItem(String id, int amount)` 发放 loot 掉落。

### 3. 不打算改 MCZJUItemCreator 时

若无法修改 MCZJUItemCreator 源码，就无法注册 `ItemCreatorApi`，InfernalMobs 的 **loot 特殊掉落（按 id 发物品）** 不会生效；其他炒鸡怪功能不受影响。可关闭 loot：在 `loot.yml` 里设置 `enable: false`，就不会再出现该警告。

---

## 小结

| 项目           | 说明 |
|----------------|------|
| jar 文件名     | 任意，例如 `MCZJUItemCreator-1.0.jar` 即可。 |
| plugin.yml 名  | 必须是 `name: MCZJUItemCreator`。 |
| 让 loot 生效   | 在 MCZJUItemCreator 的 onEnable 里用 `ServicesManager.register(ItemCreatorApi.class, 实现, plugin, ServicePriority.Normal)`。 |
