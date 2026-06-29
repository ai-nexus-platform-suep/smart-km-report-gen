import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { baseRoutes, createAuthGuard } from '@platform/ui'
import AuthGuard from '@platform/ui/src/components/AuthGuard.vue'
import { h } from 'vue'

// QaHome 页面 — 各组自行替换为真正页面
const QaHome = () => import('../pages/ChatView.vue')

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
        component: () => renderGuarded('AdminDashboard'),
        meta: { title: '问答统计', admin: true },
      },
      {
        path: 'qa/config',
        component: () => renderGuarded('AdminConfig'),
        meta: { title: '问答配置', admin: true },
      },
      {
        path: 'qa/retrieval-test',
        component: () => renderGuarded('RetrievalTest'),
        meta: { title: '检索测试', admin: true },
      },
      {
        path: 'qa/llm',
        component: () => renderGuarded('LlmConfig'),
        meta: { title: 'LLM配置', admin: true },
      },
    ],
  },
]

function renderGuarded(name: string) {
  return () =>
    Promise.resolve({
      setup() {
        return () => h(AuthGuard, { requireAdmin: true }, () => h(Placeholder(name)))
      },
    })
}

function Placeholder(name: string) {
  return h('div', { style: 'padding:40px;text-align:center;color:#999' }, [
    h('h2', '🚧 ' + name),
    h('p', '此页面待开发'),
  ])
}

const router = createRouter({
  history: createWebHistory(),
  routes: [...baseRoutes, ...moduleRoutes],
})

createAuthGuard(router)

export default router
