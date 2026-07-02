import assert from 'node:assert/strict'
import ts from 'typescript'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const here = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(join(here, 'register.helpers.ts'), 'utf8')
const output = ts.transpileModule(source, {
  compilerOptions: {
    module: ts.ModuleKind.ES2022,
    target: ts.ScriptTarget.ES2022,
  },
}).outputText

const moduleUrl = `data:text/javascript;base64,${Buffer.from(output).toString('base64')}`
const {
  buildRegisterPayload,
  getRegisterErrorMessage,
  isRegisterSuccess,
  isRegisterAuthResponse,
  normalizeRegisterForm,
} = await import(moduleUrl)

assert.deepEqual(
  normalizeRegisterForm({
    registerType: 'USERNAME',
    username: '  alice  ',
    password: '  Pass123!  ',
    confirmPassword: '  Pass123!  ',
    email: '  Alice@Example.COM  ',
    emailCode: '  123456  ',
    captchaCode: '  aB12  ',
    captchaKey: '  captcha-key  ',
  }),
  {
    registerType: 'USERNAME',
    username: 'alice',
    password: 'Pass123!',
    confirmPassword: 'Pass123!',
    email: 'alice@example.com',
    emailCode: '123456',
    captchaCode: 'aB12',
    captchaKey: 'captcha-key',
  },
)

assert.deepEqual(
  buildRegisterPayload({
    registerType: 'USERNAME',
    username: 'alice',
    password: 'Pass123!',
    confirmPassword: 'Pass123!',
    email: '',
    emailCode: '',
    captchaCode: 'aB12',
    captchaKey: 'captcha-key',
  }),
  {
    registerType: 'USERNAME',
    username: 'alice',
    password: 'Pass123!',
    confirmPassword: 'Pass123!',
    captchaCode: 'aB12',
    captchaKey: 'captcha-key',
  },
)

assert.deepEqual(
  buildRegisterPayload({
    registerType: 'EMAIL',
    username: '',
    password: '',
    confirmPassword: '',
    email: 'alice@example.com',
    emailCode: '123456',
    captchaCode: 'aB12',
    captchaKey: 'captcha-key',
  }),
  {
    registerType: 'EMAIL',
    email: 'alice@example.com',
    emailCode: '123456',
    captchaCode: 'aB12',
    captchaKey: 'captcha-key',
  },
)

assert.equal(isRegisterSuccess({ status: 201, body: { code: 200 } }), true)
assert.equal(isRegisterSuccess({ status: 200, body: { code: 200 } }), true)
assert.equal(isRegisterSuccess({ status: 201, body: { code: 1002 } }), false)

assert.equal(
  isRegisterAuthResponse({
    accessToken: 'access-token',
    refreshToken: 'refresh-token',
    tokenType: 'Bearer',
    expiresIn: 900,
    username: 'alice',
    roles: ['ROLE_USER'],
    permissions: [],
  }),
  true,
)
assert.equal(isRegisterAuthResponse(null), false)
assert.equal(isRegisterAuthResponse({ accessToken: 'access-token' }), false)

assert.equal(
  getRegisterErrorMessage({ response: { data: { code: 1002, message: '用户已存在' } } }),
  '用户已存在，请直接登录',
)
assert.equal(
  getRegisterErrorMessage({ response: { data: { message: '用户名长度需在 3-50 个字符之间' } } }),
  '用户名长度需在 3-50 个字符之间',
)
assert.equal(getRegisterErrorMessage(new Error('Network Error')), '注册失败，请稍后重试')

console.log('register helpers ok')
