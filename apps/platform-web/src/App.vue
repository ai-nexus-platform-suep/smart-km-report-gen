<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { AppLayout } from '@platform/ui'
import type { NavItem } from '@platform/ui/src/components/SideNav.vue'
import { platformNavItems } from './layout/nav'

const route = useRoute()
const showLayout = computed(() => route.meta.requiresAuth !== false)
const navItems: NavItem[] = platformNavItems
</script>

<template>
  <AppLayout v-if="showLayout" :nav-items="navItems">
    <RouterView />
  </AppLayout>
  <RouterView v-else />
</template>
