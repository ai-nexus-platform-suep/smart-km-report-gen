import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import ElementPlus from 'unplugin-element-plus/vite'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue(), ElementPlus()],
  resolve: {
    alias: [
      { find: '@', replacement: resolve(__dirname, 'src') },
      { find: '@platform/core/types', replacement: resolve(__dirname, '../../packages/core/src/types') },
      { find: '@platform/core/utils', replacement: resolve(__dirname, '../../packages/core/src/utils') },
      { find: '@platform/core/constants', replacement: resolve(__dirname, '../../packages/core/src/constants') },
      { find: '@platform/core', replacement: resolve(__dirname, '../../packages/core/src') },
      { find: '@platform/ui/src', replacement: resolve(__dirname, '../../packages/ui/src') },
      { find: '@platform/ui', replacement: resolve(__dirname, '../../packages/ui/src') },
      { find: '@platform/mock/src', replacement: resolve(__dirname, '../../packages/mock/src') },
      { find: '@platform/mock', replacement: resolve(__dirname, '../../packages/mock/src') },
    ],
  },
  server: {
    port: 5174,
  },
})
