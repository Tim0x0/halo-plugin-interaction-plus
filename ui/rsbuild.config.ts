import { rsbuildConfig } from '@halo-dev/ui-plugin-bundler-kit';
import Icons from "unplugin-icons/rspack";
import { pluginSass } from "@rsbuild/plugin-sass";
import type { RsbuildConfig } from "@rsbuild/core";

// dev 输出目录无需覆盖：bundler-kit ≥2.25 按 plugin.yaml 的 requires（>=2.25.0）
// 自动选定 ui/ 作为 bundle 位置，dev 默认输出 ../build/resources/main/ui、
// publicPath 默认 /plugins/<name>/assets/ui/（含 async chunk），与此前手工覆盖完全一致。
// ⚠ 若未来把 requires 降到 2.25 以下，kit 会退回旧的 console/ 目录，需要恢复覆盖。
export default rsbuildConfig({
  rsbuild: () => ({
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
  }),
}) as RsbuildConfig
