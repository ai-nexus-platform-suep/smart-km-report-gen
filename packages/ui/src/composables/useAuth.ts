import { ref, computed } from 'vue'
import { getStoredUser, getToken, clearToken, setToken, setStoredUser } from '@platform/core'
import type { UserInfo } from '@platform/core/types'

const user = ref<UserInfo | null>(getStoredUser<UserInfo>())
const isLoggedIn = computed(() => !!getToken())
const isAdmin = computed(() => user.value?.role === 'ADMIN' || user.value?.role === 'SUPER_ADMIN')

export function useAuth() {
  function login(token: string, u: UserInfo) {
    setToken(token)
    setStoredUser(u)
    user.value = u
  }

  function logout() {
    clearToken()
    user.value = null
  }

  function refresh() {
    user.value = getStoredUser<UserInfo>()
  }

  return { user: computed(() => user.value), isLoggedIn, isAdmin, login, logout, refresh }
}
