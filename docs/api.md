# InfernalMobs 对外 API 对接说明

本文档介绍如何让其他插件（如 MagicItems、异色炒鸡、炒鸡渔夫等）对接炒鸡怪插件的公开 API。

- 版本：`beta/event-api-rework` 分支（API `apiVersion() = 1`）
- 依赖方式：软依赖 + `ServicesManager`（无需硬依赖，炒鸡缺失时正常降级）
- 环境：Paper `api-version: '1.21.4'`、JDK 21+

---

## 1. 依赖与获取 API

### 1.1 编译期依赖（JitPack 发布）

对外 API 独立发布在 **MCZJUInfernalMobs-API** 项目（仅接口 / 事件 / 枚举），其他插件只需在 `pom.xml` 声明依赖（无需本地部署 jar）。插件本体在打包时已将 API shade 进自身 jar，因此运行时由插件本体提供实现、对接插件无需打包 API：

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.mczju-ops</groupId>
        <artifactId>MCZJUInfernalMobs-API</artifactId>
        <version>1.4.0</version>   <!-- 发布 tag；开发期可用分支名或 commit hash -->
        <scope>provided</scope>
    </dependency>
</dependencies>
```

### 1.2 运行时 plugin.yml（软依赖）

```yaml
name: MyPlugin
main: com.example.MyPlugin
api-version: '1.21'
softdepend:
  - InfernalMobs        # 炒鸡插件 name
```

### 1.3 获取 InfernalMobsApi

炒鸡插件在 `onEnable` 通过 `ServicesManager` 注册了 `InfernalMobsApi`：

```java
import com.infernalmobs.api.InfernalMobsApi;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class MyPlugin extends JavaPlugin {

    private InfernalMobsApi infernalApi;

    @Override
    public void onEnable() {
        RegisteredServiceProvider<InfernalMobsApi> rsp =
                Bukkit.getServicesManager().getRegistration(InfernalMobsApi.class);
        if (rsp != null) {
            infernalApi = rsp.getProvider();
        }
        if (infernalApi == null) {
            getLogger().warning("未找到 InfernalMobsApi（炒鸡插件未加载），相关功能禁用");
        }
    }
}
```

> 编译期依赖炒鸡插件 jar 即可（API 类在 `com.infernalmobs.api` 下）。

---

## 2. API 方法

接口：`com.infernalmobs.api.InfernalMobsApi`

| 方法 | 说明 |
| --- | --- |
| `boolean isInfernal(LivingEntity entity)` | 实体是否已被炒鸡化 |
| `Optional<InfernalMobHandle> getHandle(LivingEntity entity)` | 获取炒鸡怪门面句柄（未炒鸡化为空） |
| `List<String> getAffixIds(LivingEntity entity)` | 直接查询炒鸡怪词条 skillId 列表（未炒鸡化为空列表） |
| `boolean isAffixSuppressed(LivingEntity entity, String skillId)` | 查询某个词条是否被禁用（未炒鸡化返回 `false`） |
| `void setAffixSuppressed(LivingEntity entity, String skillId, boolean suppressed)` | 设定词条禁用状态（未炒鸡化无效） |
| `void setAffixSuppressed(LivingEntity entity, String skillId)` | 便捷重载：直接禁用指定词条 |
| `String getAffixDisplayName(String affixId)` | 查询词条显示名（优先 `skill_name.yml`，否则 `config.yml` 的 `display`，再退回英文 `id`） |
| `String getSkillDisplayName(String skillId)` | `getAffixDisplayName` 的兼容别名 |
| `LivingEntity spawnInfernalMob(EntityType type, Location loc, int level, List<String> affixSkillIds)` | 主动生成炒鸡怪（触发 `InfernalMobSpawnEvent`） |
| `LivingEntity spawnInfernalMob(EntityType type, Location loc, int level, List<String> affixSkillIds, Vector velocity)` | 同上，并施加初始速度（如钓海怪弹射） |
| `int apiVersion()` | API 版本（当前 1） |

`spawnInfernalMob` 返回 `null` 表示生成失败（类型/位置无效、词条全无效、或生成事件被取消）。

**示例：查询生物是否为炒鸡、带哪些词条，并判断是否被禁用（异色炒鸡 / MagicItems 可用）**
```java
// 是不是炒鸡
if (!api.isInfernal(mob)) return;

// 直接拿词条 skillId 列表（未炒鸡化为空列表）
List<String> affixIds = api.getAffixIds(mob);
if (affixIds.contains(InfernalAffix.WITHERING.id())) {
    // 带 withering 词条
}

boolean suppressed = api.isAffixSuppressed(mob, InfernalAffix.WITHERING.id());
if (suppressed) {
    // 该词条当前被插件禁用
}

api.setAffixSuppressed(mob, InfernalAffix.WITHERING.id(), true);

String witheringName = api.getAffixDisplayName(InfernalAffix.WITHERING.id());
// 例如："<dark_purple>凋零</dark_purple>" 或 "withering"

// 或走门面句柄，顺带拿等级
api.getHandle(mob).ifPresent(handle -> {
    int level = handle.getLevel();
    List<String> ids = handle.getAffixIds();
    boolean isSuppressed = handle.isAffixSuppressed(InfernalAffix.WITHERING.id());
    handle.setAffixSuppressed(InfernalAffix.WITHERING.id(), false);
});
```

### 2.1 InfernalMobHandle（门面句柄）

```java
public final class InfernalMobHandle {
    LivingEntity getEntity();                  // 炒鸡怪实体
    int getLevel();                            // 等级
    void setLevel(int level);                  // 设置等级（>=1）
    List<String> getAffixIds();                // 词条 skillId 列表（只读）
    void setAffixes(List<String> skillIds);    // 覆盖词条
    boolean hasAffix(String skillId);          // 是否含某词条（忽略大小写）
    boolean isAffixSuppressed(String skillId); // 是否被禁用（忽略大小写）
    void setAffixSuppressed(String skillId, boolean suppressed); // 更新禁用状态
    void setAffixSuppressed(String skillId);   // 便捷：直接禁用
    String getDisplayName();                   // 自定义显示名（MiniMessage），null=默认
    void setDisplayName(String miniMessage);   // 设置自定义显示名
}
```

> `setLevel` / `setAffixes` / `setDisplayName` 在 `InfernalMobSpawnEvent` 中使用时会对装配生效（事件在装配之前触发）。

### 2.2 InfernalAffix 词条枚举

```java
public enum InfernalAffix {
    ONE_UP("1up"), ARMOURED("armoured"), BLINDING("blinding"), WITHERING("withering"),
    QUICKSAND("quicksand"), BULLWARK("bullwark"), CLOAKED("cloaked"), ENDER("ender"),
    GHASTLY("ghastly"), LIFESTEAL("lifesteal"), SPRINT("sprint"), SAPPER("sapper"),
    WEBBER("webber"), MOLTEN("molten"), ARCHER("archer"), NECROMANCER("necromancer"),
    FIREWORK("firework"), GHOST("ghost"), DYE("dye"), CONFUSING("confusing"),
    THIEF("thief"), TOSSER("tosser"), STORM("storm"), VENGEANCE("vengeance"),
    WEAKNESS("weakness"), BERSERK("berserk"), MAMA("mama"), GRAVITY("gravity"),
    MOUNTED("mounted"), SPEAR("spear"), SULFUR("sulfur"), MORPH("morph"),
    REFRIGERATE("refrigerate"), RUST("rust"), VEXSUMMONER("vexsummoner"),
    WARDENWRATH("wardenwrath"), SWAP("swap");

    String id();                                     // 词条 skillId
    static Optional<InfernalAffix> fromId(String id); // 按 id 反向查找
}
```

---

## 3. 事件（`com.infernalmobs.api.event`）

注册方式：实现 `Listener`，`@EventHandler` 监听对应事件类即可（与监听任何 Bukkit 事件相同）。

### 3.1 事件体系概览

非 `STAT` 词条遵循以下触发链路：

```text
内部资格检查
→ InfernalAffixAttemptEvent
→ 技能条件与概率判定
→ 计算最终效果参数
→ 专用 InfernalAffixTriggeredEvent
→ 提交冷却
→ 应用效果
```

| 链路 | 事件 | 时机 | 用途 |
| --- | --- | --- | --- |
| **尝试触发** | `InfernalAffixAttemptEvent` | 内部资格检查通过、技能条件与概率判定之前 | 阻止本次尝试 |
| **真正触发** | `InfernalAffixTriggeredEvent` 各技能子类 | 条件与概率判定通过、效果即将生效 | 精确监听某技能、修改类型化效果参数 |

- `STAT` 词条是怪物装配时形成的特质，不经过 Attempt 链路。
- 每个词条都有一个专属 Triggered 事件（如 `InfernalMobThiefEvent`、`InfernalMobArmouredEvent`），全部继承抽象基类 `InfernalAffixTriggeredEvent`。
- 另有 3 个与词条触发无关的生命周期事件：`InfernalMobSpawnEvent`（生成）、`InfernalMobDropEvent`（掉落）、`InfernalMobKillEvent`（击杀）。

### 3.2 InfernalAffixAttemptEvent —— 尝试触发（可取消）

**时机**：非 `STAT` 词条通过冷却、目标等内部资格检查后，进入技能自身条件与概率判定之前。

| 字段 | 说明 |
| --- | --- |
| `String getAffixId()` | 尝试触发的词条 id（如 `"gravity"`） |
| `SkillType getSkillType()` | 技能类型（ACTIVE / PASSIVE / DEATH / RANGE / DUAL） |
| `LivingEntity getMob()` | 炒鸡怪 |
| `LivingEntity getTarget()` | 本次交互目标（可能 null） |
| `InfernalMobHandle getHandle()` | 门面（等级 / 词条只读） |
| `int getLevel()` | 等级 |
| `setCancelled(true)` | 结束本次尝试：不再判定、不进入新冷却 |

**示例：阻止玩家受到 gravity 的本次触发尝试**
```java
@EventHandler
public void onAffixAttempt(InfernalAffixAttemptEvent e) {
    if (!"gravity".equals(e.getAffixId())) return;
    if (e.getTarget() instanceof Player player && isImmune(player)) e.setCancelled(true);
}
```

> Attempt 不提供通用参数袋。需要修改效果数值时，请监听 §3.3 中对应技能的类型化 Triggered 事件。

### 3.3 InfernalAffixTriggeredEvent —— 词条真正触发

**时机**：技能条件与概率判定通过、最终效果参数已经计算、效果即将生效时。每个技能一个专属子类。

到达 Triggered 事件即表示本次词条已经成功触发。`setCancelled(true)` 会阻止效果生效，但不会改回“未触发”状态，本体仍会提交或保留本次冷却。条件失败或概率未通过时，不广播 Triggered 事件，也不产生新冷却。

**基类公共字段**：

| 字段 | 说明 |
| --- | --- |
| `String getAffixId()` | 触发的词条 id |
| `SkillType getSkillType()` | 技能类型（ACTIVE / PASSIVE / STAT / DEATH / RANGE / DUAL） |
| `LivingEntity getMob()` | 炒鸡怪 |
| `LivingEntity getTarget()` | 效果作用目标；STAT 装配类与部分场景为 null |
| `InfernalMobHandle getHandle()` | 门面（只读） |
| `int getLevel()` | 等级 |
| `setCancelled(true)` | 阻止本次效果生效，但仍视为成功触发并进入冷却 |

**Post 事件全表**（共 38 个）：

| 词条 | 事件类 | 类型 | 额外字段 |
| --- | --- | --- | --- |
| 1up | `InfernalMob1upEvent` | STAT | `getRecoveryAmount/setRecoveryAmount`（免除伤害后额外回复的生命值；target=攻击者，可能 null） |
| archer | `InfernalMobArcherEvent` | DUAL | `getArrowCount/setArrowCount`、`getSpeed/setSpeed`、`getDirectionSpread/setDirectionSpread`、`getProjectileSpread/setProjectileSpread` |
| armoured | `InfernalMobArmouredEvent` | STAT | `getHelmet/setHelmet`、`getChestplate/setChestplate`、`getLeggings/setLeggings`、`getBoots/setBoots`（target=null） |
| berserk | `InfernalMobBerserkEvent` | ACTIVE | `getSelfDamage/setSelfDamage`、`getBonusDamage/setBonusDamage` |
| blinding | `InfernalMobBlindingEvent` | PASSIVE | `getDurationTicks/setDurationTicks`、`getAmplifier/setAmplifier` |
| bullwark | `InfernalMobBullwarkEvent` | STAT | `getDurationTicks/setDurationTicks`、`getAmplifier/setAmplifier`（target=null） |
| cloaked | `InfernalMobCloakedEvent` | STAT | `getDurationTicks/setDurationTicks`、`getHelmet/setHelmet`（target=null） |
| confusing | `InfernalMobConfusingEvent` | PASSIVE | `getDurationTicks/setDurationTicks`、`getAmplifier/setAmplifier` |
| dye | `InfernalMobDyeEvent` | DEATH | —（target=击杀者，可能 null） |
| ender | `InfernalMobEnderEvent` | DUAL | `getDestination/setDestination` |
| firework | `InfernalMobFireworkEvent` | DUAL | `getSpawnLocation/setSpawnLocation`、`getFireworkEffect/setFireworkEffect` |
| ghastly | `InfernalMobGhastlyEvent` | RANGE | `getSpawnLocation/setSpawnLocation`、`getVelocity/setVelocity`、`getDirectDamage/setDirectDamage`、`getFireTicks/setFireTicks`、`getExplosionPower/setExplosionPower`、`getLifetimeTicks/setLifetimeTicks` |
| ghost | `InfernalMobGhostEvent` | DEATH | —（target=击杀者，可能 null） |
| gravity | `InfernalMobGravityEvent` | RANGE | `get/setDurationTicks`、`get/setAmplifier` |
| lifesteal | `InfernalMobLifestealEvent` | PASSIVE | `getDurationTicks/setDurationTicks`、`getHealPerSecond/setHealPerSecond`（每 20 tick 治疗量） |
| mama | `InfernalMobMamaEvent` | PASSIVE | `getCount/setCount`、`getChildType/setChildType`、`getSpawnLocation/setSpawnLocation`、`getChildLevelMin/setChildLevelMin`、`getChildLevelMax/setChildLevelMax`、`setChildLevelRange`、`isBaby/setBaby`、`getNoBabyScale/setNoBabyScale` |
| molten | `InfernalMobMoltenEvent` | PASSIVE | `getFireTicks/setFireTicks`（攻击者的最低剩余燃烧时间） |
| morph | `InfernalMobMorphEvent` | DUAL | `getTargetType/setTargetType` |
| mounted | `InfernalMobMountedEvent` | STAT | —（target=null） |
| necromancer | `InfernalMobNecromancerEvent` | RANGE | `getSpawnLocation/setSpawnLocation`、`getVelocity/setVelocity`、`getExplosionPower/setExplosionPower`、`isCharged/setCharged`、`getLifetimeTicks/setLifetimeTicks` |
| poisonous | `InfernalMobPoisonousEvent` | PASSIVE | `getDurationTicks/setDurationTicks`、`getAmplifier/setAmplifier` |
| quicksand | `InfernalMobQuicksandEvent` | PASSIVE | `getDurationTicks/setDurationTicks`、`getAmplifier/setAmplifier` |
| refrigerate | `InfernalMobRefrigerateEvent` | DUAL | `getFreezeTicks/setFreezeTicks`（目标冻结计数器的最低值，实际应用不超过目标上限） |
| rust | `InfernalMobRustEvent` | PASSIVE | `getItemStack`（只读快照）、`getDamageAmount/setDamageAmount`（标准耐久损耗量） |
| sapper | `InfernalMobSapperEvent` | PASSIVE | `getDurationTicks/setDurationTicks`、`getAmplifier/setAmplifier` |
| spear | `InfernalMobSpearEvent` | RANGE | — |
| sprint | `InfernalMobSprintEvent` | STAT | `getAmplifier/setAmplifier`（无限时长速度效果；target=null） |
| storm | `InfernalMobStormEvent` | DUAL | `getStrikeLocation/setStrikeLocation`、`getDamage/setDamage`、`isEffectOnly/setEffectOnly` |
| sulfur | `InfernalMobSulfurEvent` | PASSIVE | — |
| swap | `InfernalMobSwapEvent` | PASSIVE | — |
| thief | `InfernalMobThiefEvent` | DUAL | `getPlayer`、`getItemStack`、`get/setDropLocation`、`get/setCooldownTicks` |
| tosser | `InfernalMobTosserEvent` | RANGE | `get/setForce`、`get/setUpward` |
| vengeance | `InfernalMobVengeanceEvent` | PASSIVE | — |
| vexsummoner | `InfernalMobVexSummonerEvent` | PASSIVE | — |
| wardenwrath | `InfernalMobWardenWrathEvent` | PASSIVE | — |
| weakness | `InfernalMobWeaknessEvent` | DUAL | — |
| webber | `InfernalMobWebberEvent` | DUAL | — |
| withering | `InfernalMobWitheringEvent` | PASSIVE | `getDurationTicks/setDurationTicks`、`getAmplifier/setAmplifier` |

**sulfur 的逐玩家喷发事件**：`InfernalMobSulfurLaunchEvent` 不继承 `InfernalAffixTriggeredEvent`。
它在硫泉完成预警、准备顶起范围内某一名玩家时单独广播，可通过
`getPlayer()` 获取该玩家、通过 `getUpward/setUpward` 修改本次竖直速度，或取消该玩家本次被顶起。
取消 Launch 事件不会取消已经发生的 sulfur 触发，也不会回滚或重复提交其冷却。

**firework 的逐受害者伤害事件**：`InfernalMobFireworkDamageEvent` 不继承
`InfernalAffixTriggeredEvent`。原版烟花爆炸为每个受影响实体计算出基础伤害后分别广播，
可通过 `getVictim()` 获取当前受害者、通过 `getDamage/setDamage` 修改重新结算的基础伤害，
或取消当前受害者的本次伤害。取消 Damage 事件不会影响同次爆炸中的其他实体，
也不会回滚已经发生的 firework 触发或冷却。

**ghastly 的逐受害者伤害事件**：`InfernalMobGhastlyDamageEvent` 不继承
`InfernalAffixTriggeredEvent`。火球直接命中或爆炸产生范围伤害时，针对每个受害者分别广播，
可通过 `getDamageCause()` 区分伤害阶段、通过 `getDamage/setDamage` 修改基础伤害，
或取消当前受害者的本次伤害。取消 Damage 事件不会影响其他受害者，
也不会回滚已经发生的 ghastly 触发或冷却。

**necromancer 的逐受害者伤害事件**：`InfernalMobNecromancerDamageEvent` 不继承
`InfernalAffixTriggeredEvent`。凋灵之首直接命中或爆炸产生范围伤害时，针对每个受害者分别广播，
可通过 `getDamageCause()` 区分伤害阶段、通过 `getDamage/setDamage` 修改基础伤害，
或取消当前受害者的本次伤害。取消 Damage 事件不会影响其他受害者，
也不会回滚已经发生的 necromancer 触发或冷却。

**storm 的逐受害者伤害事件**：`InfernalMobStormDamageEvent` 不继承
`InfernalAffixTriggeredEvent`。真实闪电对范围内实体产生伤害时，针对每个受害者分别广播，
可通过 `getLightningStrike()` 获取闪电实体、通过 `getDamage/setDamage` 修改基础伤害，
或取消当前受害者的本次伤害。取消 Damage 事件不会撤销点火、生物转化或其他受害者的伤害，
也不会回滚已经发生的 storm 触发或冷却。视觉闪电不会广播该事件。

**示例：thief 缴械——把掉落位置改到玩家脚下、并缩短冷却**
```java
@EventHandler
public void onThief(InfernalMobThiefEvent e) {
    e.setDropLocation(e.getPlayer().getLocation());   // 掉到玩家脚下
    e.setCooldownTicks(e.getCooldownTicks() / 2);     // 冷却减半
}
```

**示例：armoured 装配——只监听高等级触发并记录**
```java
@EventHandler
public void onArmoured(InfernalMobArmouredEvent e) {
    if (e.getLevel() < 3) return;          // 只看 3 级+
    // STAT 装配类事件 target 恒为 null，作用对象即 e.getMob() 自己
    log("armoured 装配生效: Lv." + e.getLevel() + " @ " + e.getMob().getName());
}
```

**示例：mama 母体——翻倍产子**
```java
@EventHandler
public void onMama(InfernalMobMamaEvent e) {
    e.setCount(e.getCount() * 2);
}
```

### 3.4 InfernalMobSpawnEvent —— 炒鸡怪生成（可取消）

**时机**：等级/词条已计算之后、装配/数值/命名/注册之前。用于编辑生成内容或阻止炒鸡化。

| 字段 | 说明 |
| --- | --- |
| `LivingEntity getEntity()` | 被炒鸡化的实体 |
| `InfernalMobHandle getHandle()` | 可编辑：`setLevel` / `setAffixes` / `setDisplayName` |
| `Location getLocation()` | 生成位置 |
| `int getLevel()` | 计算出的初始等级 |
| `setCancelled(true)` | 阻止本次炒鸡化（实体保持普通怪） |

**示例：异色炒鸡——20% 概率改造成异色版（自定义名 + 固定词条）**
```java
@EventHandler
public void onSpawn(InfernalMobSpawnEvent e) {
    if (Math.random() < 0.2) {
        e.getHandle().setDisplayName("<gradient:#ff5555:#55ffff>异色炒鸡</gradient>");
        e.getHandle().setAffixes(List.of(InfernalAffix.MOLTEN.id(), InfernalAffix.RUST.id()));
    }
}
```

### 3.5 InfernalMobDropEvent —— 掉落（可取消）

**时机**：插件产出掉落（等级池加权 + special + 保底 + dye 特殊掉落）聚合后、落世界前。**不包含原版掉落。**

| 字段 | 说明 |
| --- | --- |
| `LivingEntity getEntity()` | 死亡的炒鸡怪 |
| `InfernalMobHandle getHandle()` | 等级 / 词条只读 |
| `int getLevel()` | 等级 |
| `Player getKiller()` | 击杀者（环境杀可能 null） |
| `List<ItemStack> getDrops()` | 掉落表（**可变**，可追加 / 删除 / 替换） |
| `setCancelled(true)` | 取消这批插件掉落（原版掉落不受影响） |

**示例：炒鸡渔夫——在原有战利品上追加「深海碎片」**
```java
@EventHandler
public void onDrop(InfernalMobDropEvent e) {
    ItemStack shard = makeDeepSeaShard();   // 你的 ItemStack
    e.getDrops().add(shard);
}
```

### 3.6 InfernalMobKillEvent —— 玩家击杀（不可取消）

**时机**：确认击杀者为玩家后同步触发。供进度 / 成就插件使用。

| 字段 | 说明 |
| --- | --- |
| `LivingEntity getEntity()` | 被击杀的炒鸡怪 |
| `InfernalMobHandle getHandle()` | 等级 / 词条只读 |
| `Player getKiller()` | 击杀玩家 |
| `int getLevel()` | 等级 |
| `Location getLocation()` | 死亡位置 |

**示例：成就「击杀一只同时带 withering 与 armoured 的炒鸡怪」**
```java
@EventHandler
public void onKill(InfernalMobKillEvent e) {
    var ids = e.getHandle().getAffixIds();
    if (ids.contains("withering") && ids.contains("armoured")) {
        progressManager.add(e.getKiller(), "infernal_wither_armoured");
    }
}
```

---

## 4. 完整对接示例（炒鸡渔夫：钓海怪）

```java
@EventHandler
public void onFish(ProjectileHitEvent e) {
    if (!(e.getEntity() instanceof FishHook hook)) return;
    if (hook.getHookedEntity() == null && hook.getShooter() instanceof Player) {
        // 上钩瞬间：把战利品置空并换成炒鸡怪（这里简化，实际按你的钓鱼流程）
        Location loc = hook.getLocation();
        InfernalMobsApi api = ...; // 见 §1.3
        if (api == null) return;
        api.spawnInfernalMob(
                EntityType.GUARDIAN, loc, 12,
                List.of(InfernalAffix.GRAVITY.id(), InfernalAffix.WARDENWRATH.id()),
                new Vector(0, 0.8, 0)   // 上钩弹跳初速度
        );
    }
}
```

---

## 5. 注意事项

- **thief 缴械物**不在掉落事件聚合内（它是战斗中从玩家手上掉的物品，时机在死亡之外）。
- **STAT / DEATH / 1up 词条现已各有专属 Post 事件**：STAT 在装配生效时触发（`target` 为 null）；DEATH 在死亡时触发（`target` 为击杀者，可能 null）；1up 在保命时触发（`target` 为攻击者，可能 null）。
- **不可在异步线程**调用 `spawnInfernalMob` 或直接操作实体，请切主线程（`runTask`）。
- 事件类均在 `com.infernalmobs.api.event`，包结构即对外契约；调用逻辑在插件本体（对使用方不可见）。
