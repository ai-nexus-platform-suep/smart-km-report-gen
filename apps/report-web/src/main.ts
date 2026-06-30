import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import 'element-plus/dist/index.css'

import '@platform/ui/src/styles'
import './styles/tokens.css'
import './styles/base.css'
import './styles/motion.css'
import App from './App.vue'
import router from './router'

async function bootstrap() {
  if (import.meta.env.DEV && import.meta.env.VITE_ENABLE_MOCK === 'true') {
    try {
      const { startWorker } = await import('@platform/mock')
      await startWorker()
    } catch (error) {
      console.warn('[report-web] mock worker unavailable, continue without MSW.', error)
    }
  }

  const app = createApp(App)
  app.use(createPinia())
  app.use(router)
  app.use(ElementPlus, { locale: zhCn })
  app.mount('#app')
}

void bootstrap()
