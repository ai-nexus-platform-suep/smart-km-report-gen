import { http, HttpResponse, delay } from 'msw'
import { API_QA } from '@platform/core'
import { findUser, findUserByUsername, buildLoginResponse, mockToken, mockUsers } from '../data'

export const authHandlers = [
  // 登录
  http.post(API_QA.AUTH.LOGIN, async ({ request }) => {
    await delay(600)
    const body = (await request.json()) as { username: string; password: string }

    if (!body.username || !body.password) {
      return HttpResponse.json(
        { code: 400, message: '用户名和密码不能为空', data: null },
        { status: 400 },
      )
    }

    const user = findUser(body.username, body.password)
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
    const body = (await request.json()) as { username: string; password: string }

    if (!body.username || !body.password) {
      return HttpResponse.json(
        { code: 400, message: '用户名和密码不能为空', data: null },
        { status: 400 },
      )
    }

    if (body.password.length < 6) {
      return HttpResponse.json(
        { code: 400, message: '密码至少6位', data: null },
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
    }

    return HttpResponse.json(
      { code: 200, message: '注册成功', data: buildLoginResponse(newUser) },
    )
  }),

  // 获取当前用户信息
  http.get(API_QA.AUTH.PROFILE, async ({ request }) => {
    await delay(300)
    const auth = request.headers.get('Authorization')
    if (!auth || !auth.includes(mockToken)) {
      return HttpResponse.json(
        { code: 401, message: '未登录', data: null },
        { status: 401 },
      )
    }
    const admin = mockUsers[0]
    const { password: _, ...userInfo } = admin
    return HttpResponse.json({ code: 200, message: 'ok', data: userInfo })
  }),
]
