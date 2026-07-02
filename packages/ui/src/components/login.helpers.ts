import type { LoginRequest } from '@platform/core/types'

export interface LoginFormModel {
  username: string
  password: string
  captchaCode: string
  captchaKey: string
}

export function normalizeLoginForm(form: LoginFormModel): LoginFormModel {
  return {
    username: form.username.trim(),
    password: form.password.trim(),
    captchaCode: form.captchaCode.trim(),
    captchaKey: form.captchaKey.trim(),
  }
}

export function buildLoginPayload(form: LoginFormModel): LoginRequest {
  const account = form.username.trim()
  return {
    username: account,
    password: form.password.trim(),
    captchaCode: form.captchaCode.trim(),
    captchaKey: form.captchaKey.trim(),
    loginType: isEmailAccount(account) ? 'EMAIL' : 'USERNAME',
  }
}

function isEmailAccount(account: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(account)
}
