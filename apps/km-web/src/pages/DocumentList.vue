<template>
  <div class="document-page">
    <!-- 页面标题 & 返回 -->
    <div class="page-header">
      <el-button text :icon="ArrowLeft" @click="$router.push('/knowledge')" class="back-btn">
        返回知识库
      </el-button>
      <h2>{{ kbName }} - 文档管理</h2>
    </div>

    <!-- 上传区域 -->
    <el-collapse v-model="activeCollapse" class="upload-section">
      <el-collapse-item name="upload">
        <template #title><div class="upload-title">上传文档</div></template>
        <DocUploader :kb-id="kbId" @success="onUploadSuccess" />
      </el-collapse-item>
    </el-collapse>

    <!-- 筛选 & 操作栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-select
          v-model="filters.status"
          placeholder="状态筛选"
          clearable
          size="default"
          style="width: 140px"
          @change="onFilterChange"
        >
          <el-option label="全部状态" value="" />
          <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-input
          v-model="filters.keyword"
          placeholder="搜索文件名..."
          clearable
          size="default"
          style="width: 220px"
          prefix-icon="Search"
          @input="onKeywordInput"
        />
      </div>
      <div class="toolbar-right">
        <el-button
          type="danger"
          plain
          size="default"
          :disabled="selectedIds.length === 0"
          @click="handleBatchDelete"
        >
          批量删除 ({{ selectedIds.length }})
        </el-button>
        <el-button type="primary" size="default" @click="refreshList" :loading="loading">
          刷新
        </el-button>
      </div>
    </div>

    <!-- 文档表格 -->
    <el-table
      :data="documents"
      v-loading="loading"
      stripe
      style="width: 100%"
      @selection-change="onSelectionChange"
      row-key="id"
    >
      <el-table-column type="selection" width="44" />
      <el-table-column prop="filename" label="文件名" min-width="260" show-overflow-tooltip>
        <template #default="{ row }">
          <div class="filename-cell">
            <el-icon :size="18" class="file-icon"><Document /></el-icon>
            <span>{{ row.fileName }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="fileSize" label="大小" width="100" align="right">
        <template #default="{ row }">
          {{ formatFileSize(row.fileSize) }}
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="120" align="center">
        <template #default="{ row }">
          <DocStatusBadge :status="row.status" :error-msg="row.errorMessage" :show-tooltip="row.status === 'FAILED'" />
        </template>
      </el-table-column>
      <el-table-column prop="errorMsg" label="失败原因" width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.status === 'FAILED' && row.errorMessage" class="error-text">
            <el-tooltip :content="row.errorMessage" placement="top">
              <el-icon :size="14" color="#f56c6c"><WarningFilled /></el-icon>
            </el-tooltip>
            <span class="error-msg">{{ row.errorMessage.length > 15 ? row.errorMessage.slice(0, 15) + '...' : row.errorMessage }}</span>
          </span>
          <span v-else class="no-error">--</span>
        </template>
      </el-table-column>
      <el-table-column prop="tags" label="标签" width="200">
        <template #default="{ row }">
          <TagEditor
            :tags="row.tags || {}"
            @update="(tags) => onTagsUpdate(row, tags)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="上传时间" width="170">
        <template #default="{ row }">
          {{ formatTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" link @click="viewChunks(row)">
            切片
          </el-button>
          <el-button
            v-if="row.status === 'FAILED'"
            size="small"
            type="warning"
            link
            :loading="retryingId === row.id"
            @click="handleRetry(row)"
          >
            重试
          </el-button>
          <el-popconfirm
            title="确认删除该文档？"
            confirm-button-text="删除"
            cancel-button-text="取消"
            @confirm="handleDelete(row)"
          >
            <template #reference>
              <el-button size="small" type="danger" link>删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-wrap" v-if="total > 0">
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @size-change="onPageChange"
        @current-change="onPageChange"
      />
    </div>

    <!-- 切片抽屉 -->
    <ChunkDrawer
      v-model="chunkDrawerVisible"
      :kb-id="kbId"
      :doc-id="currentDocId"
      :doc-filename="currentDocFilename"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Document, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listDocuments, deleteDocument, batchDeleteDocuments, retryProcessDocument, updateDocumentTags } from '../api/document'
import type { Document as DocType } from '@platform/core/types'
import DocUploader from '../components/DocUploader.vue'
import DocStatusBadge from '../components/DocStatusBadge.vue'
import TagEditor from '../components/TagEditor.vue'
import ChunkDrawer from '../components/ChunkDrawer.vue'

const route = useRoute()
const router = useRouter()
const kbId = computed(() => String(route.params.kbId))
const kbName = computed(() => String(route.query.name || '知识库'))

// Status options for filter
const statusOptions = [
  { label: '已上传', value: 'UPLOADED' },
  { label: '解析中', value: 'PARSING' },
  { label: '切片中', value: 'CHUNKING' },
  { label: '向量化中', value: 'EMBEDDING' },
  { label: '已完成', value: 'READY' },
  { label: '失败', value: 'FAILED' },
]

// Upload
const activeCollapse = ref<string[]>([])

// Filters
const filters = reactive({ status: '', keyword: '' })
let keywordTimer: ReturnType<typeof setTimeout> | null = null
function onKeywordInput() {
  if (keywordTimer) clearTimeout(keywordTimer)
  keywordTimer = setTimeout(() => onFilterChange(), 400)
}

// Table
const documents = ref<DocType[]>([])
const loading = ref(false)
const selectedIds = ref<string[]>([])
const total = ref(0)
const pagination = reactive({ page: 1, pageSize: 20 })

// Retry
const retryingId = ref<string | null>(null)

// Chunk drawer
const chunkDrawerVisible = ref(false)
const currentDocId = ref('')
const currentDocFilename = ref('')

// Polling interval
let pollTimer: ReturnType<typeof setInterval> | null = null
const POLL_INTERVAL = 5000 // 5 seconds

onMounted(async () => {
  await fetchDocuments()
  startPolling()
})

onUnmounted(() => {
  stopPolling()
})

function startPolling() {
  stopPolling()
  pollTimer = setInterval(async () => {
    // Don't poll if user has selected items (would clear checkbox selection)
    if (selectedIds.value.length > 0) return
    // Only poll if there are documents in non-terminal status
    const hasProcessing = documents.value.some(d =>
      ['UPLOADED', 'PARSING', 'CHUNKING', 'EMBEDDING'].includes(d.status)
    )
    if (hasProcessing) {
      await fetchDocuments(true)
    }
  }, POLL_INTERVAL)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function fetchDocuments(silent = false) {
  if (!silent) loading.value = true
  try {
    const res = await listDocuments(kbId.value, {
      page: pagination.page,
      pageSize: pagination.pageSize,
      status: filters.status || undefined,
      keyword: filters.keyword || undefined,
    })
    documents.value = (res.data.data.records || res.data.data.list || []).map(d => ({ ...d, tags: typeof d.tags === 'object' && !Array.isArray(d.tags) ? d.tags : {} }))
    total.value = res.data.data.total || 0
  } catch (e: any) {
    if (!silent) ElMessage.error('获取文档列表失败：' + (e?.response?.data?.message || e.message))
  } finally {
    if (!silent) loading.value = false
  }
}

function refreshList() {
  pagination.page = 1
  fetchDocuments()
}

function onFilterChange() {
  pagination.page = 1
  fetchDocuments()
}

function onPageChange() {
  fetchDocuments()
}

function onSelectionChange(selection: DocType[]) {
  selectedIds.value = selection.map(s => s.id)
}

function onUploadSuccess() {
  activeCollapse.value = []
  fetchDocuments()
}

// Delete
async function handleDelete(row: DocType) {
  try {
    const res = await deleteDocument(kbId.value, row.id)
    ElMessage.success('删除成功')
    fetchDocuments()
  } catch (e: any) {
    ElMessage.error('删除失败：' + (e?.response?.data?.message || e.message))
  }
}

async function handleBatchDelete() {
  if (selectedIds.value.length === 0) return
  try {
    await ElMessageBox.confirm(
      `确认删除选中的 ${selectedIds.value.length} 个文档？删除后不可恢复。`,
      '批量删除确认',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' },
    )
    const res = await batchDeleteDocuments(kbId.value, selectedIds.value)
    ElMessage.success(`成功删除 ${res.data.data.deletedIds.length} 个文档`)
    selectedIds.value = []
    fetchDocuments()
  } catch (e: any) {
    if (e?.message !== 'cancel') {
      ElMessage.error('批量删除失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
    }
  }
}

// Retry
async function handleRetry(row: DocType) {
  retryingId.value = row.id
  try {
    await retryProcessDocument(kbId.value, row.id)
    ElMessage.success('已重新加入处理队列')
    row.status = 'PARSING'
    row.errorMessage = ''
  } catch (e: any) {
    ElMessage.error('重试失败：' + (e?.response?.data?.message || e.message))
  } finally {
    retryingId.value = null
  }
}

// Tags
async function onTagsUpdate(row: DocType, tags: Record<string, string>) {
  try {
    await updateDocumentTags(kbId.value, row.id, tags)
    row.tags = tags
    ElMessage.success('标签更新成功')
  } catch (e: any) {
    ElMessage.error('标签更新失败：' + (e?.response?.data?.message || e.message))
  }
}

// Chunks
function viewChunks(row: DocType) {
  currentDocId.value = String(row.id)
  currentDocFilename.value = row.fileName || row.filename || ''
  chunkDrawerVisible.value = true
}

// Formatting
function formatFileSize(bytes: number): string {
  if (bytes >= 1024 * 1024 * 1024) return (bytes / (1024 * 1024 * 1024)).toFixed(1) + ' GB'
  if (bytes >= 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(0) + ' MB'
  if (bytes >= 1024) return (bytes / 1024).toFixed(0) + ' KB'
  return bytes + ' B'
}

function formatTime(iso: string): string {
  if (!iso) return '--'
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<style scoped>
.document-page {
  max-width: 1400px;
  margin: 0 auto;
}
.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}
.page-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}
.back-btn {
  font-size: 13px;
}
.upload-section {
  margin-bottom: 16px;
}
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}
.toolbar-left {
  display: flex;
  gap: 8px;
  align-items: center;
}
.toolbar-right {
  display: flex;
  gap: 8px;
}
.filename-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}
.file-icon {
  color: var(--el-color-primary, #409eff);
  flex-shrink: 0;
}
.error-text {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.error-msg {
  font-size: 12px;
  color: #f56c6c;
}
.no-error {
  color: var(--el-text-color-placeholder, #c0c4cc);
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  padding: 12px 0;
}
.upload-title { text-align: center; width: 100%; font-weight: 600; font-size: 15px; }

/* 深色模式表格样式 */


[data-theme='dark'] .el-table {
  --el-table-bg-color: var(--bg-container);
  --el-table-tr-bg-color: var(--bg-container);
  --el-table-header-bg-color: var(--bg-hover);
  --el-table-row-hover-bg-color: var(--bg-hover);
  --el-table-border-color: var(--border-color);
  --el-table-text-color: var(--text-primary);
  --el-table-header-text-color: var(--text-secondary);
}
[data-theme='dark'] .el-table--striped .el-table__body tr.el-table__row--striped td {
  background: var(--bg-hover);
}
[data-theme='dark'] .el-card {
  background: var(--bg-container);
  border-color: var(--border-color);
}
[data-theme='dark'] .document-page {
  color: var(--text-primary);
}
[data-theme='dark'] .doc-uploader {
  background: var(--bg-hover);
}

</style>
