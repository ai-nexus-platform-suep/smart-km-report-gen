import type { RouteRecordRaw } from 'vue-router'

// Routes exposed to apps/platform-web. report-web keeps ownership of reports/*
// and report admin page imports behind this module boundary.
export const reportPlatformRoutes: RouteRecordRaw[] = [
  {
    path: '/reports',
    name: 'ReportList',
    component: () => import('./pages/reports/ReportListPage.vue'),
    meta: { title: '报告记录' },
  },
  {
    path: '/reports/list',
    redirect: '/reports',
  },
  {
    path: '/reports/new',
    name: 'ReportCreate',
    component: () => import('./pages/reports/NewReportPage.vue'),
    meta: { title: '新建报告' },
  },
  {
    path: '/reports/dashboard',
    name: 'ReportDashboard',
    component: () => import('./pages/admin/AdminDashboardPage.vue'),
    meta: { title: '趋势统计', admin: true },
  },
  {
    path: '/reports/:id/outline',
    name: 'ReportOutline',
    component: () => import('./pages/reports/OutlinePage.vue'),
    meta: { title: '报告大纲' },
  },
  {
    path: '/reports/:id/workspace',
    name: 'ReportWorkspace',
    component: () => import('./pages/reports/WorkspacePage.vue'),
    meta: { title: '报告工作台' },
  },
  {
    path: '/reports/:id/export',
    name: 'ReportExport',
    component: () => import('./pages/reports/ExportPage.vue'),
    meta: { title: '报告导出' },
  },
  {
    path: '/reports/templates',
    name: 'ReportTemplates',
    component: () => import('./pages/admin/TemplateAdminPage.vue'),
    meta: { title: '模板管理', admin: true },
  },
  {
    path: '/admin/templates',
    name: 'ReportTemplatesCompat',
    component: () => import('./pages/admin/TemplateAdminPage.vue'),
    meta: { title: '模板管理', admin: true },
  },
  {
    path: '/reports/llm-configs',
    redirect: '/qa/llm',
  },
  {
    path: '/admin/llm-configs',
    redirect: '/qa/llm',
  },
  {
    path: '/admin/dashboard',
    name: 'ReportAdminDashboardCompat',
    component: () => import('./pages/admin/AdminDashboardPage.vue'),
    meta: { title: '趋势统计', admin: true },
  },
]
