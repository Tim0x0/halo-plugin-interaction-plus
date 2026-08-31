import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { rsbuildConfig } from '@halo-dev/ui-plugin-bundler-kit'
import Icons from 'unplugin-icons/rspack'
import { pluginSass } from '@rsbuild/plugin-sass'
import type { RsbuildConfig } from '@rsbuild/core'

const PLUGIN_VERSION = readFileSync(
  resolve(dirname(fileURLToPath(import.meta.url)), '../gradle.properties'),
  'utf8',
)
  .split(/\r?\n/)
  .map((line) => line.trim())
  .find((line) => line.startsWith('version='))
  ?.slice('version='.length)
  .trim()

if (!PLUGIN_VERSION) {
  throw new Error('ui 构建读不到 gradle.properties 的 version，无法生成 runtime 缓存版本参数')
}

// bundler-kit 根据 plugin.yaml 的 requires（>=2.25.0）使用 ui/ 资源布局：
// dev 输出 ../build/resources/main/ui，publicPath 为 /plugins/<name>/assets/ui/（含异步 chunk）。
export default rsbuildConfig({
  rsbuild: () => ({
    source: {
      define: {
        // Console 加载的 runtime.js 文件名固定，版本查询参数用于区分长期缓存。
        // 此常量只进入 Console / UC 产物；前台模板通过 Finder 获取 Runtime URL。
        'process.env.PLUGIN_VERSION': JSON.stringify(PLUGIN_VERSION),
      },
    },
    resolve: {
      alias: {
        '@': './src',
      },
    },
    plugins: [pluginSass()],
    tools: {
      rspack: {
        plugins: [Icons({ compiler: 'vue3' })],
      },
    },
  }),
}) as RsbuildConfig
