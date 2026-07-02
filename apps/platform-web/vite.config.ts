import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import ElementPlus from 'unplugin-element-plus/vite'
import { resolve } from 'path'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const gatewayTarget = env.VITE_API_PROXY_TARGET || env.VITE_GATEWAY_BASE_URL || env.VITE_API_BASE || env.VITE_API_BASE_URL || 'http://localhost:8080'

  return {
    plugins: [vue(), ElementPlus()],
    resolve: {
      alias: [
        { find: '@', replacement: resolve(__dirname, '../report-web/src') },
        { find: '@report', replacement: resolve(__dirname, '../report-web/src') },
        { find: '@km', replacement: resolve(__dirname, '../km-web/src') },
        { find: '@qa', replacement: resolve(__dirname, '../qa-web/src') },
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
      port: 5176,
      proxy: {
        '/api': {
          target: gatewayTarget,
          changeOrigin: true,
        },
      },
    },
  }
})
