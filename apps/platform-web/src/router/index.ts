import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { baseRoutes, createAuthGuard } from '@platform/ui'
import { getStoredUser } from '@platform/core'
import type { UserInfo } from '@platform/core/types'
import { kmPlatformRoutes } from '@km/platform-routes'
import { qaPlatformRoutes } from '@qa/platform-routes'
import { reportPlatformRoutes } from '@report/platform-routes'

const platformOwnedRoutes: RouteRecordRaw[] = [
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
  {
    path: '/km/resources',
    redirect: '/km/documents',
  },
  {
    path: '/km/materials',
    redirect: '/km/documents',
  },
  {
    path: '/admin/overview',
    name: 'AdminOverview',
    component: () => import('../pages/admin/SystemOverviewPage.vue'),
    meta: { title: '总览统计', admin: true },
  },
  {
    path: '/admin/users',
    name: 'AdminUsers',
    component: () => import('../pages/admin/UserManagementPage.vue'),
    meta: {
      title: '用户管理',
      description: '统一用户、角色和账号状态维护会在系统管理模块接入。',
      admin: true,
    },
  },
  {
    path: '/admin/roles',
    name: 'AdminRoles',
    component: () => import('../pages/admin/RolePermissionPage.vue'),
    meta: {
      title: '角色权限',
      description: '角色组合、菜单权限和能力开关会统一收口到这个页面。',
      admin: true,
    },
  },
]

const moduleRoutes: RouteRecordRaw[] = [
  ...platformOwnedRoutes,
  ...kmPlatformRoutes,
  ...qaPlatformRoutes,
  ...reportPlatformRoutes,
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
  if (user?.role === 'ADMIN' || user?.role === 'SUPER_ADMIN') return true
  return '/dashboard'
})

export default router
