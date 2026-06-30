import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { baseRoutes, createAuthGuard } from '@platform/ui'
import AuthGuard from '@platform/ui/src/components/AuthGuard.vue'
import { h } from 'vue'

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
        component: guarded('AdminDashboard'),
        meta: { title: 'KM统计', admin: true },
      },
      {
        path: 'km/embed',
        component: guarded('EmbedConfig'),
        meta: { title: '嵌入模型', admin: true },
      },
      {
        path: 'km/rerank',
        component: guarded('RerankConfig'),
        meta: { title: '重排序', admin: true },
      },
      {
        path: 'km/parser',
        component: guarded('ParserConfig'),
        meta: { title: '解析器', admin: true },
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
