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
  normalizeRegisterForm,
} = await import(moduleUrl)

assert.deepEqual(
  normalizeRegisterForm({
    username: '  alice  ',
    password: '  pass123  ',
    confirmPassword: '  pass123  ',
  }),
  {
    username: 'alice',
    password: 'pass123',
    confirmPassword: 'pass123',
  },
)

assert.deepEqual(
  buildRegisterPayload({
    username: 'alice',
    password: 'pass123',
    confirmPassword: 'pass123',
  }),
  { username: 'alice', password: 'pass123' },
)

assert.equal(isRegisterSuccess({ status: 201, body: { code: 200 } }), true)
assert.equal(isRegisterSuccess({ status: 200, body: { code: 200 } }), true)
assert.equal(isRegisterSuccess({ status: 201, body: { code: 1002 } }), false)

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
