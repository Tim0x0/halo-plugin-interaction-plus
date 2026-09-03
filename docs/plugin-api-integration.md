# Interaction Plus 对外插件 API 对接指南

面向**其他 Halo 插件开发者**。`interaction-plus` 通过 Halo 扩展点向其他插件开放进程内能力：

| API | 接口 | 方向 | 用途 |
|---|---|---|---|
| [发奖 API](#发奖-api) | `DecorationGrantApi` | 写 | 在用户达成你定义的条件时发放 / 撤销装饰（勋章、头像框、称号、名片背景、昵称样式） |
| [身份查询 API](#身份查询-api) | `PublicIdentityQueryApi` | 读 | 在你的插件后端查询用户公开身份与装扮，内嵌进你自己的接口响应 |
| [统计贡献](#统计贡献扩展点) | `UserStatContributor` | 反向 | 把你领域内的用户统计项贡献到用户卡数据行与公开身份 |

前两个 API 的接入方式完全相同（见[快速开始](#快速开始)），可按需只用其一。统计贡献是反向扩展点，由本插件调用你的实现。

> 对外 API 版本为 `1.0.0`，以下内容是该版本的当前契约。
>
> 以下能力仅在 `interaction-plus` **已安装并启用**时可用，请做好[降级](#降级与故障排查)。

## 独立插件前台页加载 Runtime

如果你的插件提供由 Halo 渲染的 Thymeleaf 前台页面，并在页面中输出 `hip-*`，可以直接使用 Interaction Plus 注册到模板引擎的 Finder；不需要额外的版本接口或重定向：

```html
<th:block th:if="${pluginFinder.available('interaction-plus')}">
  <script th:src="${interactionPlus.getRuntimeUrl()}"></script>
</th:block>
```

`getRuntimeUrl()` 返回带当前安装版本的完整 Runtime 地址，使固定文件名对应当前版本的浏览器缓存。若你的插件已声明 `interaction-plus` 为必需依赖，可以省略外层可用性判断。Finder 属于模板能力；插件 Java 后端要调用数据或发奖，仍按下文使用 `ExtensionGetter`。

## 快速开始

共 3 步。

### 第 1 步：添加 api 依赖

`api` 模块通过 **JitPack** 提供。先添加仓库（推荐放在 `settings.gradle` 的 `dependencyResolutionManagement`，或根 `build.gradle`；JitPack 建议排在其他仓库之后）：

```gradle
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}
```

再声明依赖（`compileOnly`——运行时由 interaction-plus 提供）：

```gradle
dependencies {
    compileOnly 'com.github.Tim0x0:halo-plugin-interaction-plus:1.0.0'
}
```

> 注意坐标是**仓库级扁平坐标**（group=GitHub 用户、artifact=仓库名），没有 `:api:` 段：
> 本项目用自定义 `install` 命令构建，JitPack 会把产物归一到这个坐标。
> `api` 模块只包含接口与 DTO；运行时实现由 Interaction Plus `1.0.0` 提供。

### 第 2 步：声明可选依赖

在你的 `plugin.yaml` 中：

```yaml
spec:
  pluginDependencies:
    "interaction-plus?": ">=1.0.0"   # 末尾问号 = 可选依赖
```

可选依赖表示 `interaction-plus` 没装 / 没启用时，**你的插件照常启动**，只是对应能力不可用。

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

`sourcePlugin` 传**你自己的插件标识**（你 `plugin.yaml` 的 `metadata.name`）。用于来源记录与撤销隔离——`revoke` 只会撤**你自己**发放的那条授予，动不到别人（其他插件 / 站长后台）发的。实现只校验非空，不做前缀拦截；请不要占用别人的插件名。

发放结果 `GrantResult.status`：

| 状态 | 含义 |
|---|---|
| `GRANTED` | 已新发放（他源是否发过与此无关） |
| `RENEWED` | 已持有你发的授予，本次已延长有效期（仅新值更晚才刷新） |
| `ALREADY_HELD` | 已持有你发的授予且本次有效期不更晚，幂等跳过 |
| `DECORATION_NOT_FOUND` | `decorationName` 不存在（含正在删除中的装饰） |
| `DECORATION_INACTIVE` | 装饰未启用 |
| `DECORATION_NOT_EXTERNALLY_GRANTABLE` | 该稀有度被站长禁止外发 |
| `USER_NOT_FOUND` | 用户不存在 |

便捷方法 `isHeld()`：`status ∈ {GRANTED, RENEWED, ALREADY_HELD}`，即「调用后用户持有该装饰」——只关心结果、不关心新发 / 续期 / 已持有区别时，用它做归一判断。`grantName` 仅在这三个持有态有值（授予记录标识），其余状态为 `null`。

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

- **发放只进库存，不自动佩戴**：`grant` 成功后装饰进入用户库存，用户需在「用户中心 → 装扮 → 我的装扮」自行佩戴，前台才会显示。身份标识不走库存、按角色映射即时生效，与本 API 无关。
- **你发的授予也可能被站长收回**：站长可在后台撤销任何来源的授予（含你发放的），不要假设"发过就永远存在"。持续型场景的周期性重发会自然重建；一次性场景可按 `grant` 返回状态感知。
- **稀有度准入**：站长可关闭某些稀有度（如传说 / 限定）的「允许外部发放」开关。被关掉的装饰不会出现在 `listGrantable`，`grant` 也会返回 `DECORATION_NOT_EXTERNALLY_GRANTABLE`——这是站长的有意控制，你的插件按结果状态处理即可。
- **不要硬编码 ulid**：装饰删了重建后 `metadata.name` 会变。建议让站长配置 / 选择，而不是把某个 `asset-xxx` 写死在代码里。
- **网络重试安全**：`grant` / `revoke` 幂等（见[发放模型](#发放模型)），可放心重试。

## 身份查询 API

> **与其他读出口同源**：本 API 与公开 HTTP API（`GET /identity/{userName}`）、Halo 模板 Finder（`${interactionPlus.getIdentity(...)}`）共用同一聚合服务与缓存，返回内容、判空行为、对用户禁用 / 删除的处理三个出口一致，仅出口形态不同。只读、无副作用，可任意重试。

### 适用场景：为什么在后端查

已有的两条读通道各有边界：**公开 HTTP API** 给浏览器前端 fetch 用，调用方必须持有 `userName`；**Finder** 注册进 Halo 模板引擎，任何由 Halo 渲染的 Thymeleaf 模板都可以调用（包括主题与独立插件前台页），但其他插件的 Java 后端受插件容器隔离，不能把 Finder 当进程内服务注入。

**典型缺口**：你想在自己插件的公开 API 响应里内嵌用户装扮（如评论插件给评论作者附带装扮数据），但 Halo 公开评论查询**不返回评论者 / 回复者的 `userName`**（只保留 displayName / avatar）——前端拿不到用户名，无从调用 HTTP 批量接口，查询必须发生在你的**后端**。本 API 就是这条通道。

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
- 返回的是**当前佩戴**的装扮与身份标识，不提供装饰墙全量列表。

DTO `PublicIdentity` 与 `GET /identity/{userName}` 共用同一聚合源，当前身份与佩戴装饰的展示字段口径一致；数据自包含、零二次查询：

```text
PublicIdentity {
  userName        String            用户名（metadata.name）。进程内关联键；喂给 hip-* 的 data 模式时必须保留——默认跳转 /authors/{name} 用它拼 href
  displayName     String            显示名称
  avatar          String?           头像（绝对链接）
  bio             String?           公开简介
  registeredAt    Instant           注册时间（spec.registeredAt 缺失时回退资源创建时间，恒有值）
  identityMarks   List<IdentityMark>    身份标识（如「管理员」，按角色映射、无需佩戴），优先级降序
  decorations     WornDecorations       当前佩戴的装扮
  stats           Stats                 互动统计（公开口径；含其他插件贡献项）
  display         DisplayConfig         站点级展示策略快照（按组件场景的开关 / 密度 / 跳转 / 无头像占位风格），非用户属性
}

IdentityMark      { displayName, icon?, color?, priority }   // icon 与 color 互斥：文字牌用 color，图标(data URL)/图片用 icon；displayName 同时作图标提示与裂图回落

Stats {
  posts           long                  公开文章数（已发布、可见性公开）
  comments        long                  公开评论数（已审核、未隐藏）
  decorations     DecorationCounts?     装扮持有计数；null = 用户关闭了公开装扮墙
  extras          List<ContributedStat> 其他插件经 UserStatContributor 贡献的统计项
}

DecorationCounts  { total, badge, avatarFrame, title, nameStyle, cardBackground }
                  （有效授予且资产可用，按资产去重）

ContributedStat   { source, key, label, value }
                  source = 来源插件 id（系统按 ExtensionDefinition 归属盖章，不可自报）

WornDecorations {                    槽位为 null = 未佩戴 / 已失效 / 该类型被站长停用
  avatarFrame     Decoration?       头像框
  title           Decoration?       称号
  primaryBadge    Decoration?       主勋章
  badgeShowcase   List<Decoration>  勋章展示柜（≤8）
  cardBackground  Decoration?       名片背景
  nameStyle       Decoration?       昵称样式
}

Decoration {
  assetName, type, displayName, url?, mediaType?,
  titleText?, titleColor?, titleBackground?, titleBackgroundSecondary?,     称号专属
  nameStyle?: { mode: solid|gradient, colors[] },                          昵称样式专属
  rarityName?, rarityDisplayName?, rarityColor?                            稀有度（已内联）
}

DisplayConfig {
  identityLine    { showTitle, showPrimaryBadge, showNameStyle, showIdentityMarks, identityLimit }
  avatar          { showFrame }
  userCard        { showTitle, showPrimaryBadge, showShowcase, showNameStyle,
                    showIdentityMarks, showAvatarFrame, showCardBackground,
                    showcaseBadgeLimit, identityLimit }
  userCardLinkTemplate    昵称 / 用户卡头像的跳转链接模板（{name} = 用户名），空表示不跳转
  avatarFallbackStyle     无头像占位：halo 灰底首字母 / hash 按显示名着色；只作用于内置 renderAvatar，不改 avatar 字段
}
                  // 站点级策略，非用户属性；自行渲染可整个忽略（见下条）
                  // 类型总闸不在这里：关了对应 decorations 槽位 / identityMarks 已是空
```

- 列表字段永不为 `null`（无则空列表）。
- **`display` 是站点级展示策略，不是该用户的属性**：这几项全站一份，与查的是谁无关，随站长改设置而变（缓存会一并失效）。内联在此只为让 `data` 模式零二次查询——把整份喂给 hip-* 就能画。**自己渲染界面的消费方可以整个忽略它**；尤其别拿 `userCard.identityLimit` 之类去裁剪并非用户卡的界面，那是按本插件的组件场景定的，不是给你的场景定的。
- `userName` 不是可剥的内部字段：内嵌进你的公开响应、再喂给 hip-* 的 `data` 时必须带上。runtime 用它和 `display.userCardLinkTemplate` 算 `profileUrl`；缺了头像 / 名字不跳转。站长清空跳转模板则两处一起关，与字段在不在无关。
- 称号 = 名称 + 可选的图，没有形态开关：`titleText` 恒非空；`titleColor` / `titleBackground` / `titleBackgroundSecondary` 可空（`#RGB` / `#RRGGBB` / `#RRGGBBAA`，8 位带透明度；空=无专属色 / 无底）。`url` 有值时是称号图、同时 `titleText` 作它的替代文本与加载失败兜底。**行内场景（评论、列表）建议只用 `titleText`**——称号图多是横条插画，缩到一行文字的高度图里的字会看不清；图适合用户卡这类有垂直空间的位置。

### 完整示例：评论装扮聚合

浏览器请求你的评论列表接口（不变）→ 你在后端组装作者 DTO 时批量查询、附加装扮 → 前端零额外请求。

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
// 每条评论：owner 为登录用户 → 从 Map 取身份数据附加到作者对象，
// 匿名评论 / 查不到 → 不附加，接口行为与集成前完全一致。
// 整份 PublicIdentity 可直接当 hip-* 的 data（含 userName）：
// 缺了它，卡和身份行就算到了 display.userCardLinkTemplate 也拼不出跳转。
var identity = identityMap.get(comment.getSpec().getOwner().getName());
if (identity != null) {
    authorVo.setIdentity(identity);
}
```

### 查询最佳实践

- **只读可重试**：无副作用，重试 / 缓存随意。interaction-plus 侧已带聚合缓存（TTL 由站长配置），高频调用一般无需你再自建缓存。
- **展示开关归站长与用户**：站长可全局停用某类装扮（对应槽位变 `null`）、用户可更换 / 取下佩戴——不要假设某槽位一定有值，也不要缓存过久。

## 统计贡献扩展点

> 方向与前两个 API 相反：不是你调用 interaction-plus，而是**你提供实现、interaction-plus 在聚合用户身份时调用你**。

把你插件领域内的用户统计项（问答插件的「采纳数」、打赏插件的「获赏数」等）贡献到 interaction-plus 的**用户卡数据行**与公开身份数据中。数据始终保存在你自己的模型里——interaction-plus 只在聚合公开身份（缓存未命中）时调用一次，结果随公开身份整体缓存（站长可配 TTL，默认 30 秒），并经 hip-* 组件、Halo 模板 Finder、公开 HTTP API 三个读出口同源下发。

本扩展点只接收展示用的业务统计（如采纳数、获赏数）；积分、经验等记账资产不属于这个接口。

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

**第 3 步**：按[快速开始](#快速开始)声明可选依赖（本插件缺席时你的实现只是无人调用，不影响你启动）。

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
- 只读取业务需要的 DTO 字段，不要对整个对象做穷举式字段校验。

### 取不到实现？自检清单

`getEnabledExtension(接口类)` 取不到实现时只返回**空 `Mono`**，核心侧**不打任何日志**——「没装」和「装了但失联」表现完全一样，无法从代码侧区分。请按序排查：

1. `interaction-plus` 是否**已安装且已启用**（后台 → 插件列表确认，装了但停用同样取不到）；
2. 站长是否在 Halo「设置 → 扩展点」里给该扩展点**指定过实现**：若指定后 `interaction-plus` 被卸载 / 重装，配置仍指向已不存在的实现，会**静默失联**——即便重新装回也取不到，需站长到扩展点设置里重新选择（或清除该项）；
3. 确认注入的是核心的 `run.halo.app.plugin.extensionpoint.ExtensionGetter`，且调用 `getEnabledExtension`（而非 `getExtensions`）。

## 接入要求

| 项 | 要求 |
|---|---|
| 运行环境 | Interaction Plus 要求 Halo `>= 2.25`；`ExtensionGetter` 对插件开放需 Halo `>= 2.18` |
| 依赖版本 | Interaction Plus `1.0.0`；api 构件 `1.0.0` |
| 构建环境 | `api` 模块使用 Java 21 字节码，消费方使用 JDK 21 且编译目标（`release` / toolchain）≥ 21；对外类型只依赖 `org.pf4j.ExtensionPoint` 与 Reactor，相关依赖均为 `compileOnly` |

## 前端输出 hip-* 标签时：带上 scene

如果你的插件在**浏览器端**渲染用户身份（评论插件是典型），直接写 `hip-*` 标签即可，但请带上 `scene` 属性声明场景：

```html
<hip-user-avatar   user-name="tim" scene="comment"></hip-user-avatar>
<hip-user-identity user-name="tim" scene="comment"></hip-user-identity>
```

- **默认不要指定尺寸**。没人覆盖时，多大由站长模板按 `scene` 决定（内置默认：`comment` 的头像是 36px）。组件没有 `size` 属性，尺寸不走标签属性。
- **要改你自己输出的那些**：在标签或容器上设 `--hip-avatar-size`（只影响你种下去的实例，不是全站这个 scene 词）。用法见[前台适配指南 · 调用方覆盖头像尺寸](theme-integration.md#调用方覆盖头像尺寸)。
- **推荐词表**：`comment`（评论）/ `post`（正文）/ `moment`（瞬间）/ `sidebar`（侧栏）/ `profile`（个人页）/ `list`（列表）。插件不校验取值，但各造各的词会让站长为一堆同义词写规则。
- 六个词在**头像**的内置模板里都有对应尺寸；身份行与用户卡的内置模板不消费 `scene`（站长可自行写规则）。哪个组件吃哪些词见[场景分档](theme-integration.md#场景分档)。
- `scene` 是**场景类别不是位置实例**：你的插件若有两处语义不同的位置，别都填 `comment`，换个更具体的词。需要第二个维度时，宿主上的任何属性都能当 CSS 挂点（如 `data-area="..."`），不必挤在 `scene` 里。
- 不填也能跑，走模板的默认形态。
- 前提是页面已引入 runtime（见[前台适配指南](theme-integration.md#引入-runtime)）。你的插件若有独立路由页，需要自己引一次。
- **另起自己的 CSS 变量或 scene 词**想让站长写进自定义模板时，**你必须自己给站长出说明**。变量写清名字、用在哪、不设时怎样；scene 写清词是什么、用在哪。本插件文档只保证 `--hip-avatar-size` 和推荐六词，不替你列那些名字。推荐词能用就别另起，免得站长为一堆同义词写规则。

## 相关文档

- [站长使用指南](site-admin.md)——后台创建装饰、授予、稀有度「允许外部发放」、权限与设置。
- [前台适配指南](theme-integration.md)——hip-* runtime 组件、公开 HTTP API、Finder（Halo 服务端模板侧）。
- [自定义模板](theme-integration.md#自定义模板)——站长可在后台贴 HTML/CSS 完全接管 `hip-*` 组件的渲染（标签名不变，第三方消费方零改动、无需感知）。身份数据与本文 API / HTTP / Finder **同源**，仅出口不同。
