# Interaction Plus 对外插件 API 对接指南

面向**其他 Halo 插件开发者**。`interaction-plus` 通过 Halo 扩展点向其他插件开放两个进程内 API：

| API | 接口 | 方向 | 用途 |
|---|---|---|---|
| [发奖 API](#发奖-api) | `DecorationGrantApi` | 写 | 在用户达成你定义的条件时发放 / 撤销装饰（勋章、头像框、称号、名片背景、昵称样式） |
| [身份查询 API](#身份查询-api) | `PublicIdentityQueryApi` | 读 | 在你的插件后端查询用户公开身份与装扮，内嵌进你自己的接口响应 |

两个 API 的接入方式完全相同（见[快速开始](#快速开始)），可按需只用其一。

> **状态**：随 `0.1.x` 以**实验性**状态发布——契约已定型但尚未经大规模生产验证，可能随首批接入者的反馈微调；调整将保持前向兼容（只增不改）并同步到本文档。
>
> 以下能力仅在 `interaction-plus` **已安装并启用**时可用，请做好[降级](#降级与故障排查)。

## 快速开始

共 3 步。

### 第 1 步：添加 api 依赖

api 模块发布在 **JitPack**。先加仓库（推荐放在 `settings.gradle` 的 `dependencyResolutionManagement`，或根 `build.gradle`；JitPack 建议排在其他仓库之后）：

```gradle
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}
```

再声明依赖（`compileOnly`——运行时由 interaction-plus 提供）：

```gradle
dependencies {
    compileOnly 'com.github.Tim0x0.halo-plugin-interaction-plus:api:0.1.0'
}
```

> api 模块仅含接口 + DTO。坐标说明：`com.github.<GitHub 用户>.<仓库>:<模块>:<版本>` 是 JitPack 多模块约定；版本号对应 `interaction-plus` 的 git tag（`v0.1.0` → 版本 `0.1.0`），以实际发布为准。

### 第 2 步：声明可选依赖

在你的 `plugin.yaml` 中：

```yaml
spec:
  pluginDependencies:
    "interaction-plus?": ">=0.1.0"   # 末尾问号 = 可选依赖（Halo 2.20.11+）
```

可选依赖：`interaction-plus` 没装 / 没启用时，**你的插件照常启动**，只是对应能力不可用。

### 第 3 步：通过 ExtensionGetter 获取实例

各插件的 Spring 容器相互隔离，**不能直接 `@Autowired` 这些接口**。统一经 Halo 核心的 `ExtensionGetter`（自 Halo 2.18 起对插件开放）取用：

```java
import run.halo.app.plugin.extensionpoint.ExtensionGetter;
import com.timxs.interactionplus.api.DecorationGrantApi;

private final ExtensionGetter extensionGetter;

private Mono<DecorationGrantApi> grantApi() {
    return extensionGetter.getEnabledExtension(DecorationGrantApi.class);
}
```

身份查询 API 同款，把接口类换成 `PublicIdentityQueryApi` 即可。

> 用 `getEnabledExtension`（不是 `getExtensions`）。`interaction-plus` 缺席时它返回**空 `Mono`**，下游自然跳过。

## 发奖 API

> **分工**：你的插件**判断业务条件**（签到 7 天、积分达标、活动参与……），`interaction-plus` 只负责把装饰发下去 / 撤回来。条件判断完全在你这边，本插件不感知、不监听任何条件。

### 适用场景

- 你写了签到 / 积分 / 等级 / 活动等插件，想在用户达成条件时发一个装饰作为奖励。
- 你只管「**何时发、何时撤**」；装饰本身（图、名称、稀有度）由站长在 `interaction-plus` 后台管理，你不负责"造装饰"。

### 发放模型

两类发放场景：

| 场景 | 怎么调 | 例子 |
|---|---|---|
| **永久型**（达成即发、不收回） | 只调 `grant`，永不 `revoke` | 连续签到 7 天发个永久勋章 |
| **持续型**（满足才有、中断即撤） | 达成 `grant`、中断 `revoke`、再达成再 `grant` | 连续留言期间挂个标，断了就摘 |

- **多来源并存（谁发的谁管）**：授予按来源相互独立——你、其他插件、站长后台可以各自给同一用户发同一装饰，互不吞并、互不覆盖；用户**持有 = 任一来源存在有效授予**，展示有效期取各来源中最晚。你只需管好自己的发与撤，**无需关心别人是否发放过**。
- **幂等**：`grant` / `revoke` 均可安全重复调用——已持有你发的授予时 `grant` 不重复发放；你无可撤的授予时 `revoke` 无副作用（状态区分见 [DecorationGrantApi 参考](#decorationgrantapi-参考)）。
- **续期语义（只延长不缩短）**：已持有你发的有效授予，再次 `grant` 时本次 `expiresAt` **比现有更晚**（或留空升格永久）才刷新并返回 `RENEWED`，否则返回 `ALREADY_HELD`——取更晚值让重复 / 乱序重试都安全；要**缩短或收回**走 `revoke`。注意这是"刷新窗口"不是"叠加时长"：需要叠加的场景（如购买 / 兑换时长），请你自行记录当前到期日、算好累计后的**绝对时刻**传入。
- **持续型防"漏撤"**（你的插件崩了 / 被卸载没来得及 `revoke`）：`grant` 时给一个**短** `expiresAt`，满足期间周期性重发续期（返回 `RENEWED`），不续则自动过期失效。

### DecorationGrantApi 参考

```java
Flux<GrantableDecoration> listGrantable(String categoryName); // 可发清单，categoryName 传 null = 全部
Mono<GrantResult>         grant(GrantRequest request);
Mono<RevokeResult>        revoke(RevokeRequest request);
```

请求与响应：

```text
GrantRequest        { userName, decorationName, sourcePlugin, expiresAt?(留空=永久), reason? }
                    另有三参便捷构造 (userName, decorationName, sourcePlugin)，等价于永久发放
RevokeRequest       { userName, decorationName, sourcePlugin, reason? }
GrantableDecoration { name, displayName, type, rarityName, rarityDisplayName, categoryName }
GrantResult         { status, grantName }
RevokeResult        { status }
```

`sourcePlugin` 传**你自己的插件标识**（你 `plugin.yaml` 的 `metadata.name`）。用于来源记录与撤销隔离——`revoke` 只会撤**你自己**发放的那条授予，动不到别人（其他插件 / 站长后台）发的。`interaction-plus:` 开头的来源标识为**内部保留**（预留给本插件后续内置的等级 / 积分引擎），外部插件请一律传自己插件的 `metadata.name`。

发放结果 `GrantResult.status`：

| 状态 | 含义 |
|---|---|
| `GRANTED` | 已新发放（他源是否发过与此无关） |
| `RENEWED` | 已持有你发的授予，本次已延长有效期（仅新值更晚才刷新） |
| `ALREADY_HELD` | 已持有你发的授予且本次有效期不更晚，幂等跳过 |
| `DECORATION_NOT_FOUND` | `decorationName` 不存在 |
| `DECORATION_INACTIVE` | 装饰未启用 |
| `DECORATION_NOT_EXTERNALLY_GRANTABLE` | 该稀有度被站长禁止外发 |
| `USER_NOT_FOUND` | 用户不存在 |

便捷方法 `isHeld()`：`status ∈ {GRANTED, RENEWED, ALREADY_HELD}`，即「调用后用户持有该装饰」——只关心结果、不关心新发 / 续期 / 已持有区别时，用它做归一判断。

撤销结果 `RevokeResult.status`：

| 状态 | 含义 |
|---|---|
| `REVOKED` | 已撤销你发的那条（用户若还有他源有效授予则仍持有该装扮） |
| `NOT_HELD` | 用户无任何有效授予，幂等无操作 |
| `FORBIDDEN_NOT_OWNER` | 你无可撤的授予，但他源（其他插件 / 后台）仍有——不可越权撤 |

参数校验：参数为空（`userName` / `decorationName` / `sourcePlugin`），或 `expiresAt` 不晚于当前时间（已是过去的有效期不会发放），会以 `IllegalArgumentException` 抛出（走 `Mono` 的 error 通道），与上面的业务状态区分。

### 指定装饰标识

`grant` / `revoke` 的 `decorationName` 就是装饰的 **`metadata.name`**（Halo 唯一标识，形如 `asset-01hxyz…`）。两种拿法，对应两种集成风格：

1. **让站长复制填**（最简单）：站长在 `互动 → 装饰 → 资产 → 编辑` 弹窗顶部能看到「装饰标识」并一键复制，粘到你插件的配置项里。
2. **可视化选择**（体验更好）：调 `listGrantable(categoryName)` 拉清单，在你插件的设置页做下拉，让站长按**显示名**选，你存下选中项的 `name`。

> `listGrantable` 只返回**当前能发的**（已启用 + 所属稀有度允许外发），与 `grant` 的校验一致——清单里有的就一定发得出。

### 完整示例：签到发放

永久型场景。把所有引用 `interaction-plus` 类的逻辑**收拢到一个独立组件**（原因见[降级与故障排查](#降级与故障排查)）：

```java
@Component
@RequiredArgsConstructor
public class DecorationGrantClient {

    private final ExtensionGetter extensionGetter;

    /** 用户连续签到达标时调用；decorationName 来自站长配置。 */
    public Mono<Void> grantOnSigninStreak(String userName, String decorationName) {
        return extensionGetter.getEnabledExtension(DecorationGrantApi.class)
            .flatMap(api -> api.grant(
                new GrantRequest(userName, decorationName, "my-checkin-plugin")))
            .doOnNext(result -> log.info("发放结果 {}", result.status()))
            .then()
            // interaction-plus 未安装 / 调用异常时静默降级，不影响签到主流程
            .onErrorResume(e -> Mono.empty());
    }
}
```

持续型则在「中断」时再调 `api.revoke(new RevokeRequest(userName, decorationName, "my-checkin-plugin"))`。

### 发放最佳实践

- **你发的授予也可能被站长收回**：站长可在后台撤销任何来源的授予（含你发放的），不要假设"发过就永远存在"。持续型场景的周期性重发会自然重建；一次性场景可按 `grant` 返回状态感知。
- **稀有度准入**：站长可关闭某些稀有度（如传说 / 限定）的「允许外部发放」开关。被关掉的装饰不会出现在 `listGrantable`，`grant` 也会返回 `DECORATION_NOT_EXTERNALLY_GRANTABLE`——这是站长的有意控制，你的插件按结果状态处理即可。
- **不要硬编码 ulid**：装饰删了重建后 `metadata.name` 会变。建议让站长配置 / 选择，而不是把某个 `asset-xxx` 写死在代码里。
- **网络重试安全**：`grant` / `revoke` 幂等（见[发放模型](#发放模型)），可放心重试。

## 身份查询 API

> **与其他读出口同源**：本 API 与公开 HTTP API（`GET /identity/{userName}`）、主题 Finder（`${interactionPlus.getIdentity(...)}`）共用同一聚合服务与缓存，返回内容、判空行为、对用户禁用 / 删除的处理三个出口一致，仅出口形态不同。只读、无副作用，可任意重试。

### 适用场景：为什么在后端查

已有的两条读通道各有边界：**公开 HTTP API** 给浏览器前端 fetch 用，调用方必须持有 `userName`；**Finder** 注册进模板引擎，只有 Thymeleaf 主题模板取得到（插件容器隔离，其他插件的 Java 代码拿不到）。

**典型缺口**：你想在自己插件的公开 API 响应里内嵌用户装扮（如评论插件给评论作者附带装扮数据），但 Halo 对**评论者 / 回复者**这类互动用户的 `userName` 明确脱敏（公开评论查询只保留 displayName / avatar，`owner.name` 置空、email 转 hash）——前端拿不到用户名，无从调用 HTTP 批量接口，查询必须发生在你的**后端**，`userName` 全程不出服务端。本 API 就是这条通道。

> 口径是**分角色**的：文章 / 页面**作者**的 userName 在 Halo 本来就是公开的（`ContributorVo.name`、作者页 `/authors/{name}`）——目标用户是作者时，前端直接调公开 HTTP API 即可，不必动用本 API。

只需要浏览器端渲染、且你自己能拿到 userName 的场景，用[公开 HTTP API](theme-integration.md) 即可，不必引本 API。

### PublicIdentityQueryApi 参考

```java
Mono<PublicIdentity> getIdentity(String userName);                    // 单查；查无 / 用户被禁用 = 空 Mono
Flux<PublicIdentity> listIdentities(Collection<String> userNames);    // 批量；自动去重，上限 50
```

- 批量自动忽略空白项并去重；**去重后上限 50**，超出以 `IllegalArgumentException` 走 error 通道（与公开 HTTP 批量接口一致），更多请自行分批（评论分页等常见场景一般远小于此）。
- 查不到 / 被禁用的用户**不出现在结果中**（不占位、不报错），按 `userName()` 关联即可；单个用户聚合失败不影响整批。入参为 `null` / 全空白返回空 `Flux`。
- `getIdentity` 入参空白以 `IllegalArgumentException` 走 error 通道。
- 返回的是**当前佩戴**的装扮与身份标识，非装饰墙全量（装饰墙如有进程内需求，将来另行扩展）。

DTO `PublicIdentity` 与 `GET /identity/{userName}` 的 JSON 响应同构，自包含、零二次查询：

```text
PublicIdentity {
  userName        String            用户名（进程内关联用；见下方脱敏责任）
  displayName     String            显示名称
  avatar          String?           头像（绝对链接）
  bio             String?           公开简介
  registeredAt    Instant?          注册时间
  identityMarks   List<IdentityMark>    身份标识（如「管理员」，按角色映射、无需佩戴），优先级降序
  decorations     WornDecorations       当前佩戴的装扮
  display         DisplayConfig         展示密度配置快照
}

IdentityMark      { displayName, icon?, color?, priority }

WornDecorations {                    槽位为 null = 未佩戴 / 已失效 / 该类型被站长停用
  avatarFrame     Decoration?       头像框
  title           Decoration?       称号
  primaryBadge    Decoration?       主勋章
  badgeShowcase   List<Decoration>  勋章展示柜（≤6）
  cardBackground  Decoration?       名片背景
  nameStyle       Decoration?       昵称样式
}

Decoration {
  assetName, type, displayName, url?, mediaType?,
  titleMode?, titleText?, titleColor?, titleBackground?, titleBackgroundSecondary?,   称号专属
  nameStyle?: { mode: solid|gradient, colors[] },                          昵称样式专属
  rarityName?, rarityDisplayName?, rarityColor?,                           稀有度（已内联）
  grantedAt?, expiresAt?                                                   本接口当前恒为 null（装饰墙类查询才填充）
}

DisplayConfig     { identityLineShowPrimaryBadge, identityLineIdentityLimit,
                    userCardShowcaseBadgeLimit, userCardIdentityLimit }
```

- 列表字段永不为 `null`（无则空列表）。
- 称号双形态：`titleMode` 为 `text` 时按三色渲染文字牌；为 `image` 时图片地址在 `url`，`titleText` 作为替代文本与加载失败回落。

### 脱敏责任：转发给浏览器前必须剥掉 userName

本 API 返回 `userName`，供你在进程内做关联 / 缓存键。但若把查询结果**内嵌进你的公开 API 响应**：

- **必须剥掉 `userName` 再下发**（以及任何可反推用户名的字段）。依据：Halo 对评论者 / 回复者这类互动用户的用户名明确脱敏（防枚举 / 撞库清单）——不要替 Halo 解除它已做的脱敏；且展示渲染不需要 userName，一律剥掉永远无害。`displayName`、`avatar` 及各装扮展示字段可以下发。
- hip-* runtime 组件的 `data` 属性模式渲染**不依赖 `userName`**——剥掉后的数据可直接喂给组件（见[主题适配指南](theme-integration.md)）。

### 完整示例：评论装扮聚合

浏览器请求你的评论列表接口（不变）→ 你在后端组装作者 DTO 时批量查询、附加装扮、剥掉 `userName` → 前端零额外请求、不接触任何用户名。

把所有引用 `interaction-plus` 类的逻辑**收拢到一个独立组件**（原因见[降级与故障排查](#降级与故障排查)）：

```java
@Component
@RequiredArgsConstructor
public class AuthorIdentityClient {

    private final ExtensionGetter extensionGetter;

    /**
     * 批量查询作者公开身份，按 userName 索引。
     * interaction-plus 缺席 / 查询异常时返回空 Map，评论主流程不受影响。
     */
    public Mono<Map<String, PublicIdentity>> fetchIdentities(Collection<String> userNames) {
        return extensionGetter.getEnabledExtension(PublicIdentityQueryApi.class)
            .flatMapMany(api -> api.listIdentities(userNames))
            .collectMap(PublicIdentity::userName)
            .onErrorResume(e -> Mono.just(Map.of()));
    }
}
```

组装响应时（示意）：

```java
// 每条评论：owner 为登录用户 → 从 Map 取身份数据附加到作者对象（剥掉 userName），
// 匿名评论 / 查不到 → 不附加，接口行为与集成前完全一致
var identity = identityMap.get(comment.getSpec().getOwner().getName());
if (identity != null) {
    authorVo.setDecorations(identity.decorations());
    authorVo.setIdentityMarks(identity.identityMarks());
    authorVo.setDisplay(identity.display());
    // 不要把 identity.userName() 放进响应
}
```

### 查询最佳实践

- **只读可重试**：无副作用，重试 / 缓存随意。interaction-plus 侧已带聚合缓存（TTL 由站长配置），高频调用一般无需你再自建缓存。
- **展示开关归站长与用户**：站长可全局停用某类装扮（对应槽位变 `null`）、用户可更换 / 取下佩戴——不要假设某槽位一定有值，也不要缓存过久。

## 统计贡献扩展点

> 方向与前两个 API 相反：不是你调用 interaction-plus，而是**你提供实现、interaction-plus 在聚合用户身份时调用你**。

把你插件领域内的用户统计项（问答插件的「采纳数」、打赏插件的「获赏数」等）贡献到 interaction-plus 的**用户悬浮卡数据行**与公开身份数据中。数据始终保存在你自己的模型里——interaction-plus 只在聚合公开身份（缓存未命中）时调用一次，结果随公开身份整体缓存（站长可配 TTL，默认 30 秒），并经 hip-* 组件、主题 Finder、公开 HTTP API 三个读出口同源下发。

### 与发奖 API 的通道分工

- **业务统计**（采纳数、获赏数这类属于你自己领域的计数）→ 走本扩展点，数据留在你家，展示时报数；
- **数值资产**（给用户加积分 / 经验这类需要记账的）→ 走后续的喂分契约（规划中），必须进 interaction-plus 的账本。

### 接入三步

**第 1 步**：实现 `UserStatContributor`（依赖声明与[快速开始](#快速开始)相同）：

```java
@Component
public class QaUserStatContributor implements UserStatContributor {

    private final AnswerService answerService; // 你自己的领域服务

    @Override
    public Flux<UserStat> getUserStats(String userName) {
        // 查你自己的模型；建议内部自带缓存，保证快速返回
        return answerService.countAccepted(userName)
            .map(count -> new UserStat("accepted", "采纳", String.valueOf(count)))
            .flux();
    }
}
```

**第 2 步**：在你插件的资源 yaml 中**声明 ExtensionDefinition（必须）**：

```yaml
apiVersion: plugin.halo.run/v1alpha1
kind: ExtensionDefinition
metadata:
  name: your-plugin-user-stat-contributor
spec:
  className: com.example.yourplugin.QaUserStatContributor
  extensionPointName: user-stat-contributor
  displayName: "问答统计贡献"
```

**为什么必须**：每个贡献项对外携带的**来源插件 id 由系统盖章**——取自该 ED 上 Halo 自动打的 `plugin.halo.run/plugin-name` label，你无法自报也无法冒充他人；查不到 ED 归属的实现，其贡献项会被**整体忽略**（interaction-plus 侧打 warn 日志）。消费方以 `来源插件 id + key` 为完整标识，不同插件的同名 `key` 互不冲突。

**第 3 步**：以可选依赖声明 `"interaction-plus?": ">=0.1.0"`（与前述 API 相同；本插件缺席时你的实现只是无人调用，不影响你启动）。

### 约束与容错（超限即丢弃，不报错）

| 项 | 约束 |
|---|---|
| `key` | `^[a-z0-9][a-z0-9-]{0,31}$`，插件内唯一 |
| `label` | 展示名，1-16 字符 |
| `value` | 展示值文本（如 `"23"`、`"Lv.16"`，格式化由你决定、原样展示），1-32 字符 |
| 项数 | 每插件每用户至多 **5 项**，超出截断 |
| 时间预算 | 单贡献方总超时 **800ms**：超时或出错，本次聚合丢弃你的全部项，不影响其他数据与其他贡献方 |

实现建议：无数据返回空 `Flux`（不要 error）；慢查询自建缓存——你的返回结果已随公开身份缓存，但缓存未命中时的那一次调用计入你的 800ms 预算。

## 降级与故障排查

- **可选依赖缺席**：`interaction-plus` 没装时，它的类（含上述两个接口）不在 classpath。**务必把引用这些类的代码隔离在单独的类 / 组件里**，避免在主流程触发 `NoClassDefFoundError`；并对调用结果做 `onErrorResume` / 空值降级。可参考官方 `plugin-live2d` 对 `plugin-ai-foundation` 的可用性判断写法。
- **前向兼容**：接口响应 / DTO 未来可能**新增字段**（如等级 / 积分能力上线后的展示属性），消费方请忽略未知字段，勿做穷举式强校验。

### 取不到实现？自检清单

`getEnabledExtension(接口类)` 取不到实现时只返回**空 `Mono`**，核心侧**不打任何日志**——「没装」和「装了但失联」表现完全一样，无法从代码侧区分。请按序排查：

1. `interaction-plus` 是否**已安装且已启用**（后台 → 插件列表确认，装了但停用同样取不到）；
2. 版本是否过旧：`DecorationGrantApi` 与 `PublicIdentityQueryApi` 均自 `0.1.0` 起提供，`UserStatContributor` 扩展点同版本引入，更早的版本没有这些扩展点；
3. 站长是否在 Halo「设置 → 扩展点」里给该扩展点**指定过实现**：若指定后 `interaction-plus` 被卸载 / 重装，配置仍指向已不存在的实现，会**静默失联**——即便重新装回也取不到，需站长到扩展点设置里重新选择（或清除该项）；
4. 确认注入的是核心的 `run.halo.app.plugin.extensionpoint.ExtensionGetter`，且调用 `getEnabledExtension`（而非 `getExtensions`）。

## 版本兼容性

| 项 | 要求 |
|---|---|
| 运行环境 | Halo `>= 2.25`（随 `interaction-plus`）；`ExtensionGetter` 对插件开放需 Halo `>= 2.18` |
| `interaction-plus` | `>= 0.1.0`（两个 API 均自 `0.1.0` 起提供，以实际发布版本为准） |
| 构建环境 | api 模块以 **Java 21 字节码**发布，消费方需以 JDK 21 且编译目标（`release` / toolchain）≥ 21 构建；编译基线 Halo **2.21+ 即可**（api 对外仅暴露 `org.pf4j.ExtensionPoint` 与 Reactor，依赖均为 compileOnly、不进 POM），无需升到 2.25 |

## 相关文档

- [主题适配指南](theme-integration.md)——hip-* runtime 组件、公开 HTTP API、Finder（浏览器 / 主题侧）。
