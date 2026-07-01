<template>
  <el-container class="layout">
    <!-- 深色侧边栏 -->
    <el-aside :width="collapsed ? 'var(--sidebar-collapsed-width)' : 'var(--sidebar-width)'" class="sidebar">
      <!-- Logo 区域 -->
      <div class="sidebar-logo" :class="{ collapsed }">
        <div class="logo-icon-wrap">
          <svg viewBox="0 0 32 32" fill="none" class="logo-svg">
            <rect width="32" height="32" rx="8" fill="var(--color-primary)"/>
            <path d="M9 16L14 21L23 11" stroke="white" stroke-width="2.5"
                  stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
        <transition name="fade">
          <span v-show="!collapsed" class="logo-text">
            <strong>技术监督平台</strong>
            <small>AI Nexus</small>
          </span>
        </transition>
      </div>

      <!-- 导航菜单 -->
      <SideNav :items="navItems" :collapsed="collapsed" />
    </el-aside>

    <!-- 右侧内容区 -->
    <el-container class="main-area">
      <el-header class="header">
        <div class="header-left">
          <button class="collapse-btn" @click="collapsed = !collapsed">
            <svg viewBox="0 0 20 20" fill="currentColor" width="20" height="20">
              <path v-if="!collapsed" d="M4 4.5h12M4 10h12M4 15.5h12" stroke="currentColor"
                    stroke-width="1.8" stroke-linecap="round" fill="none"/>
              <path v-else d="M4 4.5h12M4 10h12M4 15.5h12" stroke="currentColor"
                    stroke-width="1.8" stroke-linecap="round" fill="none"/>
            </svg>
          </button>
          <span class="header-title-group">
            <small>Unified Platform</small>
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
  background: var(--sidebar-bg);
}

/* ======= 深色侧边栏 ======= */
.sidebar {
  position: relative;
  z-index: 3;
  background:
    radial-gradient(circle at 18% 8%, rgba(21, 94, 239, 0.3), transparent 34%),
    linear-gradient(180deg, #101828 0%, #17191f 46%, #10131a 100%);
  transition: width var(--transition-slow);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  border-right: 1px solid rgba(255, 255, 255, 0.06);
  box-shadow: 18px 0 48px rgba(15, 23, 42, 0.14);
}

.sidebar::after {
  position: absolute;
  inset: auto 18px 16px 18px;
  height: 120px;
  border-radius: 999px;
  background: rgba(21, 94, 239, 0.13);
  filter: blur(42px);
  content: "";
  pointer-events: none;
}

.sidebar-logo {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  height: 72px;
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
  flex-shrink: 0;
  width: 38px;
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.08);
  box-shadow: 0 10px 24px rgba(21, 94, 239, 0.22);
}

.logo-svg {
  width: 34px;
  height: 34px;
}

.logo-text {
  display: grid;
  gap: 2px;
  line-height: 1.15;
  white-space: nowrap;
}

.logo-text strong {
  font-size: var(--font-lg);
  font-weight: 700;
  color: #ffffff;
  letter-spacing: 0.3px;
}

.logo-text small {
  color: rgba(255, 255, 255, 0.42);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.16em;
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

/* ======= 主内容区 ======= */
.main-area {
  position: relative;
  flex: 1;
  overflow: hidden;
  background:
    radial-gradient(circle at 18% -8%, rgba(21, 94, 239, 0.13), transparent 28%),
    radial-gradient(circle at 90% 10%, rgba(0, 184, 217, 0.12), transparent 30%),
    linear-gradient(180deg, #f8fbff 0%, var(--bg-page) 42%, #eef3f8 100%);
}

.main-area::before {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(15, 23, 42, 0.035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(15, 23, 42, 0.035) 1px, transparent 1px);
  background-size: 28px 28px;
  mask-image: linear-gradient(to bottom, rgba(0, 0, 0, 0.55), transparent 58%);
  content: "";
  pointer-events: none;
}

.header {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.72);
  border-bottom: 1px solid rgba(148, 163, 184, 0.2);
  padding: 0 26px;
  height: 68px;
  flex-shrink: 0;
  backdrop-filter: blur(18px);
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
  width: 38px;
  height: 38px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 12px;
  color: var(--text-secondary);
  background: rgba(255, 255, 255, 0.72);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.collapse-btn:hover {
  background: #ffffff;
  color: var(--text-primary);
  box-shadow: var(--shadow-sm);
}

.header-title-group {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.header-title-group small {
  color: var(--text-tertiary);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.14em;
  line-height: 1;
  text-transform: uppercase;
}

.header-title {
  color: var(--text-primary);
  font-size: 18px;
  font-weight: 800;
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.main-content {
  position: relative;
  z-index: 1;
  background: transparent;
  padding: 24px;
  overflow-y: auto;
  flex: 1;
}

.main-content :deep(.page) {
  max-width: 1480px;
  margin: 0 auto;
}

[data-theme='dark'] .main-area {
  background:
    radial-gradient(circle at 18% -8%, rgba(80, 132, 255, 0.16), transparent 30%),
    radial-gradient(circle at 90% 10%, rgba(34, 211, 238, 0.1), transparent 30%),
    linear-gradient(180deg, #111827 0%, var(--bg-page) 54%, #0b1020 100%);
}

[data-theme='dark'] .header {
  background: rgba(17, 24, 39, 0.72);
  border-bottom-color: rgba(148, 163, 184, 0.16);
}

[data-theme='dark'] .collapse-btn {
  background: rgba(15, 23, 42, 0.72);
}

@media (max-width: 760px) {
  .header {
    height: 62px;
    padding: 0 14px;
  }

  .header-title-group small {
    display: none;
  }

  .main-content {
    padding: 14px;
  }
}
</style>
