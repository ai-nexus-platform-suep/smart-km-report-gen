import type { Router } from 'vue-router'
import { isLoggedIn } from '@platform/core'

export function createAuthGuard(router: Router) {
  router.beforeEach((to, _from, next) => {
    if (to.meta.requiresAuth === false) return next()
    if (!isLoggedIn()) return next('/login')
    next()
  })
}
