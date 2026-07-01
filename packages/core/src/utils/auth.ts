import type { BackendRole, LoginResponse, Role, UserInfo } from '../types/auth'

const TOKEN_KEY = 'tsp_token'
const REFRESH_TOKEN_KEY = 'tsp_refresh_token'
const TOKEN_TYPE_KEY = 'tsp_token_type'
const TOKEN_EXPIRES_AT_KEY = 'tsp_token_expires_at'
const USER_KEY = 'tsp_user'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY)
}

export function getTokenType(): string {
  return localStorage.getItem(TOKEN_TYPE_KEY) || 'Bearer'
}

export function setAuthTokens(
  auth: Pick<LoginResponse, 'accessToken' | 'refreshToken' | 'tokenType' | 'expiresIn'>,
): void {
  localStorage.setItem(TOKEN_KEY, auth.accessToken)
  localStorage.setItem(REFRESH_TOKEN_KEY, auth.refreshToken)
  localStorage.setItem(TOKEN_TYPE_KEY, auth.tokenType || 'Bearer')
  localStorage.setItem(TOKEN_EXPIRES_AT_KEY, String(Date.now() + auth.expiresIn * 1000))
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
  localStorage.removeItem(TOKEN_TYPE_KEY)
  localStorage.removeItem(TOKEN_EXPIRES_AT_KEY)
  localStorage.removeItem(USER_KEY)
}

export function isLoggedIn(): boolean {
  return !!getToken()
}

export function getStoredUser<T = Record<string, unknown>>(): T | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as T
  } catch {
    return null
  }
}

export function setStoredUser(user: Record<string, unknown>): void {
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function normalizeRole(roles: BackendRole[] = []): Role {
  if (roles.some((role) => role === 'ROLE_SUPER_ADMIN' || role === 'SUPER_ADMIN')) return 'SUPER_ADMIN'
  return roles.some((role) => role === 'ROLE_ADMIN' || role === 'ADMIN') ? 'ADMIN' : 'USER'
}

export function buildUserFromAuthResponse(auth: Pick<LoginResponse, 'username' | 'roles'>): UserInfo {
  return {
    id: 0,
    username: auth.username,
    nickname: auth.username,
    email: null,
    phone: null,
    avatar: null,
    role: normalizeRole(auth.roles),
    roles: auth.roles,
    status: 1,
  }
}
