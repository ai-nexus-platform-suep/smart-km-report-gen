import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { baseRoutes, createAuthGuard } from '@platform/ui'
import AuthGuard from '@platform/ui/src/components/AuthGuard.vue'
import { defineAsyncComponent, defineComponent, h, type Component } from 'vue'

// QaHome 页面 — 各组自行替换为真正页面
const QaHome = () => import('../pages/ChatView.vue')
const QaDashboard = defineAsyncComponent(() => import('../pages/admin/QaDashboard.vue'))
const QaConfig = defineAsyncComponent(() => import('../pages/admin/QaConfig.vue'))
const RetrievalTest = defineAsyncComponent(() => import('../pages/admin/RetrievalTest.vue'))
const LlmConfig = defineAsyncComponent(() => import('../pages/admin/LlmConfig.vue'))

const moduleRoutes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/chat',
  },
  {
    path: '/chat',
    name: 'Chat',
    component: QaHome,
    meta: { title: '智能问答' },
  },
  {
    path: '/conversations',
    name: 'Conversations',
    component: () => import('../pages/Conversations.vue'),
    meta: { title: '会话列表' },
  },
  // 管理后台 — AuthGuard 包裹
  {
    path: '/admin',
    children: [
      {
        path: 'qa/dashboard',
        component: withAdminGuard(QaDashboard),
        meta: { title: '问答统计', admin: true },
      },
      {
        path: 'qa/config',
        component: withAdminGuard(QaConfig),
        meta: { title: '问答配置', admin: true },
      },
      {
        path: 'qa/retrieval-test',
        component: withAdminGuard(RetrievalTest),
        meta: { title: '检索测试', admin: true },
      },
      {
        path: 'qa/llm',
        component: withAdminGuard(LlmConfig),
        meta: { title: 'LLM配置', admin: true },
      },
    ],
  },
]

function withAdminGuard(component: Component) {
  return defineComponent({
    setup() {
      return () => h(AuthGuard, { requireAdmin: true }, { default: () => h(component) })
    },
  })
}

const router = createRouter({
  history: createWebHistory(),
  routes: [...baseRoutes, ...moduleRoutes],
})

createAuthGuard(router)

export default router
