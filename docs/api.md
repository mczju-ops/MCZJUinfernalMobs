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
        <version>1.0.0</version>   <!-- 发布 tag；开发期可用 master-SNAPSHOT 或 commit hash -->
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

### 3.1 InfernalAffixTriggerEvent —— 词条触发（可取消）

**时机**：词条技能 chance/cooldown 判定通过后、效果真正生效前。用于免疫 / 削弱 / 改数值。

| 字段 | 说明 |
| --- | --- |
| `String getAffixId()` | 触发的词条 id（如 `"gravity"`） |
| `SkillType getSkillType()` | 技能类型（PASSIVE / DUAL / RANGE / ACTIVE / DEATH） |
| `LivingEntity getMob()` | 炒鸡怪 |
| `LivingEntity getTarget()` | 作用目标（死亡类技能为击杀者，可能 null） |
| `InfernalMobHandle getHandle()` | 门面（等级 / 词条只读） |
| `int getLevel()` | 等级 |
| `Object getParam(String key)` | 读取参数袋 |
| `void setParam(String key, Object value)` | 修改参数（改数值） |
| `setCancelled(true)` | 取消本次触发（免疫） |

**常用参数 key 常量**：`PARAM_DURATION_TICKS` / `PARAM_AMPLIFIER` / `PARAM_CHANCE` / `PARAM_COOLDOWN_TICKS` / `PARAM_RANGE` / `PARAM_DAMAGE` / `PARAM_FORCE` / `PARAM_UPWARD` / `PARAM_VELOCITY` / `PARAM_FIRE_TICKS`。参数袋初始值为该词条在 config.yml 中的配置。

**示例：重力护符（MagicItems）——玩家快捷栏有护符时取消 gravity、否则把失重时长减半**
```java
@EventHandler
public void onAffix(InfernalAffixTriggerEvent e) {
    if (!"gravity".equals(e.getAffixId())) return;
    Player p = (Player) e.getTarget();
    if (p == null) return;

    if (countGravityCharm(p) > 0) {
        e.setCancelled(true);                       // 免疫
    } else {
        int t = e.getParam(InfernalAffixTriggerEvent.PARAM_DURATION_TICKS, 100);
        e.setParam(InfernalAffixTriggerEvent.PARAM_DURATION_TICKS, t / 2);  // 减半
    }
}
```

**示例：法王套免疫指定词条**
```java
@EventHandler
public void onAffix(InfernalAffixTriggerEvent e) {
    if (e.getTarget() instanceof Player p && wearingMagicKingChest(p)) {
        if (Set.of("poisonous", "withering", "lifesteal", "molten", "weakness", "rust").contains(e.getAffixId())) {
            e.setCancelled(true);
        }
    }
}
```

### 3.2 InfernalMobSpawnEvent —— 炒鸡怪生成（可取消）

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

### 3.3 InfernalMobDropEvent —— 掉落（可取消）

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

### 3.4 InfernalMobKillEvent —— 玩家击杀（不可取消）

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
- **1up**（STAT 型）不在词条触发事件线路上（STAT 词条是装配时生效，无 onTrigger）。
- **不可在异步线程**调用 `spawnInfernalMob` 或直接操作实体，请切主线程（`runTask`）。
- 事件类均在 `com.infernalmobs.api.event`，包结构即对外契约；调用逻辑在插件本体（对使用方不可见）。
