# InfernalMobs × Dye 插件对接功能清单

本文档列出 **Dye 插件必须实现/建议实现/可选实现** 的完整功能，用于和 `InfernalMobs` 联动。

---

## 一、联动边界（先统一职责）

- `InfernalMobs` 负责：
  - 带 `dye` 词条怪物生成时请求方案
  - 在实体 PDC 记录方案信息
  - 怪物死亡时按方案解析 `itemId` 并调用 ICA 掉落
- `Dye` 插件负责：
  - 生成并分配染色方案（带 `schemeId`）
  - 保存方案与实体映射
  - 执行渲染（实体与玩家物品）
  - 根据 `schemeId` 提供掉落 `itemId`

---

## 二、必须实现（MVP 必做）

## 1) 注册服务接口

必须在 `onEnable` 向 Bukkit ServicesManager 注册：

- `com.infernalmobs.api.dye.InfernalDyeApi`

否则 InfernalMobs 无法调用 Dye 插件。

## 2) 实现 `requestScheme(DyeSchemeRequest)`

输入：实体上下文（entityId/type/location/level/affixIds）  
输出：`DyeSchemeResult`

必须具备：

- 按配置选出一个方案并返回唯一 `schemeId`
- 返回该方案对应掉落 `dropItemId`
- 可选返回 `hexColor`（方便 IM 侧记录）
- 失败时返回 `Optional.empty()`（让 IM 自动回退本地配置）

## 3) 实现 `resolveDropItemId(String schemeId, UUID entityId)`

必须具备：

- 能从 `schemeId` 解析出唯一 `itemId`
- 找不到时返回 `Optional.empty()`

## 4) 维护实体方案映射（内存或持久化）

至少记录：

- `entityUuid -> schemeId`
- `schemeId -> dropItemId`

建议同时记录：

- `hex/gradient/particle profile`
- `seed`
- `createdAt`
- `configVersion`

## 5) 实现实体解绑

实现 `unbindEntity(UUID entityId)`：

- 怪物死亡/清理后释放记录
- 防止内存泄漏和脏数据

---

## 三、渲染功能（业务核心，建议视为必做）

## 1) 实体渲染

对被绑定方案的实体执行：

- 皮革盔甲颜色渲染（静态色）
- 渐变渲染（按 tick 更新时间相位）
- 粒子渲染（按 profile）

## 2) 玩家物品渲染

通过 `mi_id=xxx_dye` 或你约定的 `magicItemId` 识别物品：

- 物品在手/身上时应用同款渲染效果
- 确保“怪物掉落 dye”和“玩家持有 dye”表现一致

## 3) 渲染调度与性能保护

至少做到：

- 低频更新（建议 10~20 tick）
- 仅对有效实体/在线玩家处理
- 实体失效立即清理

---

## 四、配置系统（必须可配置）

Dye 插件至少支持下列配置维度：

- 方案池（`schemeId`）
- 色值（hex）
- 权重（weight）
- 对应掉落 `itemId`
- 是否启用渐变
- 粒子配置（类型/数量/半径/速度）

建议补充：

- 世界/生物/等级条件过滤
- 黑白名单
- 活动期轮换池

---

## 五、一致性与幂等（必须）

## 1) 幂等要求

同一 `entityId` 在短时间内重复调用 `requestScheme` 时：

- 返回同一个 `schemeId`（除非你明确允许刷新）

## 2) 死亡一致性

死亡掉落必须基于已绑定的 `schemeId`，不要二次随机。

## 3) 回退兼容

当 Dye 插件不可用或返回 empty：

- InfernalMobs 会自动回退 `dye.yml` 本地池
- Dye 插件无需额外处理，但要保证恢复上线后不影响正常流程

---

## 六、错误处理与日志（建议）

建议提供 debug 开关并输出：

- `requestScheme` 输入参数
- 方案命中结果（schemeId/dropItemId）
- `resolveDropItemId` 命中/未命中
- 渲染任务异常与清理事件

不要在高频 tick 中刷日志。

---

## 七、协议兼容建议

- `apiVersion()` 建议返回 `1`
- 后续新增字段尽量向后兼容
- 不要修改已有字段语义（如 `schemeId` 必须稳定）

---

## 八、联调验收清单

- 服务是否注册成功（IM 能拿到 `InfernalDyeApi`）
- 带 `dye` 词条怪物生成时是否调用 `requestScheme`
- 实体是否写入 `im_dye_scheme_id`
- 死亡时是否按 `schemeId` 解析正确 `itemId`
- ICA 掉落物是否带 `mi_id=xxx_dye`
- 玩家拿到物品后是否触发同款渲染
- 实体死亡/卸载后是否解绑干净

---

## 九、可选增强功能（非必做）

- 方案持久化（重启后继续保留）
- 渐变曲线编辑器（线性/正弦/HSV 环）
- 粒子模板系统（profile 复用）
- 管理命令（查询实体方案、热重载、强制重算）
- 数据导出（命中率、掉落统计、热门方案）

