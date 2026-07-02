import { http, HttpResponse, delay } from 'msw'
import { API_QA } from '@platform/core'
import {
  findUser,
  findUserByUsername,
  buildLoginResponse,
  mockRefreshToken,
  mockToken,
  mockUsers,
} from '../data'

const mockCaptchaKey = 'mock-captcha-key'
const mockCaptchaCode = '1234'
const mockEmailCode = '123456'
const mockCaptchaImage = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(
  '<svg xmlns="http://www.w3.org/2000/svg" width="118" height="48" viewBox="0 0 118 48"><rect width="118" height="48" rx="10" fill="#eff6ff"/><text x="59" y="31" text-anchor="middle" font-family="Arial" font-size="22" font-weight="700" fill="#1d4ed8">1234</text></svg>',
)}`

export const authHandlers = [
  http.get(API_QA.AUTH.CAPTCHA, async () => {
    await delay(120)
    return HttpResponse.json({
      code: 200,
      message: '操作成功',
      data: {
        captchaKey: mockCaptchaKey,
        captchaImage: mockCaptchaImage,
      },
    })
  }),

  http.post(API_QA.AUTH.SEND_REGISTER_CODE, async ({ request }) => {
    await delay(300)
    const body = (await request.json()) as { email: string }
    if (!body.email) {
      return HttpResponse.json(
        { code: 400, message: '邮箱不能为空', data: null },
        { status: 400 },
      )
    }
    return HttpResponse.json({ code: 200, message: `验证码已发送，mock 验证码为 ${mockEmailCode}`, data: null })
  }),

  // 登录
  http.post(API_QA.AUTH.LOGIN, async ({ request }) => {
    await delay(600)
    const body = (await request.json()) as {
      username: string
      password: string
      captchaCode: string
      captchaKey: string
      loginType?: 'USERNAME' | 'EMAIL'
    }

    if (!body.username || !body.password || !body.captchaCode || !body.captchaKey) {
      return HttpResponse.json(
        { code: 400, message: '用户名、密码和验证码不能为空', data: null },
        { status: 400 },
      )
    }

    if (body.captchaKey !== mockCaptchaKey || body.captchaCode.trim() !== mockCaptchaCode) {
      return HttpResponse.json(
        { code: 1008, message: '图形验证码错误或已过期', data: null },
        { status: 400 },
      )
    }

    const user =
      body.loginType === 'EMAIL'
        ? mockUsers.find((u) => u.email === body.username && u.password === body.password)
        : findUser(body.username, body.password)
    if (!user) {
      return HttpResponse.json(
        { code: 401, message: '用户名或密码错误', data: null },
        { status: 401 },
      )
    }

    const { password: _, ...userInfo } = user
    return HttpResponse.json({ code: 200, message: '登录成功', data: buildLoginResponse(userInfo) })
  }),

  // 注册
  http.post(API_QA.AUTH.REGISTER, async ({ request }) => {
    await delay(600)
    const body = (await request.json()) as {
      registerType?: 'USERNAME' | 'EMAIL'
      username?: string
      password?: string
      confirmPassword?: string
      email?: string
      emailCode?: string
      captchaCode: string
      captchaKey: string
    }

    if (body.captchaKey !== mockCaptchaKey || body.captchaCode?.trim() !== mockCaptchaCode) {
      return HttpResponse.json(
        { code: 1008, message: '图形验证码错误或已过期', data: null },
        { status: 400 },
      )
    }

    if (body.registerType === 'EMAIL') {
      if (!body.email || !body.emailCode) {
        return HttpResponse.json(
          { code: 400, message: '邮箱和邮箱验证码不能为空', data: null },
          { status: 400 },
        )
      }

      if (body.emailCode.trim() !== mockEmailCode) {
        return HttpResponse.json(
          { code: 1009, message: '邮箱验证码错误或已过期', data: null },
          { status: 400 },
        )
      }

      if (mockUsers.some((u) => u.email === body.email)) {
        return HttpResponse.json(
          { code: 1010, message: '邮箱已被注册', data: null },
          { status: 409 },
        )
      }

      const username = body.email.split('@')[0].replace(/[^a-zA-Z0-9_]/g, '_')
      mockUsers.push({
        id: mockUsers.length + 1,
        username,
        nickname: username,
        email: body.email,
        phone: null,
        avatar: null,
        role: 'USER' as const,
        status: 1 as const,
        password: 'Mock123!',
      })
      const newUser = mockUsers[mockUsers.length - 1]
      const { password: _, ...userInfo } = newUser

      return HttpResponse.json(
        { code: 200, message: '注册成功', data: buildLoginResponse(userInfo) },
        { status: 201 },
      )
    }

    if (!body.username || !body.password || !body.confirmPassword) {
      return HttpResponse.json(
        { code: 400, message: '用户名和密码不能为空', data: null },
        { status: 400 },
      )
    }

    if (body.password !== body.confirmPassword) {
      return HttpResponse.json(
        { code: 400, message: '两次密码输入不一致', data: null },
        { status: 400 },
      )
    }

    if (body.password.length < 8) {
      return HttpResponse.json(
        { code: 400, message: '密码至少8位', data: null },
        { status: 400 },
      )
    }

    const existing = findUserByUsername(body.username)
    if (existing) {
      return HttpResponse.json(
        { code: 409, message: '用户名已存在', data: null },
        { status: 409 },
      )
    }

    const newUser = {
      id: mockUsers.length + 1,
      username: body.username,
      nickname: body.username,
      email: null,
      phone: null,
      avatar: null,
      role: 'USER' as const,
      status: 1 as const,
      password: body.password,
    }
    mockUsers.push(newUser)
    const { password: _, ...userInfo } = newUser

    return HttpResponse.json(
      { code: 200, message: '注册成功', data: buildLoginResponse(userInfo) },
      { status: 201 },
    )
  }),

  // 刷新 Token
  http.post(API_QA.AUTH.REFRESH, async ({ request }) => {
    await delay(300)
    const body = (await request.json()) as { refreshToken: string }
    if (body.refreshToken !== mockRefreshToken) {
      return HttpResponse.json(
        { code: 401, message: 'Refresh Token 无效', data: null },
        { status: 401 },
      )
    }

    const admin = mockUsers[0]
    const { password: _, ...userInfo } = admin
    return HttpResponse.json({
      code: 200,
      message: 'Token 刷新成功',
      data: buildLoginResponse(userInfo),
    })
  }),

  // 获取当前用户信息
  http.get(API_QA.AUTH.ME, async ({ request }) => {
    await delay(300)
    const auth = request.headers.get('Authorization')
    if (!auth || !auth.includes(mockToken)) {
      return HttpResponse.json(
        { code: 401, message: '未登录', data: null },
        { status: 401 },
      )
    }
    const admin = mockUsers[0]
    return HttpResponse.json({
      code: 200,
      message: '操作成功',
      data: {
        id: admin.id,
        username: admin.username,
        nickname: admin.nickname,
        realName: null,
        email: admin.email,
        phone: admin.phone,
        avatar: admin.avatar,
        gender: 0,
        roles: ['ROLE_ADMIN', 'ROLE_USER'],
        permissions: ['chat:conversation:use', 'chat:model:view', 'chat:model:manage', 'chat:stats:view'],
      },
    })
  }),

  http.put(API_QA.AUTH.CHANGE_PASSWORD, async ({ request }) => {
    await delay(300)
    const auth = request.headers.get('Authorization')
    if (!auth || !auth.includes(mockToken)) {
      return HttpResponse.json(
        { code: 401, message: '未登录', data: null },
        { status: 401 },
      )
    }

    const body = (await request.json()) as { oldPassword?: string; newPassword?: string }
    if (!body.oldPassword || !body.newPassword) {
      return HttpResponse.json(
        { code: 400, message: '原密码和新密码不能为空', data: null },
        { status: 400 },
      )
    }

    const admin = mockUsers[0]
    if (admin.password !== body.oldPassword) {
      return HttpResponse.json(
        { code: 400, message: '原密码错误', data: null },
        { status: 400 },
      )
    }

    if (admin.password === body.newPassword) {
      return HttpResponse.json(
        { code: 400, message: '新密码不能与原密码相同', data: null },
        { status: 400 },
      )
    }

    admin.password = body.newPassword
    return HttpResponse.json({ code: 200, message: '密码修改成功，请重新登录', data: null })
  }),

  http.post(API_QA.AUTH.LOGOUT, async () => {
    await delay(200)
    return HttpResponse.json({ code: 200, message: '退出成功', data: null })
  }),
]
