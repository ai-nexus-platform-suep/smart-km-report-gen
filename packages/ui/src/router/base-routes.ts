import type { RouteRecordRaw } from 'vue-router'

// 公共路由 — 所有 app 统一挂载
// 各组在 apps/{name}/src/router/index.ts 中与自己的模块路由合并
export const baseRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../components/LoginPage.vue'),
    meta: { title: '登录', requiresAuth: false },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../components/RegisterPage.vue'),
    meta: { title: '注册', requiresAuth: false },
  },
]
