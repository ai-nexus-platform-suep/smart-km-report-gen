import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import 'element-plus/dist/index.css'

import '@platform/ui/src/styles'
import '../../report-web/src/styles/base.css'
import '../../report-web/src/styles/motion.css'
import './styles/skeleton.css'
import App from './App.vue'
import router from './router'

async function bootstrap() {
  if (import.meta.env.DEV) {
    try {
      const { startWorker } = await import('@platform/mock')
      await startWorker()
    } catch (error) {
      console.warn('[platform-web] mock worker unavailable, continue without MSW.', error)
    }
  }

  const app = createApp(App)
  app.use(createPinia())
  app.use(router)
  app.use(ElementPlus, { locale: zhCn })
  app.mount('#app')
}

void bootstrap()
