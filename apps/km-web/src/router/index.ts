import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { baseRoutes, createAuthGuard } from '@platform/ui'

const moduleRoutes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/knowledge',
  },
  {
    path: '/knowledge',
    name: 'Knowledge',
    component: () => import('../pages/KnowledgeList.vue'),
    meta: { title: '知识库管理' },
  },
  {
    path: '/knowledge/create',
    name: 'KnowledgeCreate',
    component: () => import('../pages/KnowledgeCreate.vue'),
    meta: { title: '新建知识库' },
  },
  {
    path: '/knowledge/:id',
    name: 'KnowledgeEdit',
    component: () => import('../pages/KnowledgeEdit.vue'),
    meta: { title: '编辑知识库' },
  },
  {
    path: '/knowledge/:kbId/documents',
    name: 'DocumentList',
    component: () => import('../pages/DocumentList.vue'),
    meta: { title: '文档管理' },
  },
  {
    path: '/search',
    name: 'Search',
    component: () => import('../pages/SearchPage.vue'),
    meta: { title: '知识检索' },
  },
  {
    path: '/admin',
    children: [
      {
        path: 'km/dashboard',
        component: () => import('../pages/admin/AdminDashboard.vue'),
        meta: { title: 'KM统计', admin: true },
      },
      {
        path: 'km/embed',
        component: () => import('../pages/admin/EmbedConfig.vue'),
        meta: { title: '嵌入模型', admin: true },
      },
      {
        path: 'km/rerank',
        component: () => import('../pages/admin/RerankConfig.vue'),
        meta: { title: '重排序', admin: true },
      },
      {
        path: 'km/parser',
        component: () => import('../pages/admin/ParserConfig.vue'),
        meta: { title: '解析器', admin: true },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes: [...baseRoutes, ...moduleRoutes],
})

createAuthGuard(router)

export default router