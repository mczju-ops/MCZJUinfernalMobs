# MCZJUinfernalMobs

> 丰富原版 Minecraft 怪物战斗体验的 **Infernal 风格**插件，为自然生成的生物附加等级、词条（技能）、掉落奖励与击杀统计系统。

---

## 目录

- [环境要求](#环境要求)
- [快速开始](#快速开始)
- [功能概览](#功能概览)
- [词条（技能）一览](#词条技能一览)
- [特殊道具](#特殊道具)
- [命令列表](#命令列表)
- [配置文件说明](#配置文件说明)
  - [config.yml](#configyml)
  - [loot.yml](#lootyml)
  - [loot/&lt;N&gt;.yml](#lootnyml)
  - [guaranteed_loot.yml](#guaranteed_lootyml)
  - [loot_name.yml](#loot_nameyml)
- [区域系统](#区域系统)
- [保底掉落](#保底掉落)
- [炒鸡小动物保护](#炒鸡小动物保护)
- [击杀统计](#击杀统计)

---

## 环境要求

| 项目 | 要求 |
|------|------|
| 服务端 | Paper 1.21.4+（API `1.21.4-R0.1-SNAPSHOT`） |
| Java | 17+ |
| 软依赖 | [MCZJUItemCreator](https://github.com/mczju-ops/MCZJUItemCreator)（掉落与保底功能需要） |

---

## 快速开始

1. 将编译好的 `MCZJUInfernalMob-*.jar` 放入 `plugins/` 目录。
2. 启动服务器，插件会自动在 `plugins/MCZJUinfernalMobs/` 生成所有配置文件。
3. 编辑 `config.yml`，至少确认 `enabled-worlds` 中包含你的目标世界。
4. 执行 `/im reload` 使配置生效。

---

## 功能概览

- **炒鸡怪生成**：自然/刷怪笼等触发的生物，以可配置概率被「炒鸡化」——分配 **等级**、**词条** 及 **头顶显示名**。
- **词条系统**：每只炒鸡怪携带若干技能词条，影响其战斗行为（毒、盲目、变形、窃取武器等）。等级越高词条越多。
- **区域配置**：按世界坐标范围划分独立区域，支持自定义等级范围、技能池、变形白名单。
- **掉落奖励**：与 MCZJUItemCreator 联动，按等级池抽取道具；支持月份轮换套与额外广播。
- **保底掉落**：累计击杀到阈值后必定掉落指定物品，进度持久化。
- **击杀统计**：记录每位玩家对各等级炒鸡怪的击杀数，可指令查询。
- **小动物保护**：可配置的生物类型列表，炒鸡版本死亡时不产生奖励，并在全服广播警告。
- **特殊道具**：全知之眼（查看词条）、幻形之锁（封印变形）、缴械反制器（抵御窃取）。

---

## 词条（技能）一览

### PASSIVE — 受击反击类（玩家攻击怪物时触发）

| 词条 ID | 效果 |
|---------|------|
| `poisonous` | 对攻击者附加中毒效果 |
| `blinding` | 对攻击者附加失明效果 |
| `withering` | 对攻击者附加凋零效果 |
| `quicksand` | 对攻击者附加缓慢效果 |
| `molten` | 使攻击者着火 |
| `sapper` | 对攻击者附加饥饿效果 |
| `confusing` | 对攻击者附加反胃效果 |
| `lifesteal` | 受击后一段时间内持续回血 |
| `rust` | 概率扣除玩家主手物品耐久 |
| `vengeance` | 概率将部分伤害反弹给攻击者 |
| `vexsummoner` | 概率召唤恼鬼（有数量上限） |
| `wardenwrath` | 概率发出声波伤害并击退玩家 |
| `swap` | 概率与玩家瞬间换位 |
| `mama` | 概率召唤子怪，高等级时体型变小 |

### ACTIVE — 主动攻击类（怪物攻击玩家时触发）

| 词条 ID | 效果 |
|---------|------|
| `firework` | 在玩家位置生成爆炸烟花造成伤害 |
| `berserk` | 自伤并对玩家造成额外伤害 |

### DUAL — 双向触发类（攻守皆可触发）

| 词条 ID | 效果 |
|---------|------|
| `ender` | 瞬移到玩家身后 |
| `webber` | 在玩家位置放置蛛网（概率大范围） |
| `archer` | 向玩家齐射多支箭矢 |
| `thief` | 延迟缴械，将玩家主手物品扔至怪物脚下 |
| `morph` | 概率变形为池内另一种生物 |
| `storm` | 概率在玩家处落雷 |
| `weakness` | 对玩家附加虚弱效果 |
| `refrigerate` | 冰冻玩家（减速 + 粒子） |

### RANGE — 范围感知类（玩家进入范围时持续触发）

| 词条 ID | 效果 |
|---------|------|
| `ghastly` | 向玩家发射火球类弹射物 |
| `necromancer` | 向玩家发射凋灵之首 |
| `tosser` | 将玩家拉向怪物 |
| `gravity` | 对玩家施加漂浮效果 |

### STAT — 生成时属性类（生成时一次性生效）

| 词条 ID | 效果 |
|---------|------|
| `armoured` | 穿戴盔甲；高等级可升级为钻石/合金套 |
| `bullwark` | 抗性 III |
| `1up` | 血量首次降至阈值时一次性回满（变身后不重置） |
| `cloaked` | 隐身（可带头盔遮蔽皮肤） |
| `sprint` | 附近有玩家时持续加速 |
| `mounted` | 骑乘白名单内的坐骑，坐骑同步被炒鸡化 |

---

## 特殊道具

以下道具通过 **MCZJUItemCreator** 的 `magicitemid`（PDC 键 `mczju:mi_id`）识别。

### 全知之眼 `infernal_eye`

**用法**：主手持有，**右键空气或方块**。  
射线追踪 20 格内的活体目标，若为已登记的炒鸡怪：

- 消耗 1 个全知之眼
- 播放粒子链特效（眼→目标）
- 在聊天栏显示该怪的等级颜色名与完整词条列表
- **已被幻形之锁封印的词条会显示删除线**

### 幻形之锁 `morph_controller`

**用法**：主手持有，**右键实体**（类似拴绳，需在交互范围内）。  
若目标为炒鸡怪且携带 `morph` 词条且尚未被封印：

- 消耗 1 个幻形之锁
- 永久封印该怪的 `morph` 词条（即使变身后也不重置）
- 触发汇聚粒子特效与音效
- 封印状态在全知之眼中以删除线标注

> 若目标无 `morph` 词条或已被封印，则不消耗道具。

### 缴械反制器 `thief_counter`

**用法**：副手持有，`thief` 词条缴械成功时**自动触发**。  
效果：

- 消耗 1 个反制器
- 大幅延长触发怪的 `thief` 技能冷却
- 播放反制粒子与音效

> 主手物品若带有 `im_thief_resistance` PDC 标记，可完全免疫缴械。

---

## 命令列表

所有命令需要权限 `infernalmobs.admin`（默认 OP），`/im reload` 额外支持 `infernalmobs.reload`。

| 命令 | 说明 |
|------|------|
| `/im` | 显示帮助信息 |
| `/im spawn <实体类型> [等级] [词条1,词条2,...]` | 在准星位置生成炒鸡怪，等级默认 5，词条逗号分隔（**仅玩家**） |
| `/im spawnat <x> <y> <z> <世界> <实体类型> [等级] [词条...]` | 在指定坐标生成炒鸡怪，**支持命令方块与控制台**；例：`/im spawnat 100 64 -200 world zombie 8 morph,ender` |
| `/im stats` | 显示当前追踪的炒鸡怪数量 |
| `/im stats <玩家>` | 查看指定玩家的各等级击杀统计 |
| `/im debug [on\|off]` | 临时开关调试输出（不写回配置） |
| `/im reload` | 重载所有配置（含区域、loot、幻形白名单等） |
| `/im clear [半径]` | 清除周围炒鸡怪，半径默认 32（1–256） |
| `/im cleantags` | 清理残留标签但未被管理的孤立实体 |

---

## 配置文件说明

### config.yml

```yaml
debug: false

# 启用炒鸡系统的世界列表
enabled-worlds:
  - world

# 触发炒鸡化的生成原因（CreatureSpawnEvent.SpawnReason）
infernal-spawn-reasons:
  - NATURAL
  - SPAWNER

defaults:
  level:
    fallback-min: 1   # 无区域时等级下限
    fallback-max: 5   # 无区域时等级上限
  infernal:
    allow-types: []   # 全局白名单（空=全部允许）
    deny-types: []    # 全局黑名单
  affix:
    count-formula: level  # level = 等级几就几个词条
    min: 1
    max: 5

# 技能权重（全局池，区域可覆盖）
skill-weights:
  poisonous: 10
  morph: 5
  # ...

# 死亡播报配置
death-messages:
  enabled: true
  broadcast-level-threshold: 8  # 达到此等级才全服播报
  level-colors:
    1: "#aaaaaa"
    5: "#ffff55"
    10: "#ff5555"
    # ...

# 炒鸡小动物保护
protected-animals:
  enabled: true
  types:
    - CAT
    - RABBIT
    - CHICKEN
  kill-broadcast: "<red>{player} 欺负炒鸡小动物！"

# 区域配置（见下方区域系统章节）
regions:
  example_region:
    world: world_the_end
    min: [-500, 0, -500]
    max: [500, 256, 500]
    priority: 10
    level-min: 8
    level-max: 15
    skill-pool:
      morph: 20
      ender: 15
    morph-types:
      - ENDERMAN
      - SHULKER

# 各技能数值参数
skills:
  morph:
    type: DUAL
    display: "变身"
    chance: 0.15
    cooldown-ticks: 100
    suppress-particle: TRIAL_OMEN
    suppress-sound: BLOCK_VAULT_REJECT_REWARDED_PLAYER
  thief:
    type: DUAL
    display: "窃取"
    chance: 0.3
    delay-ticks: 40
    counter-duration-ticks: 300
  # ...
```

---

### loot.yml

```yaml
enable: true
replace-vanilla-drops: true   # 是否替换原版掉落

# 月份轮换套（set 1～N 按月交替）
rotation:
  enable: false
  sets: 3

# 每次击杀额外掉落次数（按等级映射）
drop-times:
  enable: true
  fallback: [1, 1]     # [最少, 最多]
  1:  [1, 1]
  5:  [1, 2]
  10: [2, 3]
  15: [3, 5]
```

---

### loot/&lt;N&gt;.yml

等级 `N`（1–15）对应的奖励池，文件名即等级编号。

```yaml
rewards:
  - id: infernal_exchange_token   # ItemCreator 物品 ID
    amount: 1
    weight: 70                    # 权重
  - id: minecraft:diamond
    amount: 1
    weight: 1
    rotation-set: 1               # 仅在轮换套 1 期间有效（可选）
    broadcast: true               # 掉落时全服广播（可选）
    broadcast-message: "{player} 从 {mob} 身上获得了钻石！"
    commands:                     # 掉落时执行命令（可选）
      - "say {player} 获得了稀有掉落！"
```

---

### guaranteed_loot.yml

```yaml
enable: true

rules:
  my_rule:
    level-min: 10      # 适用等级范围
    level-max: 99
    count: 500         # 每累计击杀 500 次必得
    item-id: nether_star
    item-amount: 1
    reset-on-drop: true          # 达标后重置进度
    progress-id: shared_prog     # 可选，多规则共用进度条
    rotation-set: 1              # 可选，仅限指定轮换套期间
```

进度数据存储在 `guaranteed_loot_progress.yml`（自动创建）。

---

### loot_name.yml

```yaml
# ItemCreator 物品 ID → 广播中显示的中文名
infernal_exchange_token: "炒鸡兑换券"
thief_counter: "缴械反制器"
```

---

## 区域系统

插件按世界 + 轴对齐包围盒（AABB）划分区域，每个区域可独立配置：

| 配置项 | 说明 |
|--------|------|
| `world` | 世界名 |
| `min` / `max` | 三维坐标 `[x, y, z]` |
| `priority` | 优先级，多区域重叠时取最高值 |
| `level-min` / `level-max` | 等级随机范围 |
| `infernal-allow-types` | 区域内允许的实体类型（覆盖全局） |
| `infernal-deny-types` | 区域内禁止的实体类型 |
| `skill-pool` | 区域技能权重（完全覆盖全局池） |
| `morph-types` | `morph` 词条的变形目标池（可选） |

生成时，服务端按 **priority 从高到低** 找第一个坐标命中的区域；若无匹配区域则使用 `defaults.level.fallback-min/max`。

---

## 保底掉落

`GuaranteedLootService` 追踪每位玩家的击杀进度：

1. 击杀一只非保护动物的炒鸡怪，且等级落在规则的 `level-min`～`level-max` 之间，则该规则计数 +1。
2. 计数达到 `count` 后，向玩家发放 `item-id` 指定的 ItemCreator 物品。
3. 若 `reset-on-drop: true`，发放后进度归零；否则保留累计值。
4. `progress-id` 相同的多条规则共用同一进度条，适合多奖励共享计数。

---

## 炒鸡小动物保护

`protected-animals.types` 中列出的实体类型，被炒鸡化后死亡时：

- **清空全部掉落物与经验**
- **不计入击杀统计与保底进度**
- 若击杀者为玩家，**在整个世界广播**配置的警告消息

这意味着玩家无法通过猎杀炒鸡版小动物刷资源，同时会被其他玩家"社死"。

---

## 击杀统计

- `/im stats <玩家>` 显示该玩家对各等级炒鸡怪的击杀次数与总计。
- 数据存储于 `kill_stats.yml`（自动创建目录与文件）。
- 定时自动落盘，服务器关闭时强制保存。
