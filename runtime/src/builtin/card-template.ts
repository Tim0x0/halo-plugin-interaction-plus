// card 组件的内置默认模板
//
// 结构、样式、全部交互都在这里 —— 站长可以整段改写。交互用原生 DOM API 表达，
// 挂到 document / window 上的监听一律走 onCleanup 登记（一次组件生命周期至少渲染
// 两次：骨架 + 数据到达，不摘就是稳定泄漏）。
import type { TemplateSource } from '../template-engine'

/**
 * HTML 框。
 *
 * <p>静态骨架写在 HTML 里、动态内容由 JS 填 —— 这样站长改版式（挪块、删块）
 * 只动 HTML，改内容逻辑才动 JS。空块由 JS 判断后整块移除（称号 / 数据行 / 展柜行）。
 *
 * <p>`<slot>` 是触发元素：主题在标签里放什么（头像、昵称、一段文字）就点什么出卡；
 * 没放就回落成用户昵称。
 *
 * <p>卡与遮罩上的 `popover="manual"` 别删：它让两者在展开时进入浏览器的 **top layer**，
 * 从而不受主题层叠上下文与 `overflow:hidden` 影响。
 * 取 `manual` 而非 `auto`，是因为关闭逻辑（外点 / Esc / 遮罩 / 窄屏滚动）本模板自己全实现了，
 * 交给浏览器会两套抢着关同一张卡、把 `visible` 状态搞不同步；`auto` 还会与页面上其他
 * auto popover 互斥，把主题自己的下拉菜单顶掉。
 */
const HTML = `<span class="trigger" tabindex="0"><slot>{{displayName}}</slot></span>
<div class="cloak" popover="manual"></div>
<div class="card" role="tooltip" popover="manual">
  <div class="card-bg"></div>
  <div class="inner">
    <div class="surface">
      <span class="avatar-wrap"></span>
      <div class="head">
        <div class="name-row"></div>
        <div class="title-row"></div>
      </div>
      <div class="bio"></div>
      <div class="dstats"></div>
      <div class="shelf-row">
        <div class="shelf"></div>
        <span class="joined"></span>
      </div>
    </div>
  </div>
</div>
<script>
  var card = root.querySelector('.card')
  var cloak = root.querySelector('.cloak')
  var trigger = root.querySelector('.trigger')

  // 数据未到 / 请求失败：只留触发器，不挂卡，也不挂 document 监听。
  // 骨架对象通过 loaded 表示数据是否可用。
  if (!hipData.loaded) {
    if (card) card.remove()
    if (cloak) cloak.remove()
    if (trigger) {
      trigger.removeAttribute('tabindex')
      trigger.style.cursor = 'default'
    }
    return
  }

  /* ── 内容 ────────────────────────────────────── */

  var background = hipData.decorations.cardBackground
  if (background && background.url) {
    root.querySelector('.card-bg').style.backgroundImage =
      'url("' + hipHelper.escapeCssUrl(background.url) + '")'
  }

  // 卡内跳转（主流两段式导航：列表处点开卡，卡内点头像 / 名字进用户页）
  var avatarWrap = root.querySelector('.avatar-wrap')
  if (hipData.profileUrl) {
    var avatarLink = document.createElement('a')
    avatarLink.className = 'avatar-wrap plink'
    avatarLink.href = hipData.profileUrl
    avatarWrap.replaceWith(avatarLink)
    avatarWrap = avatarLink
  }
  avatarWrap.append(hipHelper.renderAvatar(hipData))

  var nameRow = root.querySelector('.name-row')
  var name = hipHelper.renderName(hipData)
  if (hipData.profileUrl) {
    var nameLink = document.createElement('a')
    nameLink.className = 'plink'
    nameLink.href = hipData.profileUrl
    nameLink.append(name)
    nameRow.append(nameLink)
  } else {
    nameRow.append(name)
  }
  nameRow.append(hipHelper.renderMarks(hipData), hipHelper.renderBadge(hipData))

  var titleRow = root.querySelector('.title-row')
  titleRow.append(hipHelper.renderTitle(hipData))
  if (!titleRow.firstChild) {
    titleRow.remove()
  }

  // 个人说明固定两行：空值也占位渲染，卡高与说明内容解耦
  var bio = root.querySelector('.bio')
  if (hipData.bio) {
    bio.textContent = hipData.bio
  } else {
    bio.classList.add('bio--empty')
    bio.textContent = 'TA 还没有留下个人说明'
  }

  function formatCount(value) {
    if (typeof value !== 'number' || !isFinite(value) || value < 0) {
      return '0'
    }
    if (value < 10000) {
      return value.toLocaleString('en-US')
    }
    var w = value / 10000
    return (w >= 100 ? Math.round(w) : w.toFixed(1).replace(/\\.0$/, '')) + '万'
  }

  var stats = hipData.stats
  var dstats = root.querySelector('.dstats')
  if (stats) {
    var items = [
      { label: '文章', value: formatCount(stats.posts) },
      { label: '评论', value: formatCount(stats.comments) }
    ]
    if (stats.decorations) {
      items.push({ label: '勋章', value: formatCount(stats.decorations.badge) })
    }
    var extras = stats.extras || []
    extras.forEach(function (extra) {
      if (extra && extra.label && extra.value) {
        items.push({ label: extra.label, value: extra.value })
      }
    })
    items.forEach(function (item) {
      var cell = document.createElement('span')
      cell.className = 'di'
      var strong = document.createElement('b')
      strong.textContent = item.value
      // append 收字符串会建文本节点，用户可控内容天然不解析 HTML
      cell.append(strong, item.label)
      dstats.append(cell)
    })
  }
  if (!dstats.firstChild) {
    dstats.remove()
  }

  var shelf = root.querySelector('.shelf')
  var showcase = (hipData.decorations.badgeShowcase || []).filter(function (badge) {
    return badge && badge.url
  })
  showcase.forEach(function (badge) {
    var slot = document.createElement('span')
    slot.className = 'slot'
    slot.title = badge.displayName || ''
    var image = document.createElement('img')
    image.alt = badge.displayName || ''
    image.loading = 'lazy'
    image.addEventListener('error', function () {
      image.style.display = 'none'
    })
    image.src = badge.url
    slot.append(image)
    shelf.append(slot)
  })

  // 「+N」以展柜非空为门槛：上限设 0（整柜关闭）或无佩戴时不渲染孤立计数格
  var badgeTotal = stats && stats.decorations ? stats.decorations.badge : null
  if (showcase.length && typeof badgeTotal === 'number' && badgeTotal > showcase.length) {
    var more = document.createElement('span')
    more.className = 'slot slot--more'
    more.title = '共 ' + badgeTotal + ' 枚'
    more.textContent = '+' + (badgeTotal - showcase.length)
    shelf.append(more)
  }

  var joined = root.querySelector('.joined')
  var joinedAt = hipData.registeredAt ? new Date(hipData.registeredAt) : null
  if (joinedAt && !isNaN(joinedAt.getTime())) {
    joined.textContent = joinedAt.getFullYear() + ' 年 ' + (joinedAt.getMonth() + 1) + ' 月加入'
  } else {
    joined.remove()
  }
  // ⚠ 末行不做「空了就删」，也不按展柜是否启用分档：它是卡的底边，一删整张卡就矮一截。
  // 无佩戴、整柜关闭、无加入时间，这一行都照常占一格高（.shelf 的 min-height 撑着）

  /* ── 交互 ────────────────────────────────────── */

  var visible = false
  var hideTimer = null
  var placeTicking = false

  // 同页各实例之间的「我要展开了」广播。名字带组件前缀避免与主题的事件撞车；
  // 站长若整段重写模板而没保留这套，他那张卡就不参与互斥 —— 模板归站长，这是预期的
  var PEER_OPEN = 'hip-user-card:open'

  // top layer 能力检测。不支持的浏览器走完全相同的这套逻辑，只是卡留在原地的层叠
  // 上下文里、靠 fixed + z-index 排队 —— 主题若给容器建了层叠上下文仍可能被盖住，
  // 这是不支持 top layer 时的已知限制
  var canPopover = typeof card.showPopover === 'function'

  function isNarrow() {
    return window.matchMedia('(max-width: 640px)').matches
  }

  // 进 / 出 top layer。三道闸：能力、popover 属性（站长可能在自定义模板里把它删了）、
  // 以及当前状态（已在目标状态时重复调用会抛 InvalidStateError）。
  // 外面还要包 try —— showPopover 的失败路径不止一条（元素未连接、属性被改写、
  // 浏览器实现差异……），而它一抛就会中断 toggle()，表现是「卡整个点不开」。
  // 宁可退回原地层级也不能卡死
  function enterLayer(element) {
    if (!canPopover || !element.hasAttribute('popover') || element.matches(':popover-open')) {
      return
    }
    try {
      element.showPopover()
    } catch (error) {
      // 进不了 top layer 就留在原地层级，交互与外观都不受影响
    }
  }

  function leaveLayer(element) {
    if (!canPopover || !element.hasAttribute('popover') || !element.matches(':popover-open')) {
      return
    }
    try {
      element.hidePopover()
    } catch (error) {
      // 同上：退不出去也不能让关闭流程中断
    }
  }

  // 桌面端定位：期望贴在触发元素左下角，放不下时向视口内平移（Floating UI shift 的
  // 钳制语义：留在视口内优先于贴住锚点），下方装不下而上方装得下就翻上去（flip）。
  // ⚠ 卡是 fixed（top layer 里的包含块是视口），这里写的是**视口坐标**。
  // 窄屏走 CSS 固定全宽模式，这里只清掉桌面定位留下的内联值。
  function place() {
    if (isNarrow()) {
      card.style.left = ''
      card.style.top = ''
      return
    }
    var rect = host.getBoundingClientRect()
    var padding = 12
    var width = card.offsetWidth || 560
    var height = card.offsetHeight || 298
    card.style.left = Math.min(
      Math.max(rect.left, padding),
      Math.max(padding, window.innerWidth - width - padding)
    ) + 'px'
    // flip 在 fixed 之下是必需项而非锦上添花：absolute 时卡伸出视口还能滚动看到，
    // fixed 钉在视口外就是彻底看不见
    var top = rect.bottom + 8
    if (top + height + padding > window.innerHeight && rect.top - height - 8 >= padding) {
      top = rect.top - height - 8
    }
    // 上下都放不下（卡高过视口）时钳进视口：宁可盖住触发元素，也不能整块跑出去
    card.style.top = Math.min(
      Math.max(top, padding),
      Math.max(padding, window.innerHeight - height - padding)
    ) + 'px'
  }

  // 视口变化：桌面重算位置跟随，窄屏维持「滚动即关」（移动端主流行为）。
  // ⚠ 桌面必须跟随触发元素重算 fixed 坐标，否则滚动后卡片会停在旧的视口位置。
  // capture 是为了收到主题自己的滚动容器：scroll 不冒泡，只有捕获阶段能在 window 上拿到
  function onViewportChange() {
    if (!visible) {
      return
    }
    if (isNarrow()) {
      toggle(false)
      return
    }
    if (placeTicking) {
      return
    }
    placeTicking = true
    window.requestAnimationFrame(function () {
      placeTicking = false
      if (visible && !isNarrow()) {
        place()
      }
    })
  }

  function toggle(next) {
    visible = next
    window.clearTimeout(hideTimer)
    if (visible) {
      // 同页只留一张卡：先让别的实例收起，自己再展开。
      // ⚠ 互斥放在「展开」这个动作里，不是靠监听点击 —— 原生 popover 的 auto / hint
      // 就是这个语义（showPopover 自己会关掉别的），于是鼠标、键盘（Enter / 空格）、
      // 程序化展开三条路径一起覆盖。我们用的 manual 规定就是「允许多个同时显示」，
      // 这一条不补，键盘打开就还是能开出第二张
      document.dispatchEvent(new CustomEvent(PEER_OPEN, { detail: host }))
      // 顺序有讲究：遮罩先进层、卡后进（后进者在上，卡才压得住遮罩）；
      // 都得在 place() 之前 —— 没展开的 popover 是 display:none，量不到宽高
      if (isNarrow()) {
        enterLayer(cloak)
      }
      enterLayer(card)
      place()
      // 下一帧再上类：display 从 none 到可见是离散切换，同帧加类过渡根本不会跑
      window.requestAnimationFrame(function () {
        if (visible) {
          card.classList.add('card--visible')
          cloak.classList.add('cloak--visible')
        }
      })
      window.addEventListener('scroll', onViewportChange, { passive: true, capture: true })
      window.addEventListener('resize', onViewportChange)
    } else {
      card.classList.remove('card--visible')
      cloak.classList.remove('cloak--visible')
      // 等淡出跑完再退层：一退层就是 display:none，过渡当场消失
      hideTimer = window.setTimeout(function () {
        if (!visible) {
          leaveLayer(card)
          leaveLayer(cloak)
        }
      }, 200)
      window.removeEventListener('scroll', onViewportChange, { capture: true })
      window.removeEventListener('resize', onViewportChange)
    }
  }

  function onDocumentClick(event) {
    if (visible && !event.composedPath().includes(host)) {
      toggle(false)
    }
  }

  function onKeydown(event) {
    if (event.key === 'Escape' && visible) {
      toggle(false)
    }
  }

  // 别的卡展开了：自己让位。detail 是那张卡的宿主，用它认出「广播是我自己发的」
  function onPeerOpen(event) {
    if (visible && event.detail !== host) {
      toggle(false)
    }
  }

  // 全平台点击切换（再点触发元素即关闭）；触发器是 span，补键盘等效保住可达性。
  // stopPropagation 只挡冒泡：主题常把整行做成可点区域（点评论跳详情），点昵称出卡
  // 不该顺带触发它。这不影响别的卡收到关闭信号 —— 那条走 document 的**捕获**阶段，
  // 早在这行代码执行之前就跑完了（见下面的 addEventListener）
  trigger.addEventListener('click', function (event) {
    event.stopPropagation()
    toggle(!visible)
  })
  trigger.addEventListener('keydown', function (event) {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      toggle(!visible)
    }
  })
  // 遮罩在 shadow 内，document 级外点关闭（composedPath 含宿主）拦不到，单独接
  cloak.addEventListener('click', function () {
    toggle(false)
  })
  // ⚠ capture 阶段，别改回冒泡：同页每张卡都在 document 上挂了这个外点关闭，而触发器
  // 自己会 stopPropagation。冒泡阶段收的话，点第二个人时事件根本到不了 document，
  // 第一张卡就永远收不到关闭信号（表现是「卡越点越多，一张都不消失」）。
  // 捕获早于 target，谁 stopPropagation 都拦不住——主题给列表项挂的那些同理
  document.addEventListener('click', onDocumentClick, true)
  document.addEventListener('keydown', onKeydown)
  document.addEventListener(PEER_OPEN, onPeerOpen)

  // 挂在 document / window 上的监听必须登记，引擎会在下次渲染前与组件卸载时摘掉
  onCleanup(function () {
    document.removeEventListener('click', onDocumentClick, true)
    document.removeEventListener('keydown', onKeydown)
    document.removeEventListener(PEER_OPEN, onPeerOpen)
    window.removeEventListener('scroll', onViewportChange, { capture: true })
    window.removeEventListener('resize', onViewportChange)
    window.clearTimeout(hideTimer)
    // top layer 是**文档级**状态，不随 Shadow DOM 内的重渲染自动收回：
    // 一次组件生命周期至少渲染两次（骨架 + 数据到达），卡展开着重渲染就会留下
    // 一张脱离了新 DOM、再也关不掉的卡
    leaveLayer(card)
    leaveLayer(cloak)
  })
</script>`

/**
 * CSS 框。宽 560；.surface min-height 238；说明两行。
 * 典型高约 298（露出带 60 + 内容层下限），可随内容长高。
 */
const CSS = `*,
*::before,
*::after {
  box-sizing: border-box;
}

/* ⚠ 刻意不设 position：.card 使用 fixed + top layer，宿主不是它的包含块。
   设 relative 会让每个触发元素都成为定位元素，可能遮挡其他用户卡。
   字体栈 / 字重 / 字形自包含（不同于行内组件的继承宿主）：卡是独立面板，
   借正文字体会在花哨主题下失控；触发器常是被主题加粗或斜体的昵称，
   卡内元素在 Shadow DOM 里会继承 :host，不锁死整张卡会跟着变形。
   行高不在这里锁：页面打在宿主上的规则（如 .author-capsule hip-user-card）
   比 :host 更具体，锁了也打不赢，子树仍继承 0；即便打赢，也会把主题
   压胶囊行盒的意图抵消。卡面行高写在 .card 上。 */
:host {
  display: inline-block;
  font-family: ui-sans-serif, system-ui, -apple-system, sans-serif;
  font-weight: 400;
  font-style: normal;
}

.trigger {
  cursor: pointer;
  display: inline-flex;
}

/* 卡片容器：fixed + 清 popover UA；z-index 只给无 popover 回落。坐标由 place() 写。
   行高锁 1.4：继承属性会从宿主穿进 Shadow（选择器隔离 ≠ 继承隔离）。
   主题给 <hip-user-card> 写 line-height:0 压胶囊时，数据行自己没锁行高
   就会塌成 ~1px，margin-top:auto 把剩余灌进说明和数字之间。
   锁在浮层、不锁 :host：触发器仍归主题排列表。1.4 对齐通行证模板 /
   系统 UI 面板惯例；.name / .bio / .mark 已有自己的行高，不受这行影响。
   --hip-avatar-size 走自定义属性通道，读的是 slot 里头像，与此无关。 */
.card {
  position: fixed;
  inset: auto;
  top: 0;
  left: 0;
  z-index: 9999;
  margin: 0;
  border: 0;
  padding: 0;
  color: inherit;
  line-height: 1.4;
  width: 560px;
  height: auto;
  max-width: calc(100vw - 24px);
  border-radius: 12px;
  background: #f0f1f3;
  box-shadow: 0 8px 28px rgba(31, 35, 40, 0.16);
  overflow: hidden;
  opacity: 0;
  visibility: hidden;
  transform: translateY(4px);
  transition: opacity 0.15s ease, transform 0.15s ease, visibility 0.15s;
}

.card--visible {
  opacity: 1;
  visibility: visible;
  transform: translateY(0);
}

/* 窄屏遮罩：仅移动端全宽形态有视觉（媒体查询内定义），桌面恒不可见。
   它同样走 top layer（popover="manual"）—— 否则卡上去了、遮罩还留在原地的层叠
   上下文里，窄屏下就成了「卡浮在最顶、遮罩却被主题内容盖住」的半吊子状态 */
.cloak {
  display: none;
}

/* 背景层：装扮素材全彩 cover，水平垂直居中（整卡铺图的主流锚定）。
   卡长高时 cover 等比放大、多裁四周，不会在底部留空 */
.card-bg {
  position: absolute;
  inset: 0;
  background-image: linear-gradient(160deg, #e3e7ec, #dde3ea 55%, #e7e4e0);
  background-size: cover;
  background-position: center;
}

/* 顶部背景露出带：背景唯一完整可见区 */
.inner {
  position: relative;
  padding-top: 60px;
}

/* 内容层：min-height 238 保证短内容下的卡片高度，勿加 overflow（头像骑缝会被裁）。
   底 10 < 顶 14：底边有横线兜着，不需要与顶部等量留白；这 10 与 .shelf-row 的
   padding-top 同值，让展柜行在横线与卡底之间上下对称。
   color 是卡内正文色：称号没选字色时继承这里，不是再给称号铺一层灰。 */
.surface {
  position: relative;
  min-height: 238px;
  background: rgba(255, 255, 255, 0.85);
  border-radius: 12px 12px 0 0;
  padding: 14px 26px 10px;
  display: flex;
  flex-direction: column;
  gap: 9px;
  color: #24292f;
}

/* 头像 96：骑内容层上缘，上半浸在背景露出带里（用户定稿值，勿擅调） */
.avatar-wrap {
  position: absolute;
  left: 26px;
  top: -48px;
  width: 96px;
  height: 96px;
}

/* 三层（首字母占位 / 头像 img / 头像框）一律绝对定位，共用 .avatar-wrap 一个参照系：
   占位层走正常流会被行盒基线推离 absolute 的两层（见 avatar-template.ts 同处注释）。
   img 是替换元素：inset: 0 只定偏移，宽高仍走固有尺寸，必须显式 100% 才装进参照盒。 */
.avatar {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
  box-shadow: 0 3px 10px rgba(11, 18, 32, 0.3);
}

.avatar--fallback {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #e5e7eb;
  color: #6b7280;
  font-weight: 600;
  font-size: 34px;
}

img.avatar {
  background: #e5e7eb;
}

/* 头像框覆盖层：124% 为实测最佳的框-头像视觉比例 */
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

/* 名字块：让位头像（头像宽 96 + 16 间距）。
   min-height 44 是给骑缝头像在 surface 内的那半截留垂直余量
   （头像下沿约在 surface 内容区 34px 处），不是给称号垫的假行高 */
.head {
  margin-left: 112px;
  min-height: 44px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.name {
  font-size: 20px;
  font-weight: 600;
  color: #24292f;
  line-height: 1.25;
}

/* 卡片场景主勋章 20（行内场景 1.25em，场景分化默认） */
.badge {
  width: 20px;
  height: 20px;
  object-fit: contain;
}

/* 卡上元数据（标识牌 / 称号牌 / 加入时间 / +N）统一 12px：
   对齐主流卡片 meta 字号，且不留亚像素级伪层级 */
.mark {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1;
  padding: 2.5px 6px;
  color: #57606a;
}

/* 卡片场景标识图标与主勋章同 20：同排图元尺寸统一（2px 级差读作不一致
   而非层级，印记语义由紧贴昵称的位置表达） */
.mark-icon {
  width: 20px;
  height: 20px;
  object-fit: contain;
  vertical-align: middle;
}

/* 称号行：有内容才出现。高度跟内容走，勿加 min-height。 */
.title-row {
  display: flex;
  align-items: center;
}

.title {
  display: inline-flex;
  align-items: center;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1;
}

/* 有底色才是「牌」：补内边距（.title--chip 由 card-parts 按背景有无打上）。
   裸文字称号无此类、无内边距——称号独行，空内边距会把文字读成整行缩进。 */
.title--chip {
  padding: 4px 10px;
}

/* 称号图：卡片内限高 48。 */
.title-img {
  max-height: 48px;
  max-width: 100%;
  height: auto;
  object-fit: contain;
  vertical-align: middle;
}

/* 个人说明区固定两行截断（13.5 × 1.75 × 2）。 */
.bio {
  height: 3.5em;
  font-size: 13.5px;
  color: #57606a;
  line-height: 1.75;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

.bio--empty {
  color: #8b949e;
}

/* 数据行：行内串 wrap，数字等宽；与展柜行成元信息组沉底。 */
.dstats {
  margin-top: auto;
  display: flex;
  flex-wrap: wrap;
  gap: 6px 22px;
}

.di {
  font-size: 12px;
  color: #8b949e;
}

.di b {
  font-size: 15px;
  color: #24292f;
  font-weight: 600;
  margin-right: 4px;
  font-variant-numeric: tabular-nums;
}

/* 勋章展柜行：34px 方形收藏格，hover 底色加深（无浮动动效）。
   ⚠ 这一行是卡的底边，恒存在、恒占一格高（见 .shelf），不随内容有无增删。
   数据行缺席时靠 margin-top:auto 接棒沉底；数据行在场时由下面那条把 auto 撤掉，
   块间距统一由 .surface 的 gap 9 控制。
   padding-top 10 = 横线到勋章格的距离：父级 gap 到不了边框内侧。
   与 .surface 的 padding-bottom 同值，勋章格在横线与卡底之间上下对称。
   两者要改一起改，单改一边这一行就会偏心。不要改成 gap。 */
.shelf-row {
  margin-top: auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px solid rgba(31, 35, 40, 0.1);
  padding-top: 10px;
}

/* 数据行在场：必须把 .shelf-row 的 auto 撤掉（两个 auto 会平分剩余空间、
   把元信息组整个浮起来）。写成 0 而不是再加一截：块间距只认 gap。
   两行数字时，行间视觉 ≈13、下缝视觉 ≈13.5，分组会弱一档——四项统计多数一行，
   为少见换行把常见底缝撑到 15 不值。 */
.dstats ~ .shelf-row {
  margin-top: 0;
}

/* 恒占一格高（= .slot 的 34），不分佩戴与否、不分整柜启用与否：
   这一行是卡的底边，高度一跳整张卡就跳。勿改成按内容走 */
.shelf {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 34px;
}

.slot {
  width: 34px;
  height: 34px;
  border-radius: 4px;
  background: rgba(31, 35, 40, 0.045);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: background 0.12s ease;
}

.slot:hover {
  background: rgba(31, 35, 40, 0.1);
}

.slot img {
  width: 24px;
  height: 24px;
  object-fit: contain;
}

.slot--more {
  font-size: 12px;
  font-weight: 600;
  color: #8b949e;
  background: none;
  border: 1px dashed rgba(31, 35, 40, 0.25);
}

.joined {
  font-size: 12px;
  color: #8b949e;
}

/* 卡内跳转链接（头像 / 名字）：颜色交给内部元素（nameStyle 不被链接色覆盖），
   无下划线——可点性由用户卡语境与指针表达，真实 <a> 保住中键 / Ctrl 新开 */
.plink {
  color: inherit;
  text-decoration: none;
}

/* 暗色：卡片文字三档 + 中性铬件随暗色翻转（黑底深字不可读）；
   语义色（稀有度 / 标识色 / 昵称色）走数据通道，不在此翻转 */
:host([data-hip-dark]) .card {
  background: #161b22;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.3);
}

:host([data-hip-dark]) .card-bg {
  background-image: linear-gradient(160deg, #1c2128, #22272e, #1a1f26);
}

:host([data-hip-dark]) .surface {
  background: rgba(15, 15, 17, 0.9);
  color: #e6edf3;
}

:host([data-hip-dark]) .avatar--fallback,
:host([data-hip-dark]) img.avatar {
  background: #30363d;
}

:host([data-hip-dark]) .avatar--fallback {
  color: #8b949e;
}

:host([data-hip-dark]) .name,
:host([data-hip-dark]) .di b {
  color: #e6edf3;
}

:host([data-hip-dark]) .mark {
  border-color: #30363d;
  color: #8b949e;
}

:host([data-hip-dark]) .bio {
  color: #8b949e;
}

:host([data-hip-dark]) .bio--empty,
:host([data-hip-dark]) .di,
:host([data-hip-dark]) .joined,
:host([data-hip-dark]) .slot--more {
  color: #7d8590;
}

:host([data-hip-dark]) .shelf-row {
  border-top-color: rgba(233, 237, 242, 0.12);
}

:host([data-hip-dark]) .slot {
  background: rgba(233, 237, 242, 0.07);
}

:host([data-hip-dark]) .slot:hover {
  background: rgba(233, 237, 242, 0.14);
}

:host([data-hip-dark]) .slot--more {
  background: none;
  border-color: rgba(233, 237, 242, 0.22);
}

/* 窄屏（≤640px）：弃锚定，切固定全宽卡（移动端主流用户卡形态）——
   贴顶、放开定高、内部滚动、半透明遮罩。必须放在样式表末尾（同特异性靠源顺序）。 */
@media (max-width: 640px) {
  /* !important 压过 place() 写入的内联坐标：跨断点 resize 时先来的是 resize 事件、
     内联值还没被清掉，中间那一帧不能让卡歪在桌面坐标上 */
  .card {
    position: fixed;
    left: 12px !important;
    right: 12px;
    top: 12px !important;
    width: auto;
    max-width: none;
    max-height: 85vh;
    overflow-y: auto;
  }
  /* 底部单独钉回 14：桌面的 10 是与 .shelf-row padding-top 配平出来的（勋章格上下对称），
     窄屏 min-height 已放开、不参与那笔配平；全宽形态左右只有 16，且 85vh 滚到底时
     贴边感更强，收口要更厚一点 */
  .surface {
    min-height: 0;
    padding-left: 16px;
    padding-right: 16px;
    padding-bottom: 14px;
  }
  /* 内容降档（Discourse 式断点按元素降规格，非整卡缩放）：
     桌面规格在窄卡上占比失衡（96 头像占 366 宽卡 26%，桌面仅 17%），
     头像 96→72、留白与字号随之收拢；头像框 124% 相对制自动跟缩 */
  .avatar-wrap {
    left: 16px;
    top: -36px;
    width: 72px;
    height: 72px;
  }
  .avatar--fallback {
    font-size: 26px;
  }
  .head {
    margin-left: 88px;
  }
  .name {
    font-size: 18px;
  }
  /* 说明区放宽：窄屏行容量比桌面少约四成，钳制提至 4 行对齐桌面两行的内容量
     （2 ÷ 0.6 ≈ 3.3，向上取整保证不少于桌面）；
     本形态卡高已浮动、85vh 滚动兜底，定高随之放开，短说明自然收缩 */
  .bio {
    height: auto;
    -webkit-line-clamp: 4;
  }
  /* 与 .card 同理清掉 popover 的 UA 样式。
     ⚠ width / height 这两条不能漏：UA 给的是 fit-content，而定位元素一旦 width 不是
     auto，就轮不到 left/right 决定尺寸（right 被忽略）—— 空 div 会缩成 0，遮罩整个不可见 */
  .cloak--visible {
    display: block;
    position: fixed;
    inset: 0;
    width: auto;
    height: auto;
    margin: 0;
    border: 0;
    padding: 0;
    z-index: 9998;
    background: rgba(0, 0, 0, 0.5);
  }
}`

export const CARD_TEMPLATE: TemplateSource = { html: HTML, css: CSS }
