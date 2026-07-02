import type { RouteRecordRaw } from 'vue-router'

// Routes exposed to apps/platform-web. Keep km-web internal page paths here so
// the platform shell only depends on this public module boundary.
export const kmPlatformRoutes: RouteRecordRaw[] = [
  {
    path: '/km/bases',
    name: 'KnowledgeBases',
    component: () => import('./pages/KnowledgeList.vue'),
    meta: { title: '知识库管理' },
  },
  {
    path: '/km/documents',
    name: 'KnowledgeDocumentsEntry',
    component: () => import('./pages/KnowledgeList.vue'),
    meta: { title: '文档管理', documentEntry: true },
  },
  {
    path: '/knowledge',
    name: 'KnowledgeBasesCompat',
    component: () => import('./pages/KnowledgeList.vue'),
    meta: { title: '知识库管理' },
  },
  {
    path: '/km/bases/create',
    name: 'KnowledgeCreate',
    component: () => import('./pages/KnowledgeCreate.vue'),
    meta: { title: '新建知识库', admin: true },
  },
  {
    path: '/knowledge/create',
    name: 'KnowledgeCreateCompat',
    component: () => import('./pages/KnowledgeCreate.vue'),
    meta: { title: '新建知识库', admin: true },
  },
  {
    path: '/km/bases/:kbId/documents',
    redirect: (to) => ({
      path: `/km/documents/${String(to.params.kbId)}`,
      query: to.query,
    }),
  },
  {
    path: '/km/documents/:kbId',
    name: 'KnowledgeDocumentsByBase',
    component: () => import('./pages/DocumentList.vue'),
    meta: { title: '文档管理' },
  },
  {
    path: '/knowledge/:kbId/documents',
    name: 'KnowledgeDocumentsByBaseCompat',
    component: () => import('./pages/DocumentList.vue'),
    meta: { title: '文档管理' },
  },
  {
    path: '/km/bases/:id',
    name: 'KnowledgeEdit',
    component: () => import('./pages/KnowledgeEdit.vue'),
    meta: { title: '编辑知识库', admin: true },
  },
  {
    path: '/knowledge/:id',
    name: 'KnowledgeEditCompat',
    component: () => import('./pages/KnowledgeEdit.vue'),
    meta: { title: '编辑知识库', admin: true },
  },
  {
    path: '/km/search',
    name: 'KnowledgeSearch',
    component: () => import('./pages/SearchPage.vue'),
    meta: { title: '知识检索' },
  },
  {
    path: '/search',
    name: 'KnowledgeSearchCompat',
    component: () => import('./pages/SearchPage.vue'),
    meta: { title: '知识检索' },
  },
  {
    path: '/km/settings',
    name: 'KnowledgeSettings',
    component: () => import('./pages/admin/EmbedConfig.vue'),
    meta: { title: '模型配置', admin: true },
  },
  {
    path: '/admin/km/dashboard',
    name: 'KmAdminDashboard',
    component: () => import('./pages/admin/AdminDashboard.vue'),
    meta: { title: 'KM 统计', admin: true },
  },
  {
    path: '/admin/km/embed',
    name: 'KmEmbedConfig',
    component: () => import('./pages/admin/EmbedConfig.vue'),
    meta: { title: '嵌入模型', admin: true },
  },
  {
    path: '/admin/km/rerank',
    name: 'KmRerankConfig',
    component: () => import('./pages/admin/RerankConfig.vue'),
    meta: { title: '重排序', admin: true },
  },
  {
    path: '/admin/km/parser',
    name: 'KmParserConfig',
    component: () => import('./pages/admin/ParserConfig.vue'),
    meta: { title: '解析器', admin: true },
  },
]
