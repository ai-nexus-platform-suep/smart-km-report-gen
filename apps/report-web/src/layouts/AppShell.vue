<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <BrandLogo class="brand-mark" />
        <div>
          <strong>电力智能报告</strong>
          <span>REPORT SYSTEM</span>
        </div>
      </div>

      <nav class="nav">
        <router-link v-for="item in visibleNav" :key="item.path" :to="item.path" class="nav-item">
          <el-icon :size="18">
            <component :is="item.icon" />
          </el-icon>
          <span>{{ item.label }}</span>
        </router-link>
      </nav>
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
import { DocumentAdd, Files, Histogram, Collection, UploadFilled, Setting } from '@element-plus/icons-vue'
import BrandLogo from '../components/BrandLogo.vue'

const router = useRouter()
const user = computed(() => getStoredUser<UserInfo>())
const isAdmin = computed(() => user.value?.role === 'ADMIN')

const nav = [
  { path: '/reports', label: '报告记录', icon: Files, role: 'USER' },
  { path: '/reports/new', label: '新建报告', icon: DocumentAdd, role: 'USER' },
  { path: '/admin/dashboard', label: '趋势监控', icon: Histogram, role: 'ADMIN' },
  { path: '/admin/templates', label: '模板管理', icon: Collection, role: 'ADMIN' },
  { path: '/admin/materials', label: '素材管理', icon: UploadFilled, role: 'ADMIN' },
  { path: '/admin/llm-configs', label: '模型配置', icon: Setting, role: 'ADMIN' },
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
  grid-template-columns: 248px minmax(0, 1fr);
  min-height: 100vh;
}

.sidebar {
  position: sticky;
  top: 0;
  overflow: hidden;
  height: 100vh;
  padding: 20px 14px;
  color: var(--text-inverse);
  background:
    linear-gradient(140deg, rgba(30, 107, 255, 0.18), transparent 36%),
    radial-gradient(circle at 18px 18px, rgba(0, 184, 217, 0.24) 0 1px, transparent 1.6px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.045) 1px, transparent 1px),
    linear-gradient(rgba(255, 255, 255, 0.04) 1px, transparent 1px),
    var(--bg-terminal);
  background-size:
    auto,
    36px 36px,
    28px 28px,
    28px 28px,
    auto;
}

.sidebar::before {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(180deg, transparent, rgba(0, 184, 217, 0.16), transparent),
    linear-gradient(135deg, transparent 0 58%, rgba(30, 107, 255, 0.24) 58% 58.35%, transparent 58.35%);
  background-size:
    auto,
    180px 180px;
  animation: scanLine 6s var(--ease-standard) infinite;
  content: '';
  pointer-events: none;
}

.sidebar::after {
  position: absolute;
  right: 12px;
  bottom: 18px;
  width: 38px;
  height: 38px;
  border-right: 1px solid rgba(0, 184, 217, 0.44);
  border-bottom: 1px solid rgba(0, 184, 217, 0.44);
  content: '';
  pointer-events: none;
}

.sidebar > * {
  position: relative;
  z-index: 1;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 64px;
  padding: 0 8px 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.brand-mark {
  width: 38px;
  height: 38px;
  flex: 0 0 38px;
}

.brand strong,
.brand span {
  display: block;
}

.brand span {
  margin-top: 3px;
  color: rgba(248, 251, 255, 0.6);
  font-family: var(--font-display);
  font-size: 16px;
}

.nav {
  display: grid;
  gap: 4px;
  margin-top: 20px;
}

.nav-item {
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 42px;
  padding: 0 12px;
  border-left: 2px solid transparent;
  color: rgba(248, 251, 255, 0.72);
  transition:
    border-color 160ms var(--ease-standard),
    background 160ms var(--ease-standard),
    color 160ms var(--ease-standard);
}

.nav-item::after {
  position: absolute;
  inset: 0 auto 0 -40%;
  width: 36%;
  background: linear-gradient(90deg, transparent, rgba(0, 184, 217, 0.16), transparent);
  content: '';
  opacity: 0;
  transform: skewX(-18deg);
  transition:
    opacity 160ms var(--ease-standard),
    transform 240ms var(--ease-standard);
}

.nav-item:hover,
.nav-item.router-link-active {
  border-left-color: var(--accent-cyan);
  color: var(--text-inverse);
  background: rgba(255, 255, 255, 0.08);
}

.nav-item:hover::after,
.nav-item.router-link-active::after {
  opacity: 1;
  transform: translateX(420%) skewX(-18deg);
}

.nav-item :deep(.el-icon),
.nav-item span {
  position: relative;
  z-index: 1;
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
  height: 64px;
  padding: 0 24px;
  border-bottom: 1px solid var(--border-default);
  background: rgba(244, 246, 248, 0.92);
  backdrop-filter: blur(10px);
}

.topbar strong {
  display: block;
  margin-top: 2px;
}

.topbar-label {
  color: var(--accent-blue);
}

.user-area {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
