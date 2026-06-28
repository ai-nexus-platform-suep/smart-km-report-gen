<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { AppLayout } from '@platform/ui'
import type { NavItem } from '@platform/ui/src/components/SideNav.vue'
import { ChatDotRound, ChatLineSquare, Setting } from '@element-plus/icons-vue'

const route = useRoute()
const showLayout = computed(() => route.meta.requiresAuth !== false)

const navItems: NavItem[] = [
  { path: '/chat', title: '智能问答', icon: ChatDotRound },
  { path: '/conversations', title: '会话列表', icon: ChatLineSquare },
  {
    path: '/admin',
    title: '管理后台',
    icon: Setting,
    admin: true,
    children: [
      { path: '/admin/qa/dashboard', title: '问答统计' },
      { path: '/admin/qa/config', title: '问答配置' },
      { path: '/admin/qa/retrieval-test', title: '检索测试' },
      { path: '/admin/qa/llm', title: 'LLM配置' },
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
