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
  role: Role
  roles?: BackendRole[]
  status: 0 | 1
}

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
  nickname?: string
  email?: string
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  username: string
  roles: BackendRole[]
}

export interface RefreshTokenRequest {
  refreshToken: string
}

export type RefreshTokenResponse = LoginResponse

export interface CurrentUserResponse {
  username: string
  roles: BackendRole[]
}
