<script lang="ts" setup>
// Console - 自定义组件页：站长贴 HTML + CSS 完全接管 hip-* 组件的渲染
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { Toast, VButton, VCard, VPageHeader, VSpace, VTabbar } from '@halo-dev/components'
import { customTemplateApi } from '@/api'
import { currentUserDetail } from '@/utils/preview-identity'
import { loadRuntime, type HipRuntime } from '@/utils/runtime-loader'
import ListSkeleton from '@/components/ListSkeleton.vue'
import ListError from '@/components/ListError.vue'
import type { CustomTemplate, PublicIdentity, TemplateComponent } from '@/types'

/** 三个固定槽位，与后端 CustomTemplate 的三条记录一一对应。 */
const COMPONENTS: TemplateComponent[] = ['identity', 'avatar', 'card']

// 类型标成 Record<string,string>[] 以对上 VTabbar 的 items 签名
const TABS: Record<string, string>[] = [
  { id: 'identity', label: '身份行' },
  { id: 'avatar', label: '头像' },
  { id: 'card', label: '用户卡' },
]

/**
 * scene 建议值。插件不预定义也不校验 scene，它只是模板 CSS 的挂点
 * （`:host([scene="comment"])`）；推这套词表是为了避免各家造同义词。
 *
 * <p>用可输入的 datalist 而不是下拉：运行时既然不限定取值，后台就不能把站长锁死在
 * 这六个词里 —— 主题自造 `scene="hero"` 也得能在这儿预览。
 */
const SCENE_SUGGESTIONS = ['comment', 'post', 'moment', 'sidebar', 'profile', 'list']

/**
 * 各组件对 `scene` 的实际支持，逐字告诉站长「填了会不会有变化」。
 *
 * <p>「切了没反应」的观感有一半出在这里：内置模板只有头像消费 scene，身份行的规则是
 * 注释示例、用户卡压根没有。不写明的话，站长第一反应是功能坏了，而不是「这个组件没做」。
 */
const SCENE_HINTS: Record<TemplateComponent, string> = {
  avatar:
    '内置模板对 comment / post / moment / sidebar / list / profile 六个词都有尺寸分档，填别的词走默认 40px。想理睬调用方的覆盖，写成 var(--hip-avatar-size, 该档回落)；写死字面量即锁死。',
  identity:
    '内置模板没有消费 scene（CSS 框末尾留了注释掉的示例），填了不会有变化 —— 除非你自己写 :host([scene="…"]) 规则。',
  card: '内置模板没有消费 scene：卡是浮层，形态只随视口宽度变化、与从哪儿点开无关。填了不会有变化，除非你自己写规则。',
}

const activeTab = ref<TemplateComponent>('identity')

// 文案里要出现字面量的双花括号；直接写在模板里内层 }} 会提前闭合插值，只能走常量
const PLACEHOLDER_LITERAL = '{{displayName}}'
const PLACEHOLDER_URL = '{{profileUrl}}'

/** 说明区展示的占位符清单：覆盖每个顶层字段，站长照着改路径即可。 */
const PLACEHOLDER_EXAMPLES = [
  '{{displayName}}',
  '{{userName}}',
  '{{avatar}}',
  '{{bio}}',
  '{{profileUrl}}',
  '{{registeredAt}}',
  '{{stats.posts}}',
  '{{decorations.title.titleText}}',
  '{{identityMarks.0.displayName}}',
  '{{attrs.scene}}',
]

const loading = ref(true)
const error = ref(false)
const saving = ref(false)
const templates = ref<CustomTemplate[]>([])

// 编辑草稿（按组件分开存，切 Tab 不丢未保存内容）
const drafts = ref<Record<string, { enabled: boolean; html: string; css: string }>>({})

// 断言非空：drafts 由 COMPONENTS 建槽，三个 key 恒存在（见 load）。
// 不写 `|| { ... }` 兜底——那个临时对象会被 v-model 绑上，编辑写进去就没了，
// 保存也读不到，表现是「输入框能打字但保存无效」，极难查
const draft = computed(() => drafts.value[activeTab.value]!)

// ── 加载与预填 ────────────────────────────────────────

const runtime = shallowRef<HipRuntime | null>(null)
const runtimeError = ref('')

async function load() {
  loading.value = true
  error.value = false
  try {
    // 预填要用 runtime 导出的内置模板文本，两件事一起等
    const [list, loaded] = await Promise.all([customTemplateApi.list(), loadRuntimeSafely()])
    templates.value = list
    const byComponent = new Map(list.map((template) => [template.spec.component, template.spec]))
    // 按固定槽位建而不是按返回列表：后端记录被删且插件未重启时列表会缺条，
    // 那时按列表建就会缺 key
    const next: typeof drafts.value = {}
    for (const component of COMPONENTS) {
      const spec = byComponent.get(component)
      const builtin = loaded?.BUILTIN_TEMPLATES?.[component]
      // 预填：内容为空时填入内置默认模板文本，把「从零写一份」降级为「改一行」
      next[component] = {
        enabled: spec?.enabled === true,
        html: spec?.html || builtin?.html || '',
        css: spec?.css || builtin?.css || '',
      }
    }
    drafts.value = next
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}

async function loadRuntimeSafely() {
  try {
    runtime.value = await loadRuntime()
    runtimeError.value = ''
    return runtime.value
  } catch (e) {
    // 预览与预填不可用，但编辑与保存不该被拖下水
    runtimeError.value = e instanceof Error ? e.message : String(e)
    return null
  }
}

async function handleSave() {
  const component = activeTab.value
  const current = drafts.value[component]
  if (!current) return
  if (current.enabled && !current.html.trim()) {
    Toast.warning('启用后 HTML 不能为空')
    return
  }
  saving.value = true
  try {
    await customTemplateApi.save(component, {
      enabled: current.enabled,
      html: current.html,
      css: current.css,
    })
    Toast.success('保存成功，刷新前台生效')
    // 不调 load()：那会用服务端三条记录重建全部 drafts，
    // 其它 Tab 未保存的草稿会被冲掉。当前草稿就是刚写出的内容，不必回填。
  } catch {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    saving.value = false
  }
}

/** 用内置默认模板覆盖当前编辑内容。 */
function resetToBuiltin() {
  const builtin = runtime.value?.BUILTIN_TEMPLATES?.[activeTab.value]
  const current = drafts.value[activeTab.value]
  if (!builtin || !current) {
    Toast.warning('内置模板尚未加载')
    return
  }
  current.html = builtin.html
  current.css = builtin.css
  Toast.success('已恢复内置默认（未保存）')
}

// ── 预览 ──────────────────────────────────────────────

const previewRef = ref<HTMLElement | null>(null)
const previewError = ref('')
const previewDark = ref(false)
const previewNarrow = ref(false)
const previewScene = ref('')

const sceneHint = computed(() => SCENE_HINTS[activeTab.value])

// 窄屏只有用户卡真的换形态；另外两个组件的内置模板没写 @media，勾上只是预览视口变窄
const narrowHint = computed(() =>
  activeTab.value === 'card'
    ? '勾上把预览视口收到 375px，用户卡切到移动端全宽形态（贴顶、遮罩、内部滚动）。'
    : '内置模板没有窄屏规则，勾上只把预览视口收到 375px，组件本身不变。',
)
const previewUserName = ref('')
const sampleData = shallowRef<PublicIdentity | null>(null)
const sampleLoading = ref(false)
const sampleMissing = ref(false)

/** 窄屏预览宽度：常见手机宽度，稳稳落在用户卡 ≤640 的窄屏形态里。 */
const NARROW_WIDTH = 375

/**
 * 样本数据取**真实用户**，默认是当前登录的自己 —— 假数据永远只是「看起来像」，
 * 调模板时看到的必须是这个站点真实的装扮、标识与统计。
 * 代价是没佩戴装扮的账号预览会很空，面板下方有提示引导先去配。
 */
let sampleToken = 0

async function loadSample() {
  const token = ++sampleToken
  const userName = previewUserName.value.trim()
  if (!userName || !runtime.value) {
    sampleData.value = null
    sampleMissing.value = false
    return
  }
  sampleLoading.value = true
  try {
    const identity = await runtime.value.fetchIdentity(userName)
    if (token !== sampleToken) {
      return
    }
    sampleData.value = identity || null
    // 查不到不弹 Toast（边打字边触发会刷屏），改成面板内一行状态
    sampleMissing.value = !identity
  } finally {
    if (token === sampleToken) {
      sampleLoading.value = false
    }
  }
}

/** 取当前登录用户名作为默认样本；拿不到就留空，站长自己填一个用户名。 */
async function loadCurrentUser() {
  const user = await currentUserDetail()
  if (user) {
    previewUserName.value = user.metadata.name
  }
}

// FormKit 的 @blur 不保证透传到原生输入，改用防抖 watch——
// 触发时机不依赖控件实现，边打字边出结果也更顺手。
// runtime 也必须在依赖里：取样本要用它，而它和「取当前登录用户名」是并行发起的，
// 谁先到不确定；只盯用户名的话，runtime 慢于 400ms 时这次取样会静默作废、
// 且再也不会重试，表现是预览一直卡在骨架态
let sampleTimer: ReturnType<typeof setTimeout> | undefined
watch([previewUserName, runtime], () => {
  clearTimeout(sampleTimer)
  sampleTimer = setTimeout(loadSample, 400)
})

function renderPreview() {
  const container = previewRef.value
  const current = drafts.value[activeTab.value]
  if (!container || !runtime.value || !current) {
    return
  }
  const attrs: Record<string, string> = { 'user-name': previewUserName.value.trim() || 'demo' }
  if (previewScene.value) {
    attrs.scene = previewScene.value
  }
  // 走 runtime 的 renderPreview：内部建 div + attachShadow 喂给同一个渲染引擎，
  // 绕过「从接口取已保存模板」和「自定义元素只能注册一次」两个障碍
  runtime.value
    .renderPreview(container, {
      component: activeTab.value,
      html: current.html,
      css: current.css,
      data: sampleData.value,
      attrs,
      dark: previewDark.value,
      width: previewNarrow.value ? NARROW_WIDTH : undefined,
    })
    .then(() => {
      previewError.value = ''
    })
    .catch((e) => {
      previewError.value = e instanceof Error ? e.message : String(e)
    })
}

// 编辑即时反映到预览（草稿，未保存）。
// previewUserName 必须在依赖里：它同时决定打到宿主上的 user-name 属性，
// 而查不到用户时 sampleData 不变，光盯 sampleData 会一次都不重渲染。
// loading 同理：首屏加载完成前预览容器还没渲染，那时 drafts 变化触发的这次 watch
// 只会拿到 null ref 白跑一趟——不盯着 loading 翻转就永远不会补渲染
watch(
  [
    draft,
    previewDark,
    previewNarrow,
    previewScene,
    previewUserName,
    sampleData,
    runtime,
    activeTab,
    loading,
  ],
  () => renderPreview(),
  { deep: true, flush: 'post' },
)

onMounted(() => {
  load()
  loadCurrentUser()
})

// 打完字立刻切走的话定时器还会打一次无用请求；用户卡模板往 document 上挂的
// 外点 / Esc 监听也要摘掉，否则离开页面后还留在 Console 里
onBeforeUnmount(() => {
  clearTimeout(sampleTimer)
  if (previewRef.value) {
    runtime.value?.disposePreview(previewRef.value)
  }
})
</script>

<template>
  <VPageHeader title="自定义组件">
    <template #icon>
      <span class="hip-page-icon">✦</span>
    </template>
  </VPageHeader>

  <div class="hip-page">
    <VTabbar v-model:active-id="activeTab" :items="TABS" type="outline" />

    <ListSkeleton v-if="loading" variant="list" />
    <ListError v-else-if="error" title="自定义模板加载失败" @retry="load" />
    <template v-else>
      <div class="hip-notice">
        <p>
          启用后<strong>完全接管</strong>该组件的 Shadow
          渲染，主题内嵌模板与插件内置样式都不再生效；
          关闭开关即回到内置默认。保存后刷新前台生效。主题写在宿主上的继承属性（如
          <code>line-height</code>）仍会进来，浮层要在模板里自己锁（内置用户卡锁在
          <code>.card</code>，不要锁 <code>:host</code>）。
        </p>
        <p class="hip-notice__warn">
          ⚠ <code>&lt;script&gt;</code> 在每个组件实例每次渲染时各执行一次，能访问
          <code>document</code> / <code>window</code>，权限等价于「自定义 head 代码」。 用
          <code>append</code> / <code>textContent</code> 写 DOM 是安全的；自己拼
          <code>innerHTML</code> 时，昵称、简介等用户可控内容必须先过
          <code>hipHelper.escape()</code>。
        </p>
        <details class="hip-notice__more">
          <summary>写法与可用占位符</summary>
          <p>
            取单个值用占位符 <code>{{ PLACEHOLDER_LITERAL }}</code
            >（自动转义，可写在属性里， 如
            <code>href="{{ PLACEHOLDER_URL }}"</code>）；循环、条件、带装扮样式的片段写在
            <code>&lt;script&gt;</code> 里，用 <code>hipHelper.*</code> 与 <code>hipData</code>。
            挂到 <code>document</code> / <code>window</code> 的监听用
            <code>onCleanup()</code> 登记。
          </p>
          <p>
            CSS 框是 Shadow DOM 里<strong>唯一</strong>的样式来源，组件代码里没有藏第二套。
            <code>hipHelper.render*</code> 的节点在代码里生成，HTML 框里看不到；用 helper
            就必须按内置类名写 CSS（<code>.avatar</code> / <code>.avatar--fallback</code> /
            <code>.frame</code> / <code>.name</code> / <code>.mark</code> /
            <code>.mark-icon</code> / <code>.title</code> / <code>.title-img</code> /
            <code>.badge</code>），不用 helper 则类名随便。保存后自定义模板独立于内置模板；
            需要内置内容时请执行「恢复内置默认」并保存。
          </p>
          <p>
            顶层名拼错报错，深层缺失输出空串：
            <code v-for="item in PLACEHOLDER_EXAMPLES" :key="item" class="hip-notice__ph">{{
              item
            }}</code>
          </p>
        </details>
      </div>

      <div v-if="runtimeError" class="hip-notice hip-notice--danger">
        runtime 加载失败（{{ runtimeError }}），预览与「恢复内置默认」不可用，编辑保存不受影响。
      </div>

      <div class="hip-editor">
        <VCard title="模板" :body-class="['hip-editor__body']">
          <FormKit type="form" :actions="false" @submit="handleSave">
            <FormKit v-model="draft.enabled" type="switch" label="启用自定义模板" />
            <FormKit
              v-model="draft.html"
              type="code"
              language="html"
              height="360px"
              label="HTML"
              help="JS 写在 <script> 里，可用 hipData / hipHelper / root / host / onCleanup"
            />
            <FormKit
              v-model="draft.css"
              type="code"
              language="css"
              height="360px"
              label="CSS"
              help='这是组件唯一的样式来源，自动包 <style> 放进 Shadow DOM。暗色写 :host([data-hip-dark])，场景分档写 :host([scene="comment"])。头像尺寸要理睬调用方就写 var(--hip-avatar-size, 回落)，写死字面量即锁死。装扮色是内联的，要盖需 !important。helper 片段用内置类名（.mark / .title / .avatar 等），改字重写在这里即可'
            />
          </FormKit>
          <template #footer>
            <VSpace>
              <VButton :loading="saving" type="secondary" @click="handleSave">保存</VButton>
              <VButton @click="resetToBuiltin">恢复内置默认</VButton>
            </VSpace>
          </template>
        </VCard>

        <VCard title="实时预览" :body-class="['hip-editor__body']">
          <div class="hip-toolbar">
            <!-- ⚠ sync 必须加：SearchInput 默认只在**回车**时才 emit update:modelValue
                 （见 Halo 的 ui/src/components/input/SearchInput.vue，onInput 里判 props.sync）。
                 列表页那种「输入关键词回车搜索」正合适，但这里是实时预览，不加就表现为
                 「打了字预览毫无反应」——两个框都是 -->
            <div class="hip-toolbar__field">
              <span class="hip-toolbar__label">样本用户</span>
              <SearchInput v-model="previewUserName" sync placeholder="默认你自己" />
            </div>
            <!-- 建议值走 datalist 而不是下拉：可选可填，不把站长锁死在推荐词表里 -->
            <div class="hip-toolbar__field">
              <span class="hip-toolbar__label">场景</span>
              <SearchInput
                v-model="previewScene"
                sync
                placeholder="留空即不带"
                list="hip-scene-suggestions"
              />
            </div>
            <div class="hip-preview-toggle" role="group" aria-label="预览形态">
              <button
                type="button"
                class="hip-preview-toggle__btn"
                :class="{ 'hip-preview-toggle__btn--active': previewDark }"
                @click="previewDark = !previewDark"
              >
                暗色
              </button>
              <button
                type="button"
                class="hip-preview-toggle__btn"
                :class="{ 'hip-preview-toggle__btn--active': previewNarrow }"
                @click="previewNarrow = !previewNarrow"
              >
                窄屏
              </button>
            </div>
          </div>
          <datalist id="hip-scene-suggestions">
            <option v-for="scene in SCENE_SUGGESTIONS" :key="scene" :value="scene" />
          </datalist>
          <div class="hip-preview" :class="{ 'hip-preview--dark': previewDark }">
            <!-- 内部完全交给 runtime（它在里面建 iframe），Vue 不碰这层的子节点 -->
            <div ref="previewRef"></div>
          </div>
          <p v-if="previewError" class="hip-preview__note hip-preview__note--warn">
            预览不可用：{{ previewError }}
          </p>
          <p v-if="sampleLoading" class="hip-preview__note">加载样本数据…</p>
          <p v-if="sampleMissing" class="hip-preview__note hip-preview__note--warn">
            找不到用户「{{ previewUserName }}」，预览的是骨架态。
          </p>
          <p class="hip-preview__note">
            与前台同一个渲染引擎，改动即时反映、无需保存。样本是真实账号数据，
            预览很空说明这个账号没佩戴装扮。
            <template v-if="activeTab === 'card'">用户卡自动展开，点预览区内空白即收起。</template>
          </p>
          <p class="hip-preview__note">场景：{{ sceneHint }}</p>
          <p class="hip-preview__note">窄屏：{{ narrowHint }}</p>
        </VCard>
      </div>
    </template>
  </div>
</template>

<style scoped>
.hip-page {
  margin: 16px;
  display: flex;
  flex-direction: column;
  gap: var(--hip-gap-lg);
}
@media (max-width: 640px) {
  .hip-page {
    margin: 0;
  }
}
.hip-page-icon {
  font-size: 18px;
  color: var(--hip-text-secondary);
}
/* 说明区：项目通用的浅底块（对齐各页 .hip-toolbar 的灰底语汇） */
.hip-notice {
  background: var(--hip-bg-subtle);
  border-radius: var(--hip-radius-card);
  padding: 12px 16px;
  font-size: var(--hip-font-caption);
  color: var(--hip-text-secondary);
  line-height: 1.7;
}
.hip-notice p + p {
  margin-top: 8px;
}
.hip-notice--danger,
.hip-notice__warn {
  color: var(--hip-danger);
}
/* 语法参考默认收起：危险提示与「完全接管」常驻，剩下的按需展开
   （对齐主流代码编辑后台：警告常驻 + 参考手册折叠或外链） */
.hip-notice__more {
  margin-top: 8px;
}
.hip-notice__more > summary {
  cursor: pointer;
  color: var(--hip-text-muted);
  user-select: none;
}
.hip-notice__more > summary:hover {
  color: var(--hip-text-secondary);
}
.hip-notice__more > p {
  margin-top: 8px;
}
.hip-notice code {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.95em;
  background: rgba(0, 0, 0, 0.05);
  border-radius: 3px;
  padding: 0 4px;
}
/* 占位符清单一行要挤十来个，做成 chip 靠间距分隔而不是标点 */
.hip-notice__ph {
  display: inline-block;
  margin: 2px 6px 2px 0;
  padding: 1px 6px;
  background: var(--hip-bg-card);
  border: 1px solid var(--hip-border);
  border-radius: var(--hip-radius-chip);
  color: var(--hip-text-secondary);
}
/* 左右两栏：编辑器可伸缩，预览栏保底 660px。
   660 不是随手取的 —— 用户卡的窄屏判据是 max-width:640，预览 iframe 窄于它就会翻成
   移动端形态；再加卡身 560 与两侧内边距，660 是「能站住桌面形态」的下限。
   放不下这个下限时（窄屏）转单列，预览排在编辑器下方 */
.hip-editor {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(660px, 1fr);
  gap: var(--hip-gap-lg);
  align-items: start;
}
@media (max-width: 1280px) {
  .hip-editor {
    grid-template-columns: minmax(0, 1fr);
  }
}
/* 工具栏：灰底条沿用各页语汇，但排布是等距一行而非左右分推（见模板处注释）。
   底对齐让带标签的字段与不带标签的开关组落在同一条基线上 */
.hip-toolbar {
  display: flex;
  align-items: flex-end;
  gap: var(--hip-gap-lg);
  width: 100%;
  background: var(--hip-bg-subtle);
  padding: 12px 16px;
  border-radius: var(--hip-radius-chip);
  flex-wrap: wrap;
}
.hip-toolbar__field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.hip-toolbar__label {
  font-size: var(--hip-font-caption);
  color: var(--hip-text-muted);
  line-height: 1.2;
}
/* 形态开关：与资产编辑弹窗的分段按钮同一视觉，区别是两个按钮各自独立开关 */
.hip-preview-toggle {
  display: flex;
  gap: 4px;
}
.hip-preview-toggle__btn {
  border: 1px solid var(--hip-border);
  background: var(--hip-bg-card);
  cursor: pointer;
  padding: 3px 10px;
  border-radius: var(--hip-radius-chip);
  font-size: var(--hip-font-caption);
  color: var(--hip-text-muted);
  transition: all var(--hip-transition);
}
.hip-preview-toggle__btn--active {
  background: var(--hip-bg-subtle);
  color: var(--hip-text-primary);
  font-weight: 600;
}
/* 里面是 runtime 建的 iframe：高度按组件档位、留白与背景都在 iframe 内，
   这层只画边框。窄屏档 iframe 变窄居中，两侧露出的底色跟着明暗走 */
.hip-preview {
  margin-top: var(--hip-gap-md);
  min-height: 120px;
  border: 1px solid var(--hip-border);
  border-radius: var(--hip-radius-card);
  background: var(--hip-bg-card);
  box-shadow: var(--hip-shadow-card);
  overflow: hidden;
}
.hip-preview--dark {
  background: #0d1117;
  border-color: #30363d;
}
.hip-preview__note {
  margin-top: var(--hip-gap-sm);
  font-size: var(--hip-font-caption);
  color: var(--hip-text-muted);
  line-height: 1.7;
}
.hip-preview__note--warn {
  color: var(--hip-danger);
}
</style>
