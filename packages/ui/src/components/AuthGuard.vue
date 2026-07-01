<template>
  <slot v-if="hasAccess" />
  <div v-else class="guard-block">
    <el-result icon="warning" title="权限不足" sub-title="此页面需要管理员权限">
      <template #extra>
        <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { getStoredUser } from '@platform/core'
import type { UserInfo } from '@platform/core/types'

const props = defineProps<{ requireAdmin?: boolean }>()

const user = computed(() => getStoredUser<UserInfo>())
const hasAccess = computed(() => {
  if (!props.requireAdmin) return true
  return user.value?.role === 'ADMIN' || user.value?.role === 'SUPER_ADMIN'
})
</script>

<style scoped>
.guard-block { display: flex; align-items: center; justify-content: center; height: 100%; }
</style>
