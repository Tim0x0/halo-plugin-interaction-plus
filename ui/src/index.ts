import { definePlugin } from '@halo-dev/ui-shared'
import { defineAsyncComponent, markRaw } from 'vue'
import RiSparkling2Line from '~icons/ri/sparkling-2-line'
import './styles/tokens.css'
import ConsoleEntryPage from './views/console/ConsoleEntryPage.vue'
import AssetsPage from './views/console/AssetsPage.vue'
import GrantsPage from './views/console/GrantsPage.vue'
import MetadataPage from './views/console/MetadataPage.vue'
import IdentityMarksPage from './views/console/IdentityMarksPage.vue'
import UcEntryPage from './views/uc/UcEntryPage.vue'
import MyDecorationPage from './views/uc/MyDecorationPage.vue'
import SubmissionsPage from './views/uc/SubmissionsPage.vue'

export default definePlugin({
  components: {},
  routes: [
    // Console：「互动」分组 / 装饰 主菜单 + 二级子菜单
    // 子路由逐项挂 permissions，由 Halo 菜单生成器按权限自动隐藏；
    // 父路由不写死 redirect，由 ConsoleEntryPage 跳转到首个有权子页
    {
      parentName: 'Root',
      route: {
        path: '/interaction-plus/decorations',
        name: 'InteractionPlusDecorations',
        component: ConsoleEntryPage,
        meta: {
          title: '装饰',
          // ANY 语义：拥有任一子页权限即可见父菜单
          permissions: [
            'plugin:interaction-plus:decoration:manage',
            'plugin:interaction-plus:decoration:grant',
            'plugin:interaction-plus:system:manage',
          ],
          menu: {
            name: '装饰',
            group: '互动',
            icon: markRaw(RiSparkling2Line),
            priority: 0,
          },
        },
        children: [
          {
            path: 'assets',
            name: 'InteractionPlusAssets',
            component: AssetsPage,
            meta: {
              title: '装饰资产',
              searchable: true,
              permissions: ['plugin:interaction-plus:decoration:manage'],
              menu: { name: '资产', priority: 0 },
            },
          },
          {
            path: 'grants',
            name: 'InteractionPlusGrants',
            component: GrantsPage,
            meta: {
              title: '装饰授予',
              searchable: true,
              permissions: ['plugin:interaction-plus:decoration:grant'],
              menu: { name: '授予', priority: 1 },
            },
          },
          {
            path: 'metadata',
            name: 'InteractionPlusMetadata',
            component: MetadataPage,
            meta: {
              title: '装饰元数据',
              searchable: true,
              permissions: ['plugin:interaction-plus:decoration:manage'],
              menu: { name: '元数据', priority: 2 },
            },
          },
          {
            path: 'identity-marks',
            name: 'InteractionPlusIdentityMarks',
            component: IdentityMarksPage,
            meta: {
              title: '身份标识',
              searchable: true,
              permissions: ['plugin:interaction-plus:system:manage'],
              menu: { name: '身份标识', priority: 3 },
            },
          },
        ],
      },
    },
  ],
  ucRoutes: [
    // UC：「互动」分组 / 装扮 主菜单 + 二级子菜单
    {
      parentName: 'Root',
      route: {
        path: '/interaction-plus/decorations',
        name: 'InteractionPlusUcDecorations',
        component: UcEntryPage,
        meta: {
          title: '装扮',
          permissions: [
            'plugin:interaction-plus:decoration:equip',
            'plugin:interaction-plus:decoration:submit',
          ],
          menu: {
            name: '装扮',
            group: '互动',
            icon: markRaw(RiSparkling2Line),
            priority: 0,
          },
        },
        children: [
          {
            path: 'my',
            name: 'InteractionPlusUcMyDecoration',
            component: MyDecorationPage,
            meta: {
              title: '我的装扮',
              permissions: ['plugin:interaction-plus:decoration:equip'],
              menu: { name: '我的装扮', priority: 0 },
            },
          },
          {
            path: 'submissions',
            name: 'InteractionPlusUcSubmissions',
            component: SubmissionsPage,
            meta: {
              title: '我的投稿',
              permissions: ['plugin:interaction-plus:decoration:submit'],
              menu: { name: '我的投稿', priority: 1 },
            },
          },
        ],
      },
    },
  ],
  extensionPoints: {
    // 往 Halo /uc/profile 个人资料页注入「装扮」tab（公开装扮墙开关，隐私设置归资料中枢）
    'uc:user:profile:tabs:create': () => [
      {
        id: 'interaction-plus-decoration',
        label: '装扮',
        component: markRaw(
          defineAsyncComponent(() => import('./views/uc/DecorationProfileTab.vue')),
        ),
        priority: 60,
      },
    ],
  },
})
