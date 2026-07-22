import { defineConfig } from 'vite'
import { resolve } from 'node:path'

// 站点前台 Runtime：构建为 IIFE 产物，供主题直接 <script> 引入。
// 产物：interaction-plus.runtime.js / interaction-plus.runtime.css
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
    cssCodeSplit: false,
    rollupOptions: {
      output: {
        assetFileNames: (assetInfo) => {
          if (assetInfo.names?.some((name) => name.endsWith('.css'))) {
            return 'interaction-plus.runtime.css'
          }
          return '[name][extname]'
        },
      },
    },
    target: 'es2020',
    outDir: 'build/dist',
    emptyOutDir: true,
  },
}))
