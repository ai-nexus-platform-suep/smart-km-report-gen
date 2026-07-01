<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { getStoredUser } from '@platform/core'
import type { UserInfo } from '@platform/core/types'
import type { Component } from 'vue'
import { UserFilled } from '@element-plus/icons-vue'

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
const isAdmin = computed(() => user.value?.role === 'ADMIN')

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
  const p = route.path
  if (p.startsWith('/admin')) return p
  return p
})
</script>

<template>
  <div class="side-nav">
    <!-- 导航菜单 -->
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
            class="sub-item"
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

    <!-- 底部区域 -->
    <div class="side-footer" :class="{ collapsed: collapsed }">
      <div class="user-badge">
        <el-avatar :size="28" :icon="UserFilled" />
        <span v-show="!collapsed" class="user-name">{{ user?.nickname || user?.username || '用户' }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.side-nav {
  --sidebar-text-color: rgba(255, 255, 255, 0.55);
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--sidebar-bg);
}

/* ======= 菜单样式覆盖 ======= */
.nav-menu {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: var(--gap-sm) 0;
}

/* 菜单项 */
.nav-menu :deep(.el-menu-item) {
  height: 44px;
  line-height: 44px;
  margin: 2px var(--gap-sm);
  border-radius: var(--border-radius-sm);
  color: var(--sidebar-text-color);
  font-size: var(--font-base);
  transition: all var(--transition-fast);
  padding-left: 16px !important;
}

.nav-menu :deep(.el-menu-item:hover) {
  background: var(--sidebar-item-hover);
  color: #ffffff;
}

.nav-menu :deep(.el-menu-item.is-active) {
  background: var(--sidebar-item-active) !important;
  color: #ffffff !important;
  font-weight: 500;
}

/* 子菜单标题 */
.nav-menu :deep(.el-sub-menu__title) {
  height: 44px;
  line-height: 44px;
  margin: 2px var(--gap-sm);
  border-radius: var(--border-radius-sm);
  color: var(--sidebar-text-color);
  font-size: var(--font-base);
  transition: all var(--transition-fast);
  padding-left: 16px !important;
}

.nav-menu :deep(.el-sub-menu__title:hover) {
  background: var(--sidebar-item-hover);
  color: #ffffff;
}

.nav-menu :deep(.el-sub-menu__title .el-icon) {
  color: inherit;
}

/* 子菜单内的菜单项缩进 */
.sub-item {
  padding-left: 52px !important;
}

.nav-menu :deep(.el-sub-menu .el-menu .el-menu-item) {
  padding-left: 52px !important;
}

/* 折叠状态 */
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

/* 图标 */
.menu-icon {
  margin-right: var(--gap-sm);
  font-size: var(--font-lg);
}

.nav-menu :deep(.el-menu--collapse .menu-icon) {
  margin-right: 0;
}

/* 滚动条 */
.nav-menu::-webkit-scrollbar {
  width: 4px;
}

.nav-menu::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.12);
  border-radius: 2px;
}

/* ======= 底部用户区 ======= */
.side-footer {
  flex-shrink: 0;
  border-top: 1px solid var(--sidebar-logo-border);
  padding: var(--gap-md);
}

.side-footer.collapsed {
  display: flex;
  justify-content: center;
  padding: var(--gap-md) var(--gap-xs);
}

.user-badge {
  display: flex;
  align-items: center;
  gap: var(--gap-sm);
}

.user-name {
  font-size: var(--font-sm);
  color: var(--sidebar-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
