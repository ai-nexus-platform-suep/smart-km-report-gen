import assert from 'node:assert/strict'
import ts from 'typescript'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const here = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(join(here, 'navigation.helpers.ts'), 'utf8')
const output = ts.transpileModule(source, {
  compilerOptions: {
    module: ts.ModuleKind.ES2022,
    target: ts.ScriptTarget.ES2022,
  },
}).outputText

const moduleUrl = `data:text/javascript;base64,${Buffer.from(output).toString('base64')}`
const {
  buildDocumentListLocation,
  getKnowledgeBaseCreatePath,
  getKnowledgeBaseEditPath,
  getKnowledgeBaseListPath,
} = await import(moduleUrl)

assert.equal(getKnowledgeBaseListPath('/km/bases'), '/km/bases')
assert.equal(getKnowledgeBaseListPath('/km/documents'), '/km/bases')
assert.equal(getKnowledgeBaseListPath('/knowledge'), '/knowledge')
assert.equal(getKnowledgeBaseListPath('/documents/12'), '/documents')

assert.equal(getKnowledgeBaseCreatePath('/km/bases'), '/km/bases/create')
assert.equal(getKnowledgeBaseCreatePath('/knowledge'), '/knowledge/create')

assert.equal(getKnowledgeBaseEditPath('/km/bases', 12), '/km/bases/12')
assert.equal(getKnowledgeBaseEditPath('/knowledge', 12), '/knowledge/12')

assert.deepEqual(
  buildDocumentListLocation('/km/bases', 12, '技术监督标准库'),
  { path: '/km/documents/12', query: { name: '技术监督标准库' } },
)

assert.deepEqual(
  buildDocumentListLocation('/documents', 12, '技术监督标准库'),
  { path: '/documents/12', query: { name: '技术监督标准库' } },
)

assert.deepEqual(
  buildDocumentListLocation('/knowledge', 12, '技术监督标准库'),
  { path: '/knowledge/12/documents', query: { name: '技术监督标准库' } },
)

console.log('km navigation helpers ok')
