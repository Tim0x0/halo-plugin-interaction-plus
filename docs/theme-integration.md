# Interaction Plus 主题适配指南

面向 **Halo 主题作者**：如何在主题中展示用户的装扮（头像框、称号、勋章、身份标识、名片背景等）。

> 插件名：`interaction-plus`。以下资源仅在插件**已安装并启用**时可用，主题应做好降级（见[降级与兼容](#降级与兼容)）。

## 能力总览

插件面向主题提供三套对外能力（前两套都是「取数据」，按渲染方式选）：

| 能力 | 渲染方式 | 适合 |
|---|---|---|
| [Runtime Web Component](#web-component-组件) | 客户端 | 最省事：引入一个 JS，写自定义标签即可，无需懂数据结构。绝大多数场景 |
| [Finder API](#finder-api主题-ssr) | 服务端（SSR） | 需要完全自定义 HTML、SEO 友好、首屏不闪 |
| [公开 HTTP API](#公开-http-api) | 自定义 | 前端 fetch 取数据自己画 |

## 引入 Runtime

在主题模板（如 `templates/footer.html`）按需引入：

```html
<link rel="stylesheet"
      href="/plugins/interaction-plus/assets/runtime/interaction-plus.runtime.css" />
<script src="/plugins/interaction-plus/assets/runtime/interaction-plus.runtime.js"></script>
```

- 产物为 IIFE 格式，引入即自动注册 `hip-*` 自定义元素。
- **不会自动注入任何页面**，由主题决定在哪里放组件。
- 资源路径固定为 `/plugins/interaction-plus/assets/runtime/**`（由插件 ReverseProxy 暴露）。
- 插件升级后浏览器可能沿用缓存的旧产物；主题可在引入 URL 加查询参数（如 `?v=1`，随插件升级递增）强制刷新。

## Web Component 组件

共 3 个组件。通用约定：

- `user-name`：传用户名，组件自动请求该用户的公开身份数据。
- `data`：直接传入公开身份 JSON（结构见[公开 HTTP API](#公开-http-api)），**优先于 `user-name`**，可避免逐个请求。
- 请求失败时**静默降级**，不报错；素材图片加载失败自动兜底（头像回退首字母占位、身份标识图标回退文字牌），不产生空洞。
- 使用 Shadow DOM，样式隔离，通过 CSS 变量定制（见 [CSS 变量定制](#css-变量定制)）。

### hip-user-avatar：头像 + 头像框

```html
<hip-user-avatar user-name="alice" size="40px"></hip-user-avatar>
```

- `size`：尺寸，仅接受「数字 + `px` / `em` / `rem` / `%`」（如 `40px`、`2.5em`）；默认 `40px`，非法值安全回落默认值。头像框自动跟随容器缩放。

### hip-user-identity：身份行

昵称样式 + 身份标识 + 称号 + 主勋章，最适合**评论区 / 文章作者**的昵称行：

```html
<hip-user-identity user-name="alice"></hip-user-identity>
```

### hip-user-card：用户悬浮卡片

组合头像、身份、称号、名片背景、互动统计、勋章展柜与简介的大卡（**桌面端尺寸恒定 560×296**）：
整卡背景全彩展示（顶部 60px 露出带）+ 半透明内容层；卡片高度不随佩戴内容浮动（全站名片规格统一，
背景素材按 560:296 比例居中裁切恒定，推荐素材 1120×592）；个人说明区固定三行（空值占位）；
数据行内置文章 / 评论 / 勋章计数，并自动接入其他插件贡献的统计项。
**点击触发元素显示 / 再点关闭**（键盘 Enter/Space 等效），外点 / Esc 关闭；
卡内**头像与名字可点击跳转用户页**——链接模板在后台「装扮展示」设置（`{name}` 替换为用户名，
默认 `/authors/{name}` 即 Halo 主题作者页；主题没有作者页或想关闭跳转时清空该设置即可）；
桌面端锚定触发元素展开并在水平方向自动钳制在视口内，窄屏（≤640px）切换为固定全宽卡 +
半透明遮罩、高度随内容自适应（上限 85vh 内部滚动）、页面滚动即关闭：

```html
<hip-user-card user-name="alice">
  <!-- 默认插槽 = 触发元素（如用户名 / 头像） -->
  <span>alice</span>
</hip-user-card>
```

### 批量场景：用 data 避免 N 次请求

列表 / 评论区有多个用户时，先用 `POST /identities` 批量取数据，再给每个组件传 `data`：

```html
<hip-user-identity data='{"userName":"alice","displayName":"Alice", ...}'></hip-user-identity>
```

## 评论区与作者信息接入

Halo 评论区由主题渲染。在主题模板里把用户名包进组件即可（变量名以你的主题为准）：

```html
<!-- 评论作者：悬浮卡片 -->
<hip-user-card th:user-name="${comment.spec.owner.name}">
  <span th:text="${comment.spec.owner.displayName}">用户名</span>
</hip-user-card>

<!-- 或直接渲染身份行 -->
<hip-user-identity th:user-name="${author.metadata.name}"></hip-user-identity>
```

## Finder API（主题 SSR）

对齐 Halo 主题集成正道：在 Thymeleaf 模板里直接调用 `${interactionPlus.xxx}`，**服务端渲染**取用户身份与装扮——SEO 友好、首屏不闪，适合需要完全自定义 HTML 的场景（Web Component 是客户端渲染的便捷替代）。

变量名：`interactionPlus`，两个方法：

| 方法 | 返回 | 说明 |
|---|---|---|
| `${interactionPlus.getIdentity('alice')}` | 身份对象 | 同[公开 HTTP API](#公开-http-api) 的 `/identity` 结构（基础信息 + 佩戴装饰 + 身份标识 + 互动统计 + 展示配置） |
| `${interactionPlus.getDecorations('alice')}` | 装饰数组 | 同 `/identity/{u}/decorations`（装饰墙，尊重公开开关）；差异：用户不存在或被禁用时 Finder 返回空数组（模板无需判空报错），HTTP API 返回 404 |

> Finder 与公开 HTTP API **同源**（共用 `PublicIdentityService`），返回结构完全一致，只是出口不同：Finder 给模板 SSR，HTTP API 给前端 fetch。返回字段详见[公开 HTTP API](#公开-http-api) 的响应示例。

用法：

```html
<!-- 作者身份行（SSR） -->
<div th:with="id = ${interactionPlus.getIdentity(author.metadata.name)}"
     th:if="${id != null}">
  <img th:src="${id.avatar}" th:alt="${id.displayName}" />
  <span th:text="${id.displayName}"></span>
  <!-- 称号（若佩戴）：整图形态渲染图片（titleText 为替代文本），文字牌形态渲染文本 -->
  <th:block th:if="${id.decorations?.title}" th:with="t = ${id.decorations.title}">
    <img th:if="${t.titleMode == 'image' and t.url != null}"
         th:src="${t.url}" th:alt="${t.titleText}" style="height: 20px" />
    <span th:unless="${t.titleMode == 'image' and t.url != null}"
          th:text="${t.titleText}"></span>
  </th:block>
</div>

<!-- 装饰墙（个人主页 / 作者页） -->
<ul>
  <li th:each="d : ${interactionPlus.getDecorations(profileUser.name)}">
    <img th:src="${d.url}" th:title="${d.displayName}" />
  </li>
</ul>
```

- 用户不存在 / 被禁用时 `getIdentity` 返回空，用 `th:if` 判空。
- 返回的 `Mono` 由 Halo 主题引擎自动订阅解包，模板里直接当结果对象用，无需手动 `.block()`。

## 公开 HTTP API

Base：`/apis/api.interaction-plus.timxs.com/v1alpha1`（游客可访问）。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/identity/{userName}` | 单用户公开身份：当前佩戴的装扮 + 身份标识 + 互动统计 + 展示密度配置 |
| POST | `/identities` | 批量，body `{"userNames":["a","b"]}`，单次最多 50、自动去重；响应 `{"items":[身份对象…],"skipped":["查无或不可用的用户名…"]}` |
| GET | `/identity/{userName}/decorations` | **装饰墙**：该用户获得的全部有效装饰（自包含完整信息；尊重用户「公开装扮墙」开关，关闭则返回空数组；用户不存在或被禁用时 404，与 `/identity/{userName}` 口径一致） |

`/identity/{userName}` 返回（节选）：

```jsonc
{
  "userName": "alice",
  "displayName": "Alice",
  "avatar": "https://.../avatar.png",
  "bio": "……",
  "registeredAt": "2021-03-01T00:00:00Z",
  "identityMarks": [{ "displayName": "管理员", "icon": "...", "color": "#f00" }],
  "decorations": {
    "avatarFrame": { "assetName": "...", "type": "avatar_frame", "url": "..." },
    "title": { "type": "title", "titleMode": "text", "titleText": "...", "titleColor": "...", "titleBackground": "..." },
    "primaryBadge": { "type": "badge", "url": "..." },
    "badgeShowcase": [{ "type": "badge", "url": "..." }],
    "cardBackground": { "type": "card_background", "url": "..." },
    "nameStyle": { "type": "name_style", "nameStyle": { "mode": "gradient", "colors": ["#...","#..."] } }
  },
  "stats": {
    "posts": 128,
    "comments": 356,
    "decorations": { "total": 12, "badge": 9, "avatarFrame": 1, "title": 1, "nameStyle": 1, "cardBackground": 0 },
    "extras": [{ "source": "some-qa-plugin", "key": "accepted", "label": "采纳", "value": "23" }]
  },
  "display": { "identityLineShowPrimaryBadge": true, "identityLineIdentityLimit": 1, "userCardShowcaseBadgeLimit": 3, "userCardIdentityLimit": 3, "userCardLinkTemplate": "/authors/{name}" }
}
```

**互动统计 `stats`**（用户悬浮卡数据行与「加入时间」的数据源）：`posts` / `comments` 为公开口径计数（已发布文章、已审核评论）；`stats.decorations` 为各类装扮的持有计数，**为 `null` 表示用户关闭了公开装扮墙**（此时请勿显示勋章计数）；`extras` 为其他插件贡献的统计项（`source` 是来源插件 id，`value` 已格式化、原样展示即可）。

**称号双形态**：`titleMode` 为 `text` 时按 `titleColor` / `titleBackground`（可选 `titleBackgroundSecondary`，两者形成 135° 渐变）渲染文字牌；为 `image` 时图片地址在 `url`，`titleText` 作为替代文本与加载失败时的回落文案。整图称号建议按场景限高：行内随文字行缩放（默认 `1.25em`，16px 正文下约 `20px`）、卡片等独立展示位约 `32px`，并设最大宽度防超宽横条破坏排版——`hip-user-*` 组件已内置该约束（`max-height` + `max-width` 双上限，图按自身比例取最优尺寸，可用 `--hip-title-img-height` / `--hip-title-img-max-width` 覆盖），自渲染的主题请自行限制。

`/identity/{userName}/decorations` 返回装饰数组（每项自包含，无需再按 id 查询）：

```jsonc
[
  { "assetName": "asset-x", "type": "badge", "displayName": "早期用户",
    "url": "https://.../badge.png",
    "rarityName": "rarity-epic", "rarityDisplayName": "史诗", "rarityColor": "#a335ee",
    "grantedAt": "2026-06-01T08:00:00Z", "expiresAt": null }
]
```

- 稀有度显示名 / 颜色已内联，主题可直接展示（`rarityName` 内部名仅作样式标识用）；`grantedAt` 为获得时间；`expiresAt` 为 `null` 表示永久。

> 装饰墙适合"有个人主页 / 作者页"的主题做荣誉墙；用户可在「个人中心 → 装扮」里关闭公开。

## CSS 变量定制

组件使用 Shadow DOM，可在组件标签或其祖先元素上设置 CSS 变量：

**`hip-user-card`**：

| 变量 | 默认 | 说明 |
|---|---|---|
| `--hip-card-width` | `560px` | 卡片宽度（另有 `calc(100vw - 24px)` 响应上限兜底；窄屏 ≤640px 由固定全宽形态接管，此变量不生效） |
| `--hip-card-radius` | `12px` | 卡片圆角 |
| `--hip-card-hero-height` | `60px` | 顶部背景露出带高度 |
| `--hip-card-surface-height` | `236px` | 内容层固定高度（卡片总高 = 露出带 + 此值，恒定不随内容浮动；窄屏全宽形态下高度改为随内容自适应，此变量不生效） |
| `--hip-card-hero-fallback` | 低饱和灰渐变 | 未佩戴背景装扮时露出带的占位背景（`background-image` 值） |
| `--hip-card-surface` | `rgba(255,255,255,.85)` | 内容层底色（半透明素玻璃）；暗色主题覆盖为墨色系即可整卡切暗 |
| `--hip-card-fallback-bg` | `#f0f1f3` | 背景图加载前的卡片兜底底色 |
| `--hip-card-shadow` | `0 8px 28px rgba(31,35,40,.16)` | 阴影 |
| `--hip-card-text` | `#24292f` | 主文字色（名字、数据数值） |
| `--hip-card-text-secondary` | `#57606a` | 次文字色（说明、标识、称号） |
| `--hip-card-text-muted` | `#8b949e` | 弱文字色（数据标签、加入时间、空说明占位） |
| `--hip-card-line` | `rgba(31,35,40,.1)` | 分隔线与「+N」虚线格 |
| `--hip-card-slot-bg` / `--hip-card-slot-hover-bg` | `rgba(31,35,40,.045)` / `.1` | 勋章展柜收藏格底色 / 悬停底色 |
| `--hip-badge-size` | 卡片 `20px` / 行内 `1.25em` | 名字行主勋章尺寸（默认按场景分化：卡片固定、行内随正文字号缩放——1.25em 低于正文行高，任何字号下不撑行；主题显式设置则两场景同值） |
| `--hip-title-img-height` | `32px` | 整图称号最大高度（max-height，图按自身比例在双上限内取最优尺寸） |
| `--hip-title-img-max-width` | `100%` | 整图称号最大宽度（信息列宽度即上限) |

暗色主题示例（只覆盖 surface 变量组，结构与背景层零改动）：

```css
.dark hip-user-card {
  --hip-card-surface: rgba(22, 25, 30, 0.85);
  --hip-card-text: #e9edf2;
  --hip-card-text-secondary: #b6bec8;
  --hip-card-text-muted: #7d8590;
  --hip-card-line: rgba(233, 237, 242, 0.12);
  --hip-card-slot-bg: rgba(233, 237, 242, 0.07);
  --hip-card-slot-hover-bg: rgba(233, 237, 242, 0.14);
}
```

**`hip-user-identity`**：`--hip-identity-gap`(`4px`)、`--hip-badge-size`(行内默认 `1.25em`，与标识图标同尺寸)、`--hip-text-color`、`--hip-muted-color`、`--hip-title-radius`(`4px`，标识 / 称号牌圆角，与卡片元素圆角同档)、`--hip-title-img-height`(行内默认 `1.25em`——16px 正文下即 20px，随正文字号缩放不撑行)、`--hip-title-img-max-width`(`200px`，保险丝，约 10:1 宽高比上限)。

**`hip-user-avatar`**：用 `size` 属性控制尺寸。

示例：

```css
hip-user-card { --hip-card-width: 500px; --hip-badge-size: 20px; }
hip-user-identity { --hip-identity-gap: 6px; }
```

## 降级与兼容

- 插件未安装 / 未启用：`hip-*` 标签是未注册的自定义元素，浏览器**忽略标签本身但保留插槽内容**——所以 `<hip-user-card>用户名</hip-user-card>` 仍会显示"用户名"。建议主题始终在插槽里放原始内容。
- 接口失败 / 用户无装扮：组件静默降级（`hip-user-identity` 退为纯昵称、`hip-user-card` 仅显示触发内容）。
- 资源 URL 仅在插件启用时有效，主题可在引入前做插件存在性判断（可选）。
- **前向兼容**：公开 HTTP API / Finder 的返回结构未来可能**新增字段**（如等级 / 积分能力上线后的展示属性），主题请忽略未知字段，勿做穷举式强校验。

## 相关文档

你写的是**插件**而非主题？插件后端对接走[对外插件 API 对接指南](plugin-api-integration.md)（经 `ExtensionGetter` 进程内调用，与本文的 HTTP / Finder 同源）：发奖 API 发放 / 撤销装饰（写），身份查询 API 后端查询用户公开身份与装扮（读）。
