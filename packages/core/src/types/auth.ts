// === 角色 ===
export type Role = 'USER' | 'ADMIN' | 'SUPER_ADMIN'
export type BackendRole = 'ROLE_USER' | 'ROLE_ADMIN' | string

// === 用户 ===
export interface UserInfo {
  id: number
  username: string
  nickname: string | null
  email: string | null
  phone: string | null
  avatar: string | null
  realName?: string | null
  gender?: 0 | 1 | 2 | number | null
  role: Role
  roles?: BackendRole[]
  permissions?: string[]
  status: 0 | 1
}

export interface LoginRequest {
  username: string
  password: string
  captchaCode: string
  captchaKey: string
  loginType?: 'USERNAME' | 'EMAIL'
}

export type RegisterType = 'USERNAME' | 'EMAIL'

export interface RegisterRequest {
  registerType?: RegisterType
  username?: string
  password?: string
  confirmPassword?: string
  email?: string
  emailCode?: string
  captchaCode: string
  captchaKey: string
}

export interface CaptchaResponse {
  captchaKey: string
  captchaImage: string
}

export interface SendRegisterCodeRequest {
  email: string
}

export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  username: string
  roles: BackendRole[]
  permissions?: string[]
}

export interface RefreshTokenRequest {
  refreshToken: string
}

export type RefreshTokenResponse = LoginResponse

export interface CurrentUserResponse {
  id: number
  username: string
  nickname?: string | null
  realName?: string | null
  email?: string | null
  phone?: string | null
  avatar?: string | null
  gender?: 0 | 1 | 2 | number | null
  roles: BackendRole[]
  permissions?: string[]
}
