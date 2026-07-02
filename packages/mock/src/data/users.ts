import type { UserInfo, LoginResponse } from '@platform/core/types'

export const mockToken = 'mock-jwt-token-2024'
export const mockRefreshToken = 'mock-refresh-token-2024'

export const mockUsers: (UserInfo & { password: string })[] = [
  {
    id: 1,
    username: 'admin',
    password: 'admin123',
    nickname: '管理员',
    email: 'admin@tsp.com',
    phone: '13800138000',
    avatar: null,
    role: 'ADMIN',
    status: 1,
  },
  {
    id: 2,
    username: 'user',
    password: 'user123',
    nickname: '普通用户',
    email: 'user@tsp.com',
    phone: '13900139000',
    avatar: null,
    role: 'USER',
    status: 1,
  },
]

export function buildLoginResponse(user: UserInfo): LoginResponse {
  const roles =
    user.role === 'SUPER_ADMIN'
      ? ['ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_USER']
      : user.role === 'ADMIN'
        ? ['ROLE_ADMIN', 'ROLE_USER']
        : ['ROLE_USER']

  return {
    accessToken: mockToken,
    refreshToken: mockRefreshToken,
    tokenType: 'Bearer',
    expiresIn: 900,
    username: user.username,
    roles,
    permissions:
      user.role === 'USER'
        ? ['chat:conversation:use', 'chat:model:view', 'chat:stats:view']
        : ['chat:conversation:use', 'chat:model:view', 'chat:model:manage', 'chat:stats:view'],
  }
}

export function findUser(username: string, password: string) {
  return mockUsers.find((u) => u.username === username && u.password === password)
}

export function findUserByUsername(username: string) {
  return mockUsers.find((u) => u.username === username)
}
