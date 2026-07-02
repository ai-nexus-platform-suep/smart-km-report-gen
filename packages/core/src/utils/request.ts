import axios, { type AxiosError, type AxiosInstance, type AxiosRequestConfig } from 'axios'
import {
  buildUserFromAuthResponse,
  clearToken,
  getRefreshToken,
  getToken,
  getTokenType,
  setAuthTokens,
  setStoredUser,
} from './auth'
import { API_QA, CODE } from '../constants'
import type { ApiResponse, LoginResponse } from '../types'

const BASE_URL = import.meta.env.VITE_API_BASE || ''

type RetryableAxiosRequestConfig = AxiosRequestConfig & {
  _retry?: boolean
}

let refreshPromise: Promise<LoginResponse> | null = null

function quoteUnsafeJsonNumbers(json: string) {
  return json
    .replace(/(:\s*)(-?\d{16,})(\s*[,}])/g, '$1"$2"$3')
    .replace(/([\[,]\s*)(-?\d{16,})(\s*[,\]])/g, '$1"$2"$3')
}

const instance: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
  transformResponse: [
    (data) => {
      if (typeof data !== 'string' || !data) return data
      try {
        return JSON.parse(quoteUnsafeJsonNumbers(data))
      } catch {
        return data
      }
    },
  ],
})

function isAuthRequest(url = '') {
  return [API_QA.AUTH.LOGIN, API_QA.AUTH.REGISTER, API_QA.AUTH.REFRESH].some((path) => url.includes(path))
}

function redirectToLogin() {
  clearToken()
  if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
    window.location.href = '/login'
  }
}

async function refreshAuthToken() {
  const refreshToken = getRefreshToken()
  if (!refreshToken) throw new Error('No refresh token')

  if (!refreshPromise) {
    refreshPromise = instance
      .post<ApiResponse<LoginResponse>>(API_QA.AUTH.REFRESH, { refreshToken })
      .then((res) => {
        const auth = res.data.data
        if (!auth?.accessToken) throw new Error(res.data.message || 'Refresh token failed')
        setAuthTokens(auth)
        setStoredUser(buildUserFromAuthResponse(auth))
        return auth
      })
      .finally(() => {
        refreshPromise = null
      })
  }

  return refreshPromise
}

// 请求拦截：自动带 token
instance.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `${getTokenType()} ${token}`
  }
  return config
})

// 响应拦截：401 自动跳登录
instance.interceptors.response.use(
  (res) => res,
  async (error: AxiosError) => {
    const config = error.config as RetryableAxiosRequestConfig | undefined
    const status = error.response?.status

    if (status === CODE.UNAUTHORIZED && config && !config._retry && !isAuthRequest(config.url)) {
      config._retry = true
      try {
        const auth = await refreshAuthToken()
        config.headers = {
          ...config.headers,
          Authorization: `${auth.tokenType || 'Bearer'} ${auth.accessToken}`,
        }
        return instance(config)
      } catch {
        redirectToLogin()
      }
    } else if (status === CODE.UNAUTHORIZED && !isAuthRequest(config?.url)) {
      redirectToLogin()
    }

    return Promise.reject(error)
  },
)

export function apiGet<T = unknown>(url: string, params?: Record<string, unknown>, config?: AxiosRequestConfig) {
  return instance.get<T>(url, { params, ...config })
}

export function apiPost<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig) {
  return instance.post<T>(url, data, config)
}

export function apiPut<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig) {
  return instance.put<T>(url, data, config)
}

export function apiPatch<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig) {
  return instance.patch<T>(url, data, config)
}

export function apiDelete<T = unknown>(url: string, config?: AxiosRequestConfig) {
  return instance.delete<T>(url, config)
}

export { instance as httpClient }
