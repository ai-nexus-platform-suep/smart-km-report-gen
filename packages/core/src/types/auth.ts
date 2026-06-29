// === 角色 ===
export type Role = 'USER' | 'ADMIN'

// === 用户 ===
export interface UserInfo {
  id: number
  username: string
  nickname: string | null
  email: string | null
  phone: string | null
  avatar: string | null
  role: Role
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
  token: string
  user: UserInfo
}
