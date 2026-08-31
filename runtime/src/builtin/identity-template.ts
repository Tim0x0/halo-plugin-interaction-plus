// identity 组件的内置默认模板。
// 与主题 <template>、站长自定义模板走同一个渲染引擎，优先级最低。
// 后台「预填」直接掏出这两个常量。
import type { TemplateSource } from '../template-engine'

/**
 * HTML 框。
 *
 * <p>全程 `createElement` / `append`，**不碰 innerHTML** —— 昵称、标识名这些用户可控
 * 文本走的是 `textContent`（片段内部）与节点插入，天生没有转义问题。这也是给站长的
 * 示范写法：想改排布就调 append 的顺序，想换结构就整段重写。
 */
const HTML = `<span class="line"></span>
<script>
  var line = root.querySelector('.line')
  var name = hipHelper.renderName(hipData)

  // 只有「跳不跳转」需要判断：链接模板由站长在设置里配，留空即不跳
  if (hipData.profileUrl) {
    var link = document.createElement('a')
    link.className = 'plink'
    link.href = hipData.profileUrl
    link.append(name)
    line.append(link)
  } else {
    line.append(name)
  }

  // 称号：行内只用文字牌。称号图是给用户卡的——它普遍是画布里嵌着文字的横条插画，
  // 缩到行内这个高度（约 1.25em）图里的字只剩几像素，主流论坛的做法也是行内不放头衔图。
  // helper 返回的片段里图与文字牌是并排的（文字牌默认藏着做加载失败兜底），
  // 这里摘掉图、把文字牌显出来即可。想在行内也放图，删掉这一段就是。
  var title = hipHelper.renderTitle(hipData)
  var titleImage = title.querySelector('.title-img')
  if (titleImage) {
    titleImage.remove()
    var titleChip = title.querySelector('.title')
    if (titleChip) {
      titleChip.style.display = ''
    }
  }

  line.append(
    hipHelper.renderMarks(hipData),
    title,
    hipHelper.renderBadge(hipData)
  )
</script>`

/**
 * CSS 框。
 *
 * <p>全部字面值，不用 CSS 变量：变量既读不出来也改不动（Shadow DOM 里只有这一份
 * 样式在消费它们），只是多一层间接。暗色直接写 {@code :host([data-hip-dark])} 规则，
 * 该属性由 runtime 检测主题后打在宿主上（Shadow DOM 选不到外部 .dark，这是唯一通道）。
 */
const CSS = `*,
*::before,
*::after {
  box-sizing: border-box;
}

/* 宿主只管组件在正文里的行内定位；不设 font-family / font-size，
   继承宿主正文 —— 下面所有 em 尺寸因此自动跟着主题缩放 */
:host {
  display: inline-flex;
  align-items: center;
  vertical-align: middle;
}

/* 排布层：昵称 / 标识 / 称号 / 勋章横排 */
.line {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.name {
  font-weight: 500;
  color: inherit;
}

.mark {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 0.75em;
  font-weight: 400;
  line-height: 1;
  padding: 0 5px;
  color: #6b7280;
}

/* 行内图元（标识图标 / 主勋章 / 称号图上限）统一 1.25em 纯相对制：
   同排尺寸一致、随正文字号缩放；正文行高普遍 >= 1.4 > 1.25，
   故任何字号下都不撑高文字行——不撑行的真约束是相对的，勿加绝对封顶 */
.mark-icon {
  width: 1.25em;
  height: 1.25em;
  object-fit: contain;
  vertical-align: middle;
}

/* 文字牌（标识回落牌 / 称号牌）与上面的图元等高。
   牌自身 font-size 是 0.75em，1.667em = 1.25 ÷ 0.75，折回外层正好是同一个 1.25em。
   描边算进高度里（box-sizing 全局 border-box），所以带描边的标识牌与无描边的
   称号牌一样高。⚠ 改牌的 font-size 就要跟着改这个系数——靠上下 padding 凑高度
   仅依赖 padding 会让称号牌、标识牌和图标在同一行出现高度差。 */
.mark,
.title {
  height: 1.667em;
}

.title {
  display: inline-flex;
  align-items: center;
  border-radius: 4px;
  font-size: 0.75em;
  font-weight: 400;
  line-height: 1;
  padding: 0 6px;
}

/* 称号图：默认模板在行内不渲染它（见 HTML 框里摘图的那段），这条规则是留给
   「改回行内也放图」的站长用的——上限 1.25em（16px 正文下即 20px，随正文缩放不撑行），
   宽度随素材比例自适应；限宽只作保险丝（200px 约等于 20px 高 x 10:1） */
.title-img {
  max-height: 1.25em;
  max-width: 200px;
  height: auto;
  object-fit: contain;
  vertical-align: middle;
}

.badge {
  width: 1.25em;
  height: 1.25em;
  object-fit: contain;
}

/* 昵称跳转链接：颜色交给内部 .name（nameStyle 不被链接色覆盖），
   无下划线；真实 <a> 保住中键 / Ctrl 新开 */
.plink {
  color: inherit;
  text-decoration: none;
}

/* 场景分档示例（默认不启用，取消注释即生效）。
   scene 的值由调用方声明，插件不预定义也不校验；推荐词表见前台适配指南。

   ⚠ 默认**不**在评论区隐藏称号，是产品决定不是遗漏：评论区往往是称号唯一的曝光场景，
   默认藏掉等于让称号这类装扮失去展示面（主流社区反而都把头衔摆在评论里）。
   行内已经为拥挤做过一轮让步——上面的 JS 把称号图摘掉、只留文字牌。
   确实嫌挤的站长再取消下面两条：
:host([scene="comment"]) .title {
  display: none;
}
:host([scene="comment"]) .line {
  gap: 2px;
}
*/

/* 暗色：只翻中性铬件，语义色（昵称样式 / 标识色 / 称号色）走数据通道不在此翻 */
:host([data-hip-dark]) .mark {
  border-color: #30363d;
  color: #8b949e;
}`

export const IDENTITY_TEMPLATE: TemplateSource = { html: HTML, css: CSS }
