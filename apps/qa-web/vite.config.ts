import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import ElementPlus from 'unplugin-element-plus/vite'
import { resolve } from 'path'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const qaAgentTarget = env.VITE_QA_AGENT_TARGET || 'http://localhost:8000'
  const qaServiceTarget = env.VITE_QA_SERVICE_TARGET || 'http://localhost:8082'

  return {
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
      port: 5173,
      proxy: {
        '/api/conversations': {
          target: qaAgentTarget,
          changeOrigin: true,
        },
        '/api/chat': {
          target: qaAgentTarget,
          changeOrigin: true,
        },
        '/api/model-configs': {
          target: qaServiceTarget,
          changeOrigin: true,
        },
        '/api/stats/qa': {
          target: qaServiceTarget,
          changeOrigin: true,
        },
      },
    },
  }
})
