<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { AppLayout } from '@platform/ui'
import type { NavItem } from '@platform/ui/src/components/SideNav.vue'
import { Collection, Search, Setting } from '@element-plus/icons-vue'

const route = useRoute()
const showLayout = computed(() => route.meta.requiresAuth !== false)

const navItems: NavItem[] = [
  { path: '/knowledge', title: '知识库管理', icon: Collection },
  { path: '/search', title: '知识检索', icon: Search },
  {
    path: '/admin',
    title: '管理后台',
    icon: Setting,
    admin: true,
    children: [
      { path: '/admin/km/dashboard', title: 'KM统计' },
      { path: '/admin/km/embed', title: '嵌入模型' },
      { path: '/admin/km/rerank', title: '重排序' },
      { path: '/admin/km/parser', title: '解析器' },
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
