<template>
  <span class="status-badge">
    <span class="status-dot" :class="tone" />
    <span>{{ label }}</span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  status: string
  type?: 'report' | 'section'
}>()

const reportStatusLabels: Record<string, string> = {
  DRAFT: '草稿',
  OUTLINE_READY: '大纲就绪',
  CONTENT_GENERATING: '正文生成中',
  CONTENT_INCOMPLETE: '正文待补全',
  CONTENT_READY: '正文就绪',
  EXPORTED: '已导出',
  FAILED: '生成失败',
  DELETED: '已删除',
}

const sectionStatusLabels: Record<string, string> = {
  PENDING: '等待生成',
  GENERATING: '正在生成',
  GENERATED: 'AI 已生成',
  USER_EDITED: '用户已编辑',
  FAILED: '生成失败',
}

const label = computed(() => {
  const source = props.type === 'section' ? sectionStatusLabels : reportStatusLabels
  return source[props.status] || props.status
})

const tone = computed(() => {
  if (props.status === 'CONTENT_GENERATING' || props.status === 'GENERATING') return 'generating'
  if (props.status === 'CONTENT_READY' || props.status === 'EXPORTED' || props.status === 'GENERATED') return 'success'
  if (props.status === 'OUTLINE_READY' || props.status === 'CONTENT_INCOMPLETE' || props.status === 'USER_EDITED') return 'warning'
  if (props.status === 'FAILED') return 'danger'
  return 'blue'
})
</script>

<style scoped>
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: max-content;
  color: var(--text-secondary);
  font-size: 13px;
}
</style>
