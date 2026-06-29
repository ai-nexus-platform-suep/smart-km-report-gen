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
    component: () => import('../pages/ReportHistory.vue'),
    meta: { title: '报告记录' },
  },
  {
    path: '/reports/new',
    name: 'ReportCreate',
    component: () => import('../pages/ReportCreate.vue'),
    meta: { title: '新建报告' },
  },
  {
    path: '/reports/:id/outline',
    name: 'ReportOutline',
    component: placeholder('大纲编辑', '后续任务 C-14 迁入完整大纲生成与编辑页面'),
    meta: { title: '大纲编辑' },
  },
  {
    path: '/reports/:id/workspace',
    name: 'ReportWorkspace',
    component: placeholder('正文工作台', '后续任务 C-15 迁入正文流式生成与章节编辑页面'),
    meta: { title: '正文工作台' },
  },
  {
    path: '/reports/:id/export',
    name: 'ReportExport',
    component: placeholder('报告导出', '后续任务 C-18 迁入 DOCX 导出与下载页面'),
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

function placeholder(title: string, description: string) {
  return () =>
    Promise.resolve({
      setup() {
        return () => Placeholder(title, description)
      },
    })
}

function Placeholder(title: string, description: string) {
  return h('section', { class: 'page' }, [
    h('div', { class: 'page-title' }, [
      h('div', [
        h('span', { class: 'eyebrow' }, 'REPORT MODULE'),
        h('h1', title),
        h('p', description),
      ]),
    ]),
    h('div', { class: 'surface', style: 'padding: 28px;' }, [
      h('div', { class: 'terminal-label', style: 'color: var(--accent-blue);' }, 'MIGRATION PLACEHOLDER'),
      h('p', { style: 'margin: 12px 0 0; color: var(--text-secondary);' }, '当前阶段完成入口、路由、布局、样式和基础组件迁移，业务页面将在后续任务逐步接入。'),
    ]),
  ])
}

const router = createRouter({
  history: createWebHistory(),
  routes: [...baseRoutes, ...moduleRoutes],
})

createAuthGuard(router)

export default router
