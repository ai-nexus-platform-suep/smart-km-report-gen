import type { ChangePasswordRequest } from '@platform/core/types'

export interface ChangePasswordFormModel {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}

const WEAK_PASSWORDS = new Set([
  'password',
  '12345678',
  '123456789',
  'qwerty123',
  'admin123',
  'abc12345',
  '11111111',
  'aaaaaaaa',
  'password1',
  'pa$$w0rd',
])

export function normalizeChangePasswordForm(form: ChangePasswordFormModel): ChangePasswordFormModel {
  return {
    oldPassword: form.oldPassword.trim(),
    newPassword: form.newPassword.trim(),
    confirmPassword: form.confirmPassword.trim(),
  }
}

export function buildChangePasswordPayload(form: Pick<ChangePasswordFormModel, 'oldPassword' | 'newPassword'>): ChangePasswordRequest {
  return {
    oldPassword: form.oldPassword,
    newPassword: form.newPassword,
  }
}

export function getPasswordStrengthError(newPassword: string, oldPassword = '') {
  const password = newPassword.trim()
  const currentPassword = oldPassword.trim()

  if (!password) return '请输入新密码'
  if (password.length < 8 || password.length > 100) return '密码长度需在 8-100 个字符之间'
  if (currentPassword && password === currentPassword) return '新密码不能与原密码相同'
  if (WEAK_PASSWORDS.has(password.toLowerCase())) return '密码过于简单，请使用更复杂的密码'

  const typeCount = [
    /[A-Z]/.test(password),
    /[a-z]/.test(password),
    /\d/.test(password),
    /[!@#$%^&*()_+\-=[\]{};':"\\|,.<>/?`~]/.test(password),
  ].filter(Boolean).length

  if (typeCount < 3) return '密码需包含大写字母、小写字母、数字、特殊字符中的至少3种'
  return null
}

export function getChangePasswordErrorMessage(error: unknown, fallback = '密码修改失败，请稍后重试') {
  if (typeof error === 'object' && error && 'response' in error) {
    const response = (error as { response?: { data?: { message?: string } } }).response
    return response?.data?.message || fallback
  }
  return fallback
}
