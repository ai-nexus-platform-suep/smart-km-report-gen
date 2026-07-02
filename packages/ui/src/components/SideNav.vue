<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { getStoredUser } from '@platform/core'
import type { UserInfo } from '@platform/core/types'
import type { Component } from 'vue'
import { resolveActiveMenuPath } from './side-nav.helpers'

export interface NavItem {
  path: string
  title: string
  icon?: Component
  admin?: boolean
  children?: NavItem[]
}

const props = defineProps<{ items: NavItem[]; collapsed: boolean }>()
const route = useRoute()

const user = computed(() => getStoredUser<UserInfo>())
const isAdmin = computed(() => user.value?.role === 'ADMIN' || user.value?.role === 'SUPER_ADMIN')

const visibleItems = computed(() =>
  props.items
    .map((item) => ({
      ...item,
      children: item.children?.filter((child) => !child.admin || isAdmin.value),
    }))
    .filter((item) => {
      if (item.admin && !isAdmin.value) return false
      if (item.children) return item.children.length > 0
      return true
    }),
)

const activeMenu = computed(() => {
  return resolveActiveMenuPath(route.path, visibleItems.value)
})
</script>

<template>
  <div class="side-nav">
    <div v-show="!collapsed" class="nav-section-label">工作台导航</div>
    <el-menu
      :default-active="activeMenu"
      :collapse="collapsed"
      router
      background-color="transparent"
      text-color="var(--sidebar-text-color)"
      active-text-color="#ffffff"
      class="nav-menu"
    >
      <template v-for="item in visibleItems" :key="item.path">
        <!-- 有子菜单 -->
        <el-sub-menu v-if="item.children?.length" :index="item.path">
          <template #title>
            <el-icon v-if="item.icon" class="menu-icon"><component :is="item.icon" /></el-icon>
            <span>{{ item.title }}</span>
          </template>
          <el-menu-item
            v-for="ch in item.children"
            :key="ch.path"
            :index="ch.path"
            class="sub-item submenu-rail"
          >
            <span>{{ ch.title }}</span>
          </el-menu-item>
        </el-sub-menu>

        <!-- 叶子菜单 -->
        <el-menu-item v-else :index="item.path">
          <el-icon v-if="item.icon" class="menu-icon"><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
      </template>
    </el-menu>
  </div>
</template>

<style scoped>
.side-nav {
  --sidebar-text-color: var(--sidebar-text);
  display: flex;
  flex-direction: column;
  height: 100%;
  background: transparent;
}

.nav-section-label {
  padding: 16px 18px 2px;
  color: rgba(148, 163, 184, 0.66);
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.nav-menu {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 8px 10px 18px;
  border-right: 0;
}

.nav-menu :deep(.el-menu-item) {
  position: relative;
  display: flex;
  align-items: center;
  height: 42px;
  line-height: 42px;
  margin: 4px 0;
  border: 1px solid transparent;
  border-radius: 10px;
  color: var(--sidebar-text-color);
  font-size: 15px;
  font-weight: 600;
  transition: all var(--transition-fast);
  padding-left: 14px !important;
}

.nav-menu :deep(.el-menu-item:hover) {
  border-color: rgba(148, 163, 184, 0.10);
  background: var(--sidebar-item-hover);
  color: #ffffff;
}

.nav-menu :deep(.el-menu-item.is-active) {
  border-color: rgba(96, 165, 250, 0.18);
  background: var(--sidebar-item-active) !important;
  box-shadow: inset 0 0 0 1px rgba(96, 165, 250, 0.06);
  color: #ffffff !important;
  font-weight: 700;
}

.nav-menu :deep(.el-menu-item.is-active::before) {
  position: absolute;
  left: 6px;
  top: 10px;
  bottom: 10px;
  width: 3px;
  border-radius: 999px;
  background: var(--sidebar-indicator);
  box-shadow: 0 0 14px rgba(96, 165, 250, 0.52);
  content: "";
}

.nav-menu :deep(.el-sub-menu__title) {
  position: relative;
  display: flex;
  align-items: center;
  height: 42px;
  line-height: 42px;
  margin: 4px 0;
  border: 1px solid transparent;
  border-radius: 10px;
  color: var(--sidebar-text-color);
  font-size: 15px;
  font-weight: 600;
  transition: all var(--transition-fast);
  padding-left: 14px !important;
  padding-right: 12px !important;
}

.nav-menu :deep(.el-sub-menu__title:hover) {
  border-color: rgba(148, 163, 184, 0.10);
  background: var(--sidebar-item-hover);
  color: #ffffff;
}

.nav-menu :deep(.el-sub-menu__icon-arrow) {
  right: 12px;
  color: rgba(203, 213, 225, 0.56);
  font-size: 13px;
}

.nav-menu :deep(.el-sub-menu.is-opened > .el-sub-menu__title) {
  color: #ffffff;
  background: rgba(148, 163, 184, 0.06);
}

.nav-menu :deep(.el-sub-menu__title .menu-icon) {
  color: currentColor;
}

.nav-menu :deep(.el-sub-menu .el-menu .el-menu-item) {
  height: 36px;
  line-height: 36px;
  margin: 2px 0;
  padding-left: 42px !important;
  border-radius: 9px;
  color: var(--sidebar-text-muted);
  font-size: 13px;
  font-weight: 600;
}

.nav-menu :deep(.el-sub-menu .el-menu .submenu-rail::after) {
  position: absolute;
  left: 28px;
  top: -4px;
  bottom: -4px;
  width: 1px;
  background: rgba(148, 163, 184, 0.17);
  content: "";
}

.nav-menu :deep(.el-sub-menu .el-menu .el-menu-item.is-active::before) {
  left: 27px;
  top: 12px;
  bottom: 12px;
  width: 2px;
  z-index: 1;
}

.nav-menu :deep(.el-sub-menu .el-menu .el-menu-item.is-active) {
  color: #ffffff !important;
  background: rgba(96, 165, 250, 0.13) !important;
}

.nav-menu :deep(.el-menu--collapse) {
  width: 64px;
}

.nav-menu :deep(.el-menu--collapse .el-menu-item) {
  padding: 0 !important;
  display: flex;
  align-items: center;
  justify-content: center;
}

.nav-menu :deep(.el-menu--collapse .el-sub-menu__title) {
  padding: 0 !important;
  display: flex;
  align-items: center;
  justify-content: center;
}

.menu-icon {
  position: relative;
  display: inline-grid;
  flex-shrink: 0;
  width: 26px;
  height: 26px;
  place-items: center;
  margin-right: 10px;
  border: 1px solid rgba(148, 163, 184, 0.12);
  border-radius: 8px;
  background: rgba(15, 23, 42, 0.34);
  color: currentColor;
  font-size: 16px;
  overflow: hidden;
}

.menu-icon::after {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, rgba(96, 165, 250, 0.22), transparent 55%);
  opacity: 0;
  content: "";
  transition: opacity var(--transition-fast);
}

.nav-menu :deep(.el-menu-item.is-active .menu-icon),
.nav-menu :deep(.el-sub-menu.is-opened > .el-sub-menu__title .menu-icon) {
  border-color: rgba(96, 165, 250, 0.30);
  background: rgba(37, 99, 235, 0.18);
  color: #ffffff;
}

.nav-menu :deep(.el-menu-item.is-active .menu-icon::after),
.nav-menu :deep(.el-sub-menu.is-opened > .el-sub-menu__title .menu-icon::after) {
  opacity: 1;
}

.nav-menu :deep(.el-menu--collapse .menu-icon) {
  margin-right: 0;
}

.nav-menu :deep(.el-menu--collapse .el-menu-item),
.nav-menu :deep(.el-menu--collapse .el-sub-menu__title) {
  width: 42px;
  height: 42px;
  margin: 5px auto;
  border-radius: 12px;
}

.nav-menu :deep(.el-menu--collapse .el-menu-item.is-active::before) {
  left: 3px;
}

.nav-menu::-webkit-scrollbar {
  width: 4px;
}

.nav-menu::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.12);
  border-radius: 2px;
}
</style>
