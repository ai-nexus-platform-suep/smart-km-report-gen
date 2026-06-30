<template>
  <el-tooltip v-if="showTooltip && status === 'FAILED'" :content="errorMsg || '处理失败'" placement="top">
    <el-tag :type="tagType" :closable="false" size="small" effect="dark">
      {{ label }}
    </el-tag>
  </el-tooltip>
  <el-tag v-else :type="tagType" :closable="false" size="small" effect="dark">
    {{ label }}
  </el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { DocumentStatus } from '@platform/core/types'

const props = withDefaults(defineProps<{
  status: DocumentStatus
  errorMsg?: string
  showTooltip?: boolean
}>(), {
  showTooltip: true,
})

const STATUS_MAP: Record<DocumentStatus, { label: string; type: 'primary' | 'warning' | 'success' | 'danger' | 'info' }> = {
  UPLOADED: { label: '已上传', type: 'info' },
  PARSING: { label: '解析中', type: 'warning' },
  CHUNKING: { label: '切片中', type: 'warning' },
  EMBEDDING: { label: '向量化中', type: 'warning' },
  READY: { label: '已完成', type: 'success' },
  FAILED: { label: '失败', type: 'danger' },
}

const tagType = computed(() => STATUS_MAP[props.status]?.type || 'info')
const label = computed(() => STATUS_MAP[props.status]?.label || props.status)
</script>
