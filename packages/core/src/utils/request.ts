import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'
import { getToken, clearToken } from './auth'
import { CODE } from '../constants'

const BASE_URL = import.meta.env.VITE_API_BASE || ''

const instance: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
})

// 请求拦截：自动带 token
instance.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截：401 自动跳登录
instance.interceptors.response.use(
  (res) => res,
  (error) => {
    if (error.response?.status === CODE.UNAUTHORIZED) {
      clearToken()
      window.location.href = '/login'
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

export function apiDelete<T = unknown>(url: string, config?: AxiosRequestConfig) {
  return instance.delete<T>(url, config)
}

export { instance as httpClient }
