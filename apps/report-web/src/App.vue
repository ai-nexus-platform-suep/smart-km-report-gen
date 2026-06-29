<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { AppLayout } from '@platform/ui'
import type { NavItem } from '@platform/ui/src/components/SideNav.vue'
import { DocumentAdd, Clock, Setting } from '@element-plus/icons-vue'

const route = useRoute()
const showLayout = computed(() => route.meta.requiresAuth !== false)

const navItems: NavItem[] = [
  { path: '/report', title: '新建报告', icon: DocumentAdd },
  { path: '/report/history', title: '历史记录', icon: Clock },
  {
    path: '/admin',
    title: '管理后台',
    icon: Setting,
    admin: true,
    children: [
      { path: '/admin/report/dashboard', title: '报告统计' },
      { path: '/admin/report/template', title: '模板管理' },
      { path: '/admin/report/material', title: '素材管理' },
      { path: '/admin/report/llm', title: 'LLM配置' },
      { path: '/admin/report/docx', title: '样式配置' },
    ],
  },
]
</script>

<template>
  <AppLayout v-if="showLayout" :nav-items="navItems">
    <RouterView />
  </AppLayout>
  <RouterView v-else />
</template>
