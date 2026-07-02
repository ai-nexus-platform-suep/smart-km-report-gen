import assert from 'node:assert/strict'
import ts from 'typescript'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const here = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(join(here, 'password.helpers.ts'), 'utf8')
const output = ts.transpileModule(source, {
  compilerOptions: {
    module: ts.ModuleKind.ES2022,
    target: ts.ScriptTarget.ES2022,
  },
}).outputText

const moduleUrl = `data:text/javascript;base64,${Buffer.from(output).toString('base64')}`
const {
  buildChangePasswordPayload,
  getChangePasswordErrorMessage,
  getPasswordStrengthError,
  normalizeChangePasswordForm,
} = await import(moduleUrl)

assert.deepEqual(
  normalizeChangePasswordForm({
    oldPassword: '  Old123!  ',
    newPassword: '  New12345!  ',
    confirmPassword: '  New12345!  ',
  }),
  {
    oldPassword: 'Old123!',
    newPassword: 'New12345!',
    confirmPassword: 'New12345!',
  },
)

assert.deepEqual(
  buildChangePasswordPayload({
    oldPassword: 'Old123!',
    newPassword: 'New12345!',
  }),
  {
    oldPassword: 'Old123!',
    newPassword: 'New12345!',
  },
)

assert.equal(getPasswordStrengthError('', 'Old123!'), '请输入新密码')
assert.equal(getPasswordStrengthError('short1!', 'Old123!'), '密码长度需在 8-100 个字符之间')
assert.equal(getPasswordStrengthError('Old12345!', 'Old12345!'), '新密码不能与原密码相同')
assert.equal(getPasswordStrengthError('password1', 'Old123!'), '密码过于简单，请使用更复杂的密码')
assert.equal(
  getPasswordStrengthError('lowercase123', 'Old123!'),
  '密码需包含大写字母、小写字母、数字、特殊字符中的至少3种',
)
assert.equal(getPasswordStrengthError('New12345!', 'Old123!'), null)

assert.equal(
  getChangePasswordErrorMessage({ response: { data: { message: '原密码错误' } } }),
  '原密码错误',
)
assert.equal(getChangePasswordErrorMessage(new Error('Network Error')), '密码修改失败，请稍后重试')

console.log('password helpers ok')
