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
      alias: { '@': resolve(__dirname, 'src') },
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
