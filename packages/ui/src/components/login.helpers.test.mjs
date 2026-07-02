import assert from 'node:assert/strict'
import ts from 'typescript'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const here = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(join(here, 'login.helpers.ts'), 'utf8')
const output = ts.transpileModule(source, {
  compilerOptions: {
    module: ts.ModuleKind.ES2022,
    target: ts.ScriptTarget.ES2022,
  },
}).outputText

const moduleUrl = `data:text/javascript;base64,${Buffer.from(output).toString('base64')}`
const { buildLoginPayload, normalizeLoginForm } = await import(moduleUrl)

assert.deepEqual(
  normalizeLoginForm({
    username: '  Admin@Example.COM  ',
    password: '  Pass123!  ',
    captchaCode: '  xY9k  ',
    captchaKey: '  captcha-key  ',
  }),
  {
    username: 'Admin@Example.COM',
    password: 'Pass123!',
    captchaCode: 'xY9k',
    captchaKey: 'captcha-key',
  },
)

assert.deepEqual(
  buildLoginPayload({
    username: 'Admin@Example.COM',
    password: 'Pass123!',
    captchaCode: 'xY9k',
    captchaKey: 'captcha-key',
  }),
  {
    username: 'Admin@Example.COM',
    password: 'Pass123!',
    captchaCode: 'xY9k',
    captchaKey: 'captcha-key',
    loginType: 'EMAIL',
  },
)

assert.deepEqual(
  buildLoginPayload({
    username: 'admin',
    password: 'Pass123!',
    captchaCode: 'xY9k',
    captchaKey: 'captcha-key',
  }),
  {
    username: 'admin',
    password: 'Pass123!',
    captchaCode: 'xY9k',
    captchaKey: 'captcha-key',
    loginType: 'USERNAME',
  },
)

console.log('login helpers ok')
