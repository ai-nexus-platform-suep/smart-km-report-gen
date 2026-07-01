import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { baseRoutes, createAuthGuard } from '@platform/ui'
import { getStoredUser } from '@platform/core'
import type { UserInfo } from '@platform/core/types'

const moduleRoutes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/dashboard',
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('../pages/DashboardPage.vue'),
    meta: { title: '平台首页' },
  },

  // Knowledge management: platform paths plus original km-web paths used by page internals.
  {
    path: '/km/bases',
    name: 'KnowledgeBases',
    component: () => import('../../../km-web/src/pages/KnowledgeList.vue'),
    meta: { title: '知识库管理' },
  },
  {
    path: '/knowledge',
    name: 'KnowledgeBasesCompat',
    component: () => import('../../../km-web/src/pages/KnowledgeList.vue'),
    meta: { title: '知识库管理' },
  },
  {
    path: '/km/bases/create',
    name: 'KnowledgeCreate',
    component: () => import('../../../km-web/src/pages/KnowledgeCreate.vue'),
    meta: { title: '新建知识库', admin: true },
  },
  {
    path: '/knowledge/create',
    name: 'KnowledgeCreateCompat',
    component: () => import('../../../km-web/src/pages/KnowledgeCreate.vue'),
    meta: { title: '新建知识库', admin: true },
  },
  {
    path: '/km/bases/:kbId/documents',
    name: 'KnowledgeDocumentsByBase',
    component: () => import('../../../km-web/src/pages/DocumentList.vue'),
    meta: { title: '文档管理' },
  },
  {
    path: '/knowledge/:kbId/documents',
    name: 'KnowledgeDocumentsByBaseCompat',
    component: () => import('../../../km-web/src/pages/DocumentList.vue'),
    meta: { title: '文档管理' },
  },
  {
    path: '/km/bases/:id',
    name: 'KnowledgeEdit',
    component: () => import('../../../km-web/src/pages/KnowledgeEdit.vue'),
    meta: { title: '编辑知识库', admin: true },
  },
  {
    path: '/knowledge/:id',
    name: 'KnowledgeEditCompat',
    component: () => import('../../../km-web/src/pages/KnowledgeEdit.vue'),
    meta: { title: '编辑知识库', admin: true },
  },
  {
    path: '/km/documents',
    name: 'KnowledgeDocuments',
    component: () => import('../pages/km/DocumentsPage.vue'),
    meta: { title: '文档管理' },
  },
  {
    path: '/km/search',
    name: 'KnowledgeSearch',
    component: () => import('../../../km-web/src/pages/SearchPage.vue'),
    meta: { title: '知识检索' },
  },
  {
    path: '/search',
    name: 'KnowledgeSearchCompat',
    component: () => import('../../../km-web/src/pages/SearchPage.vue'),
    meta: { title: '知识检索' },
  },
  {
    path: '/km/materials',
    name: 'KnowledgeMaterials',
    component: () => import('../pages/PlaceholderPage.vue'),
    meta: {
      title: '素材管理',
      description: '素材管理会在后续阶段接入标签编辑、筛选和批量操作能力。',
    },
  },
  {
    path: '/km/settings',
    name: 'KnowledgeSettings',
    component: () => import('../../../km-web/src/pages/admin/EmbedConfig.vue'),
    meta: { title: '模型配置', admin: true },
  },
  {
    path: '/admin/km/dashboard',
    name: 'KmAdminDashboard',
    component: () => import('../../../km-web/src/pages/admin/AdminDashboard.vue'),
    meta: { title: 'KM 统计', admin: true },
  },
  {
    path: '/admin/km/embed',
    name: 'KmEmbedConfig',
    component: () => import('../../../km-web/src/pages/admin/EmbedConfig.vue'),
    meta: { title: '嵌入模型', admin: true },
  },
  {
    path: '/admin/km/rerank',
    name: 'KmRerankConfig',
    component: () => import('../../../km-web/src/pages/admin/RerankConfig.vue'),
    meta: { title: '重排序', admin: true },
  },
  {
    path: '/admin/km/parser',
    name: 'KmParserConfig',
    component: () => import('../../../km-web/src/pages/admin/ParserConfig.vue'),
    meta: { title: '解析器', admin: true },
  },

  // Intelligent QA: keep original qa-web paths for in-page navigation compatibility.
  {
    path: '/qa/chat',
    name: 'QaChat',
    component: () => import('../../../qa-web/src/pages/ChatView.vue'),
    meta: { title: '智能对话' },
  },
  {
    path: '/chat',
    name: 'QaChatCompat',
    component: () => import('../../../qa-web/src/pages/ChatView.vue'),
    meta: { title: '智能对话' },
  },
  {
    path: '/qa/conversations',
    name: 'QaConversations',
    component: () => import('../../../qa-web/src/pages/Conversations.vue'),
    meta: { title: '会话记录' },
  },
  {
    path: '/conversations',
    name: 'QaConversationsCompat',
    component: () => import('../../../qa-web/src/pages/Conversations.vue'),
    meta: { title: '会话记录' },
  },
  {
    path: '/qa/retrieval-test',
    name: 'QaRetrievalTest',
    component: () => import('../../../qa-web/src/pages/admin/RetrievalTest.vue'),
    meta: { title: '检索测试', admin: true },
  },
  {
    path: '/admin/qa/retrieval-test',
    name: 'QaRetrievalTestCompat',
    component: () => import('../../../qa-web/src/pages/admin/RetrievalTest.vue'),
    meta: { title: '检索测试', admin: true },
  },
  {
    path: '/qa/settings',
    name: 'QaSettings',
    component: () => import('../../../qa-web/src/pages/admin/QaConfig.vue'),
    meta: { title: '问答配置', admin: true },
  },
  {
    path: '/admin/qa/config',
    name: 'QaSettingsCompat',
    component: () => import('../../../qa-web/src/pages/admin/QaConfig.vue'),
    meta: { title: '问答配置', admin: true },
  },
  {
    path: '/qa/llm',
    name: 'QaLlmSettings',
    component: () => import('../../../qa-web/src/pages/admin/LlmConfig.vue'),
    meta: { title: 'LLM 配置', admin: true },
  },
  {
    path: '/admin/qa/llm',
    name: 'QaLlmSettingsCompat',
    component: () => import('../../../qa-web/src/pages/admin/LlmConfig.vue'),
    meta: { title: 'LLM 配置', admin: true },
  },
  {
    path: '/admin/qa/dashboard',
    name: 'QaAdminDashboard',
    component: () => import('../../../qa-web/src/pages/admin/QaDashboard.vue'),
    meta: { title: '问答统计', admin: true },
  },

  // Report generation: use the latest report-web pages and preserve /reports as canonical list path.
  {
    path: '/reports',
    name: 'ReportList',
    component: () => import('../../../report-web/src/pages/reports/ReportListPage.vue'),
    meta: { title: '报告记录' },
  },
  {
    path: '/reports/list',
    redirect: '/reports',
  },
  {
    path: '/reports/new',
    name: 'ReportCreate',
    component: () => import('../../../report-web/src/pages/reports/NewReportPage.vue'),
    meta: { title: '新建报告' },
  },
  {
    path: '/reports/:id/outline',
    name: 'ReportOutline',
    component: () => import('../../../report-web/src/pages/reports/OutlinePage.vue'),
    meta: { title: '报告大纲' },
  },
  {
    path: '/reports/:id/workspace',
    name: 'ReportWorkspace',
    component: () => import('../../../report-web/src/pages/reports/WorkspacePage.vue'),
    meta: { title: '报告工作台' },
  },
  {
    path: '/reports/:id/export',
    name: 'ReportExport',
    component: () => import('../../../report-web/src/pages/reports/ExportPage.vue'),
    meta: { title: '报告导出' },
  },
  {
    path: '/reports/templates',
    name: 'ReportTemplates',
    component: () => import('../../../report-web/src/pages/admin/TemplateAdminPage.vue'),
    meta: { title: '模板管理', admin: true },
  },
  {
    path: '/admin/templates',
    name: 'ReportTemplatesCompat',
    component: () => import('../../../report-web/src/pages/admin/TemplateAdminPage.vue'),
    meta: { title: '模板管理', admin: true },
  },
  {
    path: '/reports/llm-configs',
    name: 'ReportLlmConfig',
    component: () => import('../../../report-web/src/pages/admin/LlmConfigPage.vue'),
    meta: { title: '报告模型配置', admin: true },
  },
  {
    path: '/admin/llm-configs',
    name: 'ReportLlmConfigCompat',
    component: () => import('../../../report-web/src/pages/admin/LlmConfigPage.vue'),
    meta: { title: '报告模型配置', admin: true },
  },
  {
    path: '/reports/materials',
    name: 'ReportMaterials',
    component: () => import('../pages/PlaceholderPage.vue'),
    meta: {
      title: '素材映射',
      description: '素材映射将在报告组页面骨架完成后并入统一入口。',
      admin: true,
    },
  },
  {
    path: '/admin/overview',
    name: 'AdminOverview',
    component: () => import('../../../report-web/src/pages/admin/AdminDashboardPage.vue'),
    meta: { title: '总览统计', admin: true },
  },
  {
    path: '/admin/dashboard',
    name: 'ReportAdminDashboardCompat',
    component: () => import('../../../report-web/src/pages/admin/AdminDashboardPage.vue'),
    meta: { title: '趋势监控', admin: true },
  },
  {
    path: '/admin/users',
    name: 'AdminUsers',
    component: () => import('../pages/PlaceholderPage.vue'),
    meta: {
      title: '用户管理',
      description: '统一用户、角色和账号状态维护会在系统管理模块接入。',
      admin: true,
    },
  },
  {
    path: '/admin/roles',
    name: 'AdminRoles',
    component: () => import('../pages/PlaceholderPage.vue'),
    meta: {
      title: '角色权限',
      description: '角色组合、菜单权限和能力开关会统一收口到这个页面。',
      admin: true,
    },
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard',
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes: [...baseRoutes, ...moduleRoutes],
})

createAuthGuard(router)

router.beforeEach((to) => {
  if (!to.meta.admin) return true
  const user = getStoredUser<UserInfo>()
  if (user?.role === 'ADMIN') return true
  return '/dashboard'
})

export default router
