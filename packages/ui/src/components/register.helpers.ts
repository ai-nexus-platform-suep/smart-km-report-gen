import type { ApiResponse, LoginResponse, RegisterRequest, RegisterType } from '@platform/core/types'

export interface RegisterFormModel {
  registerType: RegisterType
  username: string
  password: string
  confirmPassword: string
  email: string
  emailCode: string
  captchaCode: string
  captchaKey: string
  acceptTerms: boolean
}

export interface RegisterResultLike {
  status: number
  body?: Pick<ApiResponse<null>, 'code' | 'message'> | null
}

export function normalizeRegisterForm(
  form: Pick<
    RegisterFormModel,
    | 'registerType'
    | 'username'
    | 'password'
    | 'confirmPassword'
    | 'email'
    | 'emailCode'
    | 'captchaCode'
    | 'captchaKey'
  >,
) {
  return {
    registerType: form.registerType,
    username: form.username.trim(),
    password: form.password.trim(),
    confirmPassword: form.confirmPassword.trim(),
    email: form.email.trim().toLowerCase(),
    emailCode: form.emailCode.trim(),
    captchaCode: form.captchaCode.trim(),
    captchaKey: form.captchaKey.trim(),
  }
}

export function buildRegisterPayload(
  form: Pick<
    RegisterFormModel,
    | 'registerType'
    | 'username'
    | 'password'
    | 'confirmPassword'
    | 'email'
    | 'emailCode'
    | 'captchaCode'
    | 'captchaKey'
  >,
): RegisterRequest {
  if (form.registerType === 'EMAIL') {
    return {
      registerType: 'EMAIL',
      email: form.email,
      emailCode: form.emailCode,
      captchaCode: form.captchaCode,
      captchaKey: form.captchaKey,
    }
  }

  return {
    registerType: 'USERNAME',
    username: form.username,
    password: form.password,
    confirmPassword: form.confirmPassword,
    captchaCode: form.captchaCode,
    captchaKey: form.captchaKey,
  }
}

export function isRegisterSuccess(result: RegisterResultLike) {
  return result.body?.code === 200 && (result.status === 200 || result.status === 201)
}

export function isRegisterAuthResponse(data: unknown): data is LoginResponse {
  if (typeof data !== 'object' || data === null) return false
  const auth = data as Partial<LoginResponse>
  return Boolean(
    auth.accessToken
      && auth.refreshToken
      && auth.username
      && typeof auth.expiresIn === 'number'
      && Array.isArray(auth.roles),
  )
}

export function getRegisterErrorMessage(error: unknown, fallback = '注册失败，请稍后重试') {
  if (typeof error === 'object' && error && 'response' in error) {
    const response = (error as { response?: { data?: { code?: number; message?: string } } }).response
    if (response?.data?.code === 1002) return '用户已存在，请直接登录'
    return response?.data?.message || fallback
  }
  return fallback
}
