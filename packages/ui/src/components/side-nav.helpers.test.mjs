import assert from 'node:assert/strict'
import ts from 'typescript'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const here = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(join(here, 'side-nav.helpers.ts'), 'utf8')
const output = ts.transpileModule(source, {
  compilerOptions: {
    module: ts.ModuleKind.ES2022,
    target: ts.ScriptTarget.ES2022,
  },
}).outputText

const moduleUrl = `data:text/javascript;base64,${Buffer.from(output).toString('base64')}`
const { resolveActiveMenuPath } = await import(moduleUrl)

const items = [
  {
    path: '/km',
    title: '知识管理',
    children: [
      { path: '/km/bases', title: '知识库管理' },
      { path: '/km/documents', title: '文档管理' },
      { path: '/km/search', title: '知识检索' },
    ],
  },
  {
    path: '/reports',
    title: '报告生成',
    children: [
      { path: '/reports', title: '报告记录' },
      { path: '/reports/new', title: '新建报告' },
    ],
  },
]

assert.equal(resolveActiveMenuPath('/km/documents/12', items), '/km/documents')
assert.equal(resolveActiveMenuPath('/km/bases/12', items), '/km/bases')
assert.equal(resolveActiveMenuPath('/reports/new', items), '/reports/new')
assert.equal(resolveActiveMenuPath('/reports/42', items), '/reports')
assert.equal(resolveActiveMenuPath('/admin/users', items), '/admin/users')

console.log('side nav helpers ok')
