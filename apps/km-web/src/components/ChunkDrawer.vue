<template>
  <el-drawer
    v-model="visible"
    :title="'切片详情 - ' + docFilename"
    size="500px"
    direction="rtl"
  >
    <div v-loading="loading" class="chunk-drawer">
      <template v-if="!loading">
        <div class="chunk-stats">
          共 <strong>{{ total }}</strong> 个切片，当前显示第 {{ page }} 页
        </div>
        <div v-if="chunks.length === 0" class="chunk-empty">
          <el-empty description="暂无切片数据" />
        </div>
        <div v-for="chunk in chunks" :key="chunk.id" class="chunk-card">
          <div class="chunk-header">
            <span class="chunk-index">#{{ chunk.chunkIndex + 1 }}</span>
            <span v-if="chunk.chunkType" class="chunk-type">{{ chunk.chunkType }}</span>
            <span class="chunk-chars">{{ chunk.charCount }} 字符</span>
          </div>
          <div v-if="chunk.chapterPath" class="chunk-path">
            <el-icon :size="14"><FolderOpened /></el-icon>
            {{ chunk.chapterPath }}
          </div>
          <div class="chunk-content">{{ chunk.content }}</div>
        </div>
        <div class="chunk-pagination" v-if="total > pageSize">
          <el-pagination
            v-model:current-page="page"
            :page-size="pageSize"
            :total="total"
            layout="prev, pager, next"
            small
            background
            @current-change="loadChunks"
          />
        </div>
      </template>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { FolderOpened } from '@element-plus/icons-vue'
import { listChunks } from '../api/document'
import type { Chunk } from '@platform/core/types'

const props = withDefaults(defineProps<{
  modelValue: boolean
  kbId: number | string
  docId: string
  docFilename?: string
}>(), {
  docFilename: '',
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const visible = ref(false)
const loading = ref(false)
const chunks = ref<Chunk[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)

watch(() => props.modelValue, (val) => {
  visible.value = val
  if (val) {
    page.value = 1
    loadChunks()
  }
})

watch(visible, (val) => {
  emit('update:modelValue', val)
})

async function loadChunks() {
  loading.value = true
  try {
    const res = await listChunks(props.kbId, props.docId, { page: page.value, pageSize: pageSize.value })
    chunks.value = res.data.data.list
    total.value = res.data.data.total
  } catch {
    chunks.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.chunk-drawer {
  padding: 0 4px;
}
.chunk-stats {
  font-size: 13px;
  color: var(--el-text-color-secondary, #909399);
  margin-bottom: 16px;
}
.chunk-empty {
  margin-top: 40px;
}
.chunk-card {
  background: var(--el-fill-color-lighter, #f5f7fa);
  border-radius: 6px;
  padding: 12px;
  margin-bottom: 12px;
}
.chunk-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.chunk-index {
  font-weight: 600;
  font-size: 13px;
  color: var(--el-color-primary, #409eff);
}
.chunk-type {
  font-size: 11px;
  background: var(--el-color-primary-light-9, #ecf5ff);
  color: var(--el-color-primary, #409eff);
  padding: 0 6px;
  border-radius: 3px;
}
.chunk-chars {
  font-size: 11px;
  color: var(--el-text-color-secondary, #909399);
  margin-left: auto;
}
.chunk-path {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--el-color-warning, #e6a23c);
  margin-bottom: 8px;
}
.chunk-content {
  font-size: 13px;
  line-height: 1.7;
  color: var(--el-text-color-primary, #303133);
  white-space: pre-wrap;
  word-break: break-all;
}
.chunk-pagination {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>
