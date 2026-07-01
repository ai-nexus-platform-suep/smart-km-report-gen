<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="scan-line"></div>

      <div class="brand">
        <span class="brand-index">RG-SYS / 2026</span>
        <h1>电力智能<br />报告生成系统</h1>
        <p>INDUSTRIAL REPORT TERMINAL</p>
      </div>

      <nav class="nav">
        <router-link v-for="item in visibleNav" :key="item.path" :to="item.path" class="nav-button">
          <span class="nav-num">{{ item.code }}</span>
          <span class="nav-text">
            <strong>{{ item.label }}</strong>
            <span>{{ item.subLabel }}</span>
          </span>
        </router-link>
      </nav>

      <div class="side-status">
        <div class="side-status-title">
          <span class="status-dot pulse"></span>
          SYSTEM ONLINE
        </div>
        <p>当前为 C 组报告生成模块，支持报告记录、新建报告、趋势监控、模板管理、素材管理和模型配置。</p>
      </div>
    </aside>

    <main class="shell-main">
      <header class="topbar">
        <div>
          <span class="terminal-label topbar-label">INDUSTRIAL AI TERMINAL</span>
          <strong>报告生成系统控制台</strong>
        </div>
        <div class="user-area">
          <el-tag size="small" effect="plain" type="info">{{ roleText }}</el-tag>
          <span>{{ displayName }}</span>
          <el-button size="small" plain @click="handleLogout">登出</el-button>
        </div>
      </header>
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { clearToken, getStoredUser } from '@platform/core'
import type { UserInfo } from '@platform/core/types'

const router = useRouter()
const user = computed(() => getStoredUser<UserInfo>())
const isAdmin = computed(() => user.value?.role === 'ADMIN')

const nav = [
  { path: '/reports', label: '报告记录', subLabel: 'REPORTS', code: '01', role: 'USER' },
  { path: '/reports/new', label: '新建报告', subLabel: 'CREATE', code: '02', role: 'USER' },
  { path: '/admin/dashboard', label: '趋势监控', subLabel: 'MONITOR', code: '03', role: 'ADMIN' },
  { path: '/admin/templates', label: '模板管理', subLabel: 'TEMPLATE', code: '04', role: 'ADMIN' },
  { path: '/admin/assets', label: '素材管理', subLabel: 'ASSETS', code: '05', role: 'ADMIN' },
  { path: '/admin/llm-configs', label: '模型配置', subLabel: 'MODEL', code: '06', role: 'ADMIN' },
] as const

const visibleNav = computed(() => nav.filter((item) => item.role === 'USER' || isAdmin.value))
const roleText = computed(() => (isAdmin.value ? 'ADMIN' : 'USER'))
const displayName = computed(() => user.value?.nickname || user.value?.username || '用户')

function handleLogout() {
  clearToken()
  router.push('/login')
}
</script>

<style scoped>
.app-shell {
  display: grid;
  grid-template-columns: 272px minmax(0, 1fr);
  min-height: 100vh;
}

.sidebar {
  position: sticky;
  top: 0;
  height: 100vh;
  overflow: hidden;
  color: var(--text-inverse);
  background:
    linear-gradient(120deg, rgba(30, 107, 255, 0.2) 0 18%, transparent 18% 100%),
    linear-gradient(28deg, transparent 0 62%, rgba(0, 184, 217, 0.13) 62% 63%, transparent 63%),
    linear-gradient(90deg, rgba(255, 255, 255, 0.08) 0 1px, transparent 1px),
    linear-gradient(135deg, #10151b 0%, #171d25 48%, #0c1015 100%);
  background-size: auto, 180px 180px, 36px 36px, auto;
  border-right: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: none;
}

.sidebar::before {
  content: "";
  position: absolute;
  inset: 0;
  background:
    linear-gradient(rgba(255, 255, 255, 0.055) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.055) 1px, transparent 1px),
    linear-gradient(135deg, transparent 0 78%, rgba(255, 255, 255, 0.09) 78% 78.5%, transparent 78.5%);
  background-size: 24px 24px, 24px 24px, 160px 160px;
  pointer-events: none;
}

.scan-line {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  height: 120px;
  background: linear-gradient(180deg, transparent, rgba(0, 184, 217, 0.22), transparent);
  animation: scanLine 5.6s linear infinite;
  pointer-events: none;
}

.sidebar > * {
  position: relative;
  z-index: 1;
}

.brand {
  position: relative;
  padding: 8px 24px 18px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.brand-index {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 8px;
  color: #9eeaff;
  background: rgba(0, 184, 217, 0.14);
  border: 1px solid rgba(0, 184, 217, 0.42);
  font-family: "Cascadia Mono", Consolas, "DIN Alternate", monospace;
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0.12em;
}

.brand h1 {
  margin: 12px 0 8px;
  font-family: var(--font-display);
  font-size: 26px;
  line-height: 1.28;
  font-weight: 900;
  text-shadow: 0.6px 0 currentColor;
}

.brand p {
  margin: 0;
  color: rgba(248, 251, 255, 0.58);
  font-family: var(--font-display);
  font-size: 14px;
  font-weight: 900;
  letter-spacing: 0.08em;
}

.nav {
  position: relative;
  display: grid;
  gap: 12px;
  padding: 18px 14px;
}

.nav-button {
  position: relative;
  display: grid;
  grid-template-columns: 50px 1fr;
  align-items: center;
  width: 100%;
  min-height: 66px;
  color: rgba(248, 251, 255, 0.72);
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  text-align: left;
  transition:
    color 160ms var(--ease-standard),
    background 160ms var(--ease-standard),
    border-color 160ms var(--ease-standard);
}

.nav-button::before {
  content: "";
  position: absolute;
  left: -1px;
  top: 10px;
  bottom: 10px;
  width: 2px;
  background: transparent;
}

.nav-button:hover,
.nav-button.router-link-active {
  color: #fff;
  background: rgba(255, 255, 255, 0.065);
  border-color: rgba(255, 255, 255, 0.12);
}

.nav-button.router-link-active::before {
  background: var(--accent-cyan);
}

.nav-num {
  color: rgba(0, 184, 217, 0.82);
  font-family: "Cascadia Mono", Consolas, "DIN Alternate", monospace;
  font-size: 17px;
  font-weight: 900;
  text-align: center;
}

.nav-text strong {
  display: block;
  font-size: 18px;
  font-weight: 600;
}

.nav-text span {
  display: block;
  margin-top: 4px;
  color: rgba(248, 251, 255, 0.42);
  font-family: "Cascadia Mono", Consolas, "DIN Alternate", monospace;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
}

.side-status {
  position: absolute;
  left: 16px;
  right: 16px;
  bottom: 18px;
  padding: 14px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: var(--radius-md);
}

.side-status-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  color: rgba(248, 251, 255, 0.78);
  font-size: 12px;
  font-weight: 700;
}

.side-status .status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--accent-cyan);
}

.side-status .status-dot.pulse {
  animation: statusPulse 1.45s infinite;
}

.side-status p {
  margin: 0;
  color: rgba(248, 251, 255, 0.48);
  font-size: 13px;
  line-height: 1.6;
}

.shell-main {
  position: relative;
  min-width: 0;
}

.topbar {
  position: sticky;
  z-index: 4;
  top: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 68px;
  padding: 0 28px;
  border-bottom: 1px solid rgba(132, 151, 176, 0.28);
  background: rgba(247, 250, 254, 0.84);
  box-shadow: 0 10px 28px rgba(29, 35, 43, 0.06);
  backdrop-filter: blur(16px);
}

.topbar strong {
  display: block;
  margin-top: 3px;
  color: var(--text-primary);
}

.topbar-label {
  color: var(--accent-blue);
}

.user-area {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 7px 8px 7px 12px;
  border: 1px solid rgba(132, 151, 176, 0.26);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.62);
  box-shadow: 0 10px 24px rgba(29, 35, 43, 0.05);
}
</style>
