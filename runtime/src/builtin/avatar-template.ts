// avatar 组件的内置默认模板
import type { TemplateSource } from '../template-engine'

/** HTML 框：头像 + 头像框叠放，一句话的事。 */
const HTML = `<span class="wrapper"></span>
<script>
  root.querySelector('.wrapper').append(hipHelper.renderAvatar(hipData))
</script>`

/**
 * CSS 框。
 *
 * <p>尺寸走**纯相对制**：`:host` 上的 `font-size` 就是头像边长，内部一律 em，
 * 于是换一个场景档位只需改一行。调用方用 `scene` 属性声明上下文
 * （`<hip-user-avatar user-name="tim" scene="comment">`），没人覆盖时由这里的
 * 回落数字决定。调用方要改**自己种下去的那些**，在标签或容器上设
 * `--hip-avatar-size`（见主题指南）。
 */
const CSS = `*,
*::before,
*::after {
  box-sizing: border-box;
}

/* font-size 即头像边长，内部全部 em —— 换档位只改这一处。
   ⚠ display 与 box-sizing 必须显式声明：宿主页的 reset / preflight 穿不进 Shadow DOM。
   ⚠ 这里**刻意不设 position**：内部三层的参照系是 .wrapper（它自己是 relative），
   宿主不参与定位。设了 relative 会让每个头像都变成「定位元素」，在用户卡回落到
   非 top layer 形态时正好具备盖住卡面的资格 —— 加回来之前先想清楚这一条
   ⚠ 只读 --hip-avatar-size，绝不在 :host / :root 上定义它，也不给它 @property
   initial-value：一写，祖先覆盖或 scene 回落就失效 */
:host {
  display: inline-block;
  font-size: var(--hip-avatar-size, 40px);
  width: 1em;
  height: 1em;
  vertical-align: middle;
}

/* 场景分档：值由调用方（主题 / 第三方插件）填在 scene 属性上。
   匹配不上就用上面的默认 40px，不会破版。
   推荐词表 comment / post / moment / sidebar / profile / list **六个词全部在此有档**——
   推了词却不给效果，站长第一反应是「坏了」而不是「这个词没做」。

   分档取值对齐官方各位置的头像尺寸：comment 36 / post 40 / moment 48；
   sidebar / list / profile 无对应位置，自定一档

   调用方要改自己种下去的那些，设 --hip-avatar-size；没设才走这里的回落。 */
:host([scene="comment"]) {
  font-size: var(--hip-avatar-size, 36px);
}

:host([scene="post"]) {
  font-size: var(--hip-avatar-size, 40px);
}

:host([scene="moment"]) {
  font-size: var(--hip-avatar-size, 48px);
}

/* 侧栏与列表都属于「比评论区紧、比正文略小」的一档，同值即可：
   硬拆成 32 / 28 是假精确，多一个档位就多一处要维护的口径 */
:host([scene="sidebar"]),
:host([scene="list"]) {
  font-size: var(--hip-avatar-size, 32px);
}

:host([scene="profile"]) {
  font-size: var(--hip-avatar-size, 96px);
}

/* 另一条路：完全不看 scene，让头像跟着宿主正文字号走 —— 一条规则通吃所有调用方，
   评论区自动小、正文里自动大。想这么干就把上面 :host 的 font-size 与四条分档删掉，
   换成下面这行（2.5em ≈ 16px 正文下的 40px）：
:host {
  width: 2.5em;
  height: 2.5em;
}
*/

/* 必须 block：span 默认 inline 时 width/height 失效，
   绝对定位的 frame / img.avatar 包含块会塌成字体行高（框显示为头像的一半） */
.wrapper {
  display: block;
  position: relative;
  width: 100%;
  height: 100%;
}

/* 三层（首字母占位 / 头像 img / 头像框）一律绝对定位，共用 .wrapper 一个参照系。
   占位层不能走正常流：:host 的 font-size 即头像边长，行盒 strut 的 ascent 会压过
   占位层基线、把它下推约 0.22em，而 absolute 的 img 与 frame 不受影响 —— 结果是
   没有头像的用户看到首字母与框错开一截 */
.avatar {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  object-fit: cover;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

/* 首字母占位常驻兜底，头像 img 叠在其上；img 加载失败即隐藏，自然露出这一层。
   font-size 用 calc(1em / 2.2)：em 在 font-size 上取父级字号（即 :host 的边长），
   使占位字随头像尺寸同比例缩放。 */
.avatar--fallback {
  background: #e5e7eb;
  color: #6b7280;
  font-weight: 600;
  font-size: calc(1em / 2.2);
}

/* 背景色与占位同色，避免加载中或透明头像透出底下的字母 */
img.avatar {
  background: #e5e7eb;
}

/* 头像框覆盖层：124% 为实测最佳的框-头像视觉比例，
   前提是素材图案画满画布（上传提示已约定） */
.frame {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 124%;
  height: 124%;
  max-width: none;
  max-height: none;
  transform: translate(-50%, -50%);
  object-fit: contain;
  pointer-events: none;
}

:host([data-hip-dark]) .avatar--fallback {
  background: #30363d;
  color: #8b949e;
}

:host([data-hip-dark]) img.avatar {
  background: #30363d;
}`

export const AVATAR_TEMPLATE: TemplateSource = { html: HTML, css: CSS }
