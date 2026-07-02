import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const here = dirname(fileURLToPath(import.meta.url))
const routerSource = readFileSync(join(here, 'index.ts'), 'utf8')
const appSource = readFileSync(join(here, '..', 'App.vue'), 'utf8')

assert.match(appSource, /path:\s*'\/documents'[\s\S]*title:\s*'文档管理'/)
assert.match(routerSource, /path:\s*'\/documents'[\s\S]*meta:\s*\{\s*title:\s*'文档管理'[\s\S]*documentEntry:\s*true/)
assert.match(routerSource, /path:\s*'\/documents\/:kbId'[\s\S]*component:\s*\(\)\s*=>\s*import\('\.\.\/pages\/DocumentList\.vue'\)/)

console.log('km routes ok')
