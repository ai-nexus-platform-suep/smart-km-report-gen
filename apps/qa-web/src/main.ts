import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import 'element-plus/dist/index.css'

import '@platform/ui/src/styles'
import App from './App.vue'
import router from './router'

async function bootstrap() {
  if (import.meta.env.DEV && import.meta.env.VITE_ENABLE_MOCK !== 'false') {
    const { startWorker } = await import('@platform/mock')
    const modules = (import.meta.env.VITE_MOCK_MODULES || '')
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean)
    if (modules.length) await startWorker(modules)
  }

  const app = createApp(App)
  app.use(createPinia())
  app.use(router)
  app.use(ElementPlus, { locale: zhCn })
  app.mount('#app')
}

bootstrap()
