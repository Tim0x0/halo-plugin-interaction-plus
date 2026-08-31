import { defineConfig } from 'vite'
import { resolve } from 'node:path'

// 站点前台 Runtime：构建为 IIFE 产物，供主题直接 <script> 引入。
// 产物只有 interaction-plus.runtime.js。组件样式全部在各自模板的 Shadow DOM 里；
// --hip-avatar-size 由内置头像模板读取，无需额外样式表。
// 生产构建默认不输出 sourcemap；开发构建输出 sourcemap 便于调试。
export default defineConfig(({ mode }) => ({
  build: {
    lib: {
      entry: resolve(__dirname, 'src/index.ts'),
      name: 'InteractionPlusRuntime',
      formats: ['iife'],
      fileName: () => 'interaction-plus.runtime.js',
    },
    sourcemap: mode === 'development',
    target: 'es2020',
    outDir: 'build/dist',
    emptyOutDir: true,
  },
}))
