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
          <span v-show="!collapsed" class="logo-text">技术监督平台</span>
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
          <span class="header-title">{{ pageTitle }}</span>
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
}

/* ======= 深色侧边栏 ======= */
.sidebar {
  background: var(--sidebar-bg);
  transition: width var(--transition-slow);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.sidebar-logo {
  display: flex;
  align-items: center;
  gap: var(--gap-sm);
  height: 56px;
  padding: 0 var(--gap-md);
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
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.logo-svg {
  width: 32px;
  height: 32px;
}

.logo-text {
  font-size: var(--font-lg);
  font-weight: 700;
  color: #ffffff;
  white-space: nowrap;
  letter-spacing: 0.3px;
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
  flex: 1;
  overflow: hidden;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--bg-container);
  border-bottom: 1px solid var(--border-color);
  padding: 0 var(--gap-lg);
  height: 56px;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--gap-md);
}

.collapse-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: var(--border-radius-sm);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.collapse-btn:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.header-title {
  font-size: var(--font-lg);
  font-weight: 600;
  color: var(--text-primary);
}

.main-content {
  background: var(--bg-page);
  padding: var(--gap-lg);
  overflow-y: auto;
  flex: 1;
}
</style>
