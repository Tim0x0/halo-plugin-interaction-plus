# 自定义模板案例

本目录收录可直接使用的自定义模板案例。契约说明见[前台适配指南 · 自定义模板](../theme-integration.md#自定义模板)。

## alt-user-card —— 用户卡整卡替换案例

票根 + 通行证风格的整卡替换：左侧票根放头像，右侧通行证放身份行、称号、简介、数据统计与勋章展柜。

**安装**：后台 → 互动 → 装饰 → 自定义组件 → 用户卡，把 `alt-user-card.html` 全文贴进 HTML 框、`alt-user-card.css` 全文贴进 CSS 框，打开「启用自定义模板」并保存，刷新前台生效。

**案例覆盖的契约点**：

- `{{displayName}}` 占位符（触发器插槽的回落内容）
- `hipHelper.render*` 全家桶：`renderAvatar` / `renderName` / `renderMarks` / `renderBadge` / `renderTitle` / `escapeCssUrl`
- `hipData` 直读：统计数字、勋章展柜、卡背景、`profileUrl` 链接包裹头像与名字
- `popover` top layer 浮层：桌面端位置钳制、窄屏（≤640px）切全屏遮罩两档
- `:host([data-hip-dark])` 暗色适配
- `onCleanup` 清理全局监听与定时器
- 数据缺失兜底：模板数据未加载降级、空简介占位文案、空展柜 / 空统计整块移除

改外观直接改这两个文件再贴回去即可；自定义模板不受内置用户卡的尺寸设计限制。
