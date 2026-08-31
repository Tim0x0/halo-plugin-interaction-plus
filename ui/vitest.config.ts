import { fileURLToPath, URL } from 'node:url'
import { viteConfig } from '@halo-dev/ui-plugin-bundler-kit'
import { defineConfig as defineVitestConfig } from 'vitest/config'

// 复用 Halo bundler kit 的 Vite 预设，以获得与插件产物一致的 Vue SFC 编译链；
// Rsbuild 仍是生产构建器，这份配置只服务 Vitest。
const testConfig = defineVitestConfig({
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
  },
})

export default viteConfig({
  // defineVitestConfig 的扩展字段会原样交给 Vitest；基础部分仍满足 Vite UserConfig。
  vite: testConfig,
})
