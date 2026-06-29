<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppShell from './layouts/AppShell.vue'

const route = useRoute()
const showLayout = computed(() => route.meta.requiresAuth !== false)
</script>

<template>
  <AppShell v-if="showLayout" />
  <RouterView v-else />
</template>
