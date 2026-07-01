import type { ApiResponse, RegisterRequest } from '@platform/core/types'

export interface RegisterFormModel extends RegisterRequest {
  confirmPassword: string
  acceptTerms: boolean
}

export interface RegisterResultLike {
  status: number
  body?: Pick<ApiResponse<null>, 'code' | 'message'> | null
}

export function normalizeRegisterForm(form: Pick<RegisterFormModel, 'username' | 'password' | 'confirmPassword'>) {
  return {
    username: form.username.trim(),
    password: form.password.trim(),
    confirmPassword: form.confirmPassword.trim(),
  }
}

export function buildRegisterPayload(form: Pick<RegisterFormModel, 'username' | 'password'>): RegisterRequest {
  return {
    username: form.username,
    password: form.password,
  }
}

export function isRegisterSuccess(result: RegisterResultLike) {
  return result.body?.code === 200 && (result.status === 200 || result.status === 201)
}

export function getRegisterErrorMessage(error: unknown, fallback = '注册失败，请稍后重试') {
  if (typeof error === 'object' && error && 'response' in error) {
    const response = (error as { response?: { data?: { code?: number; message?: string } } }).response
    if (response?.data?.code === 1002) return '用户已存在，请直接登录'
    return response?.data?.message || fallback
  }
  return fallback
}
