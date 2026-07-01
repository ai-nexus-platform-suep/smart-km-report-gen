<template>
  <el-container class="layout">
    <el-aside :width="collapsed ? 'var(--sidebar-collapsed-width)' : 'var(--sidebar-width)'" class="sidebar">
      <div class="sidebar-logo" :class="{ collapsed }">
        <div class="logo-icon-wrap">监</div>
        <transition name="fade">
          <span v-show="!collapsed" class="logo-text">
            <small class="brand-kicker">Workspace</small>
            <strong>技术监督平台</strong>
          </span>
        </transition>
      </div>

      <SideNav :items="navItems" :collapsed="collapsed" />
    </el-aside>

    <el-container class="main-area">
      <el-header class="header">
        <div class="header-left">
          <button class="collapse-btn" :title="collapsed ? '展开导航' : '收起导航'" @click="collapsed = !collapsed">
            <el-icon><Fold v-if="!collapsed" /><Expand v-else /></el-icon>
          </button>
          <span class="header-title-group">
            <strong class="header-title">{{ pageTitle }}</strong>
          </span>
        </div>
        <HeaderBar />
      </el-header>

      <el-main class="main-content">
        <slot />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { Expand, Fold } from '@element-plus/icons-vue'
import SideNav from './SideNav.vue'
import HeaderBar from './HeaderBar.vue'
import type { NavItem } from './SideNav.vue'

defineProps<{ navItems: NavItem[] }>()

const route = useRoute()
const collapsed = ref(false)
const pageTitle = computed(() => (route.meta.title as string) || '技术监督辅助平台')
</script>

<style scoped>
.layout {
  height: 100vh;
  overflow: hidden;
  background: var(--bg-page);
}

.sidebar {
  position: relative;
  z-index: 3;
  background:
    radial-gradient(circle at 22% 0%, rgba(59, 130, 246, 0.18), transparent 27%),
    linear-gradient(180deg, #0d1726 0%, var(--sidebar-bg) 58%, #060c16 100%);
  transition: width var(--transition-slow);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  border-right: 1px solid var(--sidebar-border);
  box-shadow: 8px 0 22px rgba(15, 23, 42, 0.08);
}

.sidebar::after {
  position: absolute;
  inset: 0 0 auto;
  height: 1px;
  background: linear-gradient(90deg, rgba(96, 165, 250, 0.44), transparent 72%);
  content: "";
  pointer-events: none;
}

.sidebar-logo {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 11px;
  height: 74px;
  padding: 0 18px;
  border-bottom: 1px solid var(--sidebar-logo-border);
  flex-shrink: 0;
  cursor: pointer;
  transition: padding var(--transition-slow);
}

.sidebar-logo.collapsed {
  padding: 0;
  justify-content: center;
}

.logo-icon-wrap {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 42px;
  height: 42px;
  border: 1px solid rgba(147, 197, 253, 0.28);
  border-radius: 11px;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.18), transparent 46%),
    linear-gradient(135deg, #1d4ed8 0%, #2563eb 100%);
  color: #ffffff;
  font-size: 20px;
  font-weight: 800;
  box-shadow: 0 10px 20px rgba(37, 99, 235, 0.26);
}

.logo-text {
  display: grid;
  gap: 3px;
  line-height: 1.15;
  white-space: nowrap;
  min-width: 0;
}

.logo-text strong {
  font-size: 18px;
  font-weight: 800;
  color: #ffffff;
  letter-spacing: 0;
}

.brand-kicker {
  color: rgba(147, 197, 253, 0.78);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.main-area {
  position: relative;
  flex: 1;
  overflow: hidden;
  background: var(--bg-page);
}

.header {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: color-mix(in srgb, var(--bg-container) 92%, transparent);
  border-bottom: 1px solid var(--platform-border);
  padding: 0 22px;
  height: 58px;
  flex-shrink: 0;
  backdrop-filter: blur(14px);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.collapse-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 1px solid var(--platform-border);
  border-radius: 8px;
  color: var(--text-secondary);
  background: rgba(255, 255, 255, 0.74);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.collapse-btn:hover {
  background: #ffffff;
  color: var(--text-primary);
  box-shadow: var(--shadow-sm);
}

.header-title {
  color: var(--text-primary);
  font-size: 17px;
  font-weight: 700;
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.main-content {
  position: relative;
  z-index: 1;
  background: transparent;
  padding: 22px;
  overflow-y: auto;
  flex: 1;
}

.main-content :deep(.page) {
  max-width: 1480px;
  margin: 0 auto;
}

[data-theme='dark'] .main-area {
  background: var(--bg-page);
}

[data-theme='dark'] .header {
  background: rgba(17, 24, 39, 0.86);
  border-bottom-color: rgba(148, 163, 184, 0.18);
}

[data-theme='dark'] .collapse-btn {
  background: rgba(15, 23, 42, 0.72);
  border-color: rgba(148, 163, 184, 0.2);
}

@media (max-width: 760px) {
  .header {
    height: 62px;
    padding: 0 14px;
  }
  .main-content {
    padding: 14px;
  }
}
</style>
