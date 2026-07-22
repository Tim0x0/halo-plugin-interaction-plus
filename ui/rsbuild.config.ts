import { rsbuildConfig } from '@halo-dev/ui-plugin-bundler-kit';
import Icons from "unplugin-icons/rspack";
import { pluginSass } from "@rsbuild/plugin-sass";
import type { RsbuildConfig } from "@rsbuild/core";

export default rsbuildConfig({
  rsbuild: ({ envMode }) => ({
    resolve: {
      alias: {
        "@": "./src",
      },
    },
    plugins: [pluginSass()],
    tools: {
      rspack: {
        plugins: [Icons({ compiler: "vue3" })],
      },
    },
    // dev 产物输出到 resources/main/ui：kit 默认 dev 输出 console/，
    // 而 Halo 2.25 优先读取 ui/，跑过完整 gradle 构建后旧 ui/ 会遮蔽
    // console/ 下的 dev 改动导致“热更新不生效”（审查报告问题 6）。
    // 注意：若未来引入 async chunk，需同步覆盖 publicPath 为 /assets/ui/。
    output:
      envMode === 'production'
        ? {}
        : {
            distPath: {
              root: '../build/resources/main/ui',
            },
          },
  }),
}) as RsbuildConfig
