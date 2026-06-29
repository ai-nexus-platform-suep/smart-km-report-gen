import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { baseRoutes, createAuthGuard } from '@platform/ui'
import AuthGuard from '@platform/ui/src/components/AuthGuard.vue'
import { h } from 'vue'

const moduleRoutes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/report',
  },
  {
    path: '/report',
    name: 'Report',
    component: () => import('../pages/ReportCreate.vue'),
    meta: { title: '新建报告' },
  },
  {
    path: '/report/history',
    name: 'History',
    component: () => import('../pages/ReportHistory.vue'),
    meta: { title: '历史记录' },
  },
  {
    path: '/admin',
    children: [
      {
        path: 'report/dashboard',
        component: guarded('AdminDashboard'),
        meta: { title: '报告统计', admin: true },
      },
      {
        path: 'report/template',
        component: guarded('TemplateMgr'),
        meta: { title: '模板管理', admin: true },
      },
      {
        path: 'report/material',
        component: guarded('MaterialMgr'),
        meta: { title: '素材管理', admin: true },
      },
      {
        path: 'report/llm',
        component: guarded('LlmConfig'),
        meta: { title: 'LLM配置', admin: true },
      },
      {
        path: 'report/docx',
        component: guarded('DocxConfig'),
        meta: { title: '样式配置', admin: true },
      },
    ],
  },
]

function guarded(name: string) {
  return () =>
    Promise.resolve({
      setup() {
        return () => h(AuthGuard, { requireAdmin: true }, () => Placeholder(name))
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
