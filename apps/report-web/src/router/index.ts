import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { baseRoutes, createAuthGuard } from '@platform/ui'
import AuthGuard from '@platform/ui/src/components/AuthGuard.vue'
import { h } from 'vue'

const moduleRoutes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/reports',
  },
  {
    path: '/reports',
    name: 'ReportList',
    component: () => import('../pages/reports/ReportListPage.vue'),
    meta: { title: '报告记录' },
  },
  {
    path: '/reports/new',
    name: 'ReportCreate',
    component: () => import('../pages/reports/NewReportPage.vue'),
    meta: { title: '新建报告' },
  },
  {
    path: '/reports/:id/outline',
    name: 'ReportOutline',
    component: () => import('../pages/reports/OutlinePage.vue'),
    meta: { title: '大纲编辑' },
  },
  {
    path: '/reports/:id/workspace',
    name: 'ReportWorkspace',
    component: () => import('../pages/reports/WorkspacePage.vue'),
    meta: { title: '正文工作台' },
  },
  {
    path: '/reports/:id/export',
    name: 'ReportExport',
    component: () => import('../pages/reports/ExportPage.vue'),
    meta: { title: '报告导出' },
  },
  {
    path: '/admin/dashboard',
    name: 'AdminDashboard',
    component: guarded('趋势监控', '后续任务 C-19 迁入总览指标、趋势图和异常数据'),
    meta: { title: '趋势监控', admin: true },
  },
  {
    path: '/admin/templates',
    name: 'TemplateAdmin',
    component: guarded('模板管理', '后续任务 C-20 迁入模板上传、配置和删除能力'),
    meta: { title: '模板管理', admin: true },
  },
  {
    path: '/admin/materials',
    name: 'MaterialAdmin',
    component: guarded('素材管理', '后续任务 C-21 迁入素材上传、解析状态和删除能力'),
    meta: { title: '素材管理', admin: true },
  },
  {
    path: '/admin/llm-configs',
    name: 'LlmConfig',
    component: guarded('模型配置', '后续任务 C-22 迁入模型新增、保存和连通性测试'),
    meta: { title: '模型配置', admin: true },
  },
]

function guarded(title: string, description: string) {
  return () =>
    Promise.resolve({
      setup() {
        return () => h(AuthGuard, { requireAdmin: true }, () => Placeholder(title, description))
      },
    })
}

function Placeholder(title: string, description: string) {
  return h('section', { class: 'page' }, [
    h('div', { class: 'page-title' }, [
      h('div', [
        h('span', { class: 'eyebrow' }, 'REPORT ADMIN'),
        h('h1', title),
        h('p', description),
      ]),
    ]),
    h('div', { class: 'surface', style: 'padding: 28px;' }, [
      h('div', { class: 'terminal-label', style: 'color: var(--accent-blue);' }, 'ADMIN PLACEHOLDER'),
      h('p', { style: 'margin: 12px 0 0; color: var(--text-secondary);' }, '当前只迁移用户侧报告功能，管理侧页面将在后续任务单独接入。'),
    ]),
  ])
}

const router = createRouter({
  history: createWebHistory(),
  routes: [...baseRoutes, ...moduleRoutes],
})

createAuthGuard(router)

export default router