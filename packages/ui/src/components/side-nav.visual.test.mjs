import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))

const sideNav = readFileSync(join(__dirname, 'SideNav.vue'), 'utf8')
const appLayout = readFileSync(join(__dirname, 'AppLayout.vue'), 'utf8')
const tokens = readFileSync(join(__dirname, '../styles/tokens.css'), 'utf8')

assert.match(sideNav, /class="nav-section-label"/, 'sidebar should include a compact section label')
assert.match(sideNav, /class="[^"]*submenu-rail[^"]*"/, 'submenu should render with a subtle vertical rail')
assert.match(sideNav, /\.menu-icon::after/, 'menu icons should have an active-state accent layer')
assert.match(appLayout, /class="brand-kicker"/, 'brand block should use a small workspace label')
assert.match(tokens, /--sidebar-indicator:/, 'tokens should define a sidebar active indicator color')

console.log('side nav visual contract ok')
