<template>
  <div class="page">
    <div class="page-header">
      <h2>知识库管理</h2>
      <el-button type="primary" @click="handleCreate">
        <el-icon><Plus /></el-icon>新建知识库
      </el-button>
    </div>

    <div class="filter-bar">
      <el-select v-model="filterType" placeholder="文档类型" clearable style="width:150px" @change="fetchList">
        <el-option label="全部" value="" />
        <el-option label="规程规范" value="REGULATION" />
        <el-option label="技术报告论文" value="REPORT" />
        <el-option label="术语条目" value="TERM" />
        <el-option label="通用文档" value="GENERAL" />
      </el-select>
      <el-input
        v-model="searchKeyword"
        placeholder="搜索知识库名称..."
        clearable
        style="width:280px"
        @input="onSearchInput"
        @clear="fetchList"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
    </div>

    <div class="batch-bar" v-if="selectedIds.length > 0">
      <el-button type="danger" size="small" @click="handleBatchDelete">
        <el-icon><Delete /></el-icon> 批量删除（{{ selectedIds.length }}）
      </el-button>
    </div>

    <div v-if="loading" class="loading">
      <el-skeleton :rows="5" animated />
    </div>

    <el-table v-else :data="list" style="width: 100%" v-loading="loading" @selection-change="onSelectionChange">
      <el-table-column type="selection" width="50" />
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column label="名称" min-width="200">
        <template #default="{ row }">
          <el-link type="primary" :underline="false" @click="router.push(`/knowledge/${row.id}`)">
            {{ row.name }}
          </el-link>
        </template>
      </el-table-column>
      <el-table-column label="文档类型" width="120">
        <template #default="{ row }">
          {{ typeLabel(row.type) }}
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
      <el-table-column prop="documentCount" label="文档数" width="80" align="center" />
      <el-table-column prop="creator" label="创建人" width="100" />
      <el-table-column label="创建时间" width="180">
        <template #default="{ row }">
          {{ formatTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button text type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination" v-if="total > 0">
      <el-pagination
        v-model:current-page="page"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next, total"
        @current-change="fetchList"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Delete } from '@element-plus/icons-vue'
import { getKnowledgeBaseList, deleteKnowledgeBase, batchDeleteKnowledgeBase } from '../api/knowledge'

const router = useRouter()
const list = ref<any[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const filterType = ref('')
const searchKeyword = ref('')
const selectedIds = ref<number[]>([])
let searchTimer: ReturnType<typeof setTimeout> | null = null

function onSearchInput() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => { fetchList() }, 400)
}

function onSelectionChange(rows: any[]) {
  selectedIds.value = rows.map(r => r.id)
}

async function handleBatchDelete() {
  await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 个知识库吗？`, '批量删除', {
    type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消',
  })
  await batchDeleteKnowledgeBase(selectedIds.value)
  ElMessage.success(`成功删除 ${selectedIds.value.length} 个知识库`)
  selectedIds.value = []
  fetchList()
}

const handleCreate = () => router.push('/knowledge/create')
const handleEdit = (row: any) => router.push(`/knowledge/${row.id}`)

async function fetchList() {
  loading.value = true
  try {
    const params: any = { page: page.value, pageSize: pageSize.value }
    if (filterType.value) params.docType = filterType.value
    if (searchKeyword.value) params.keyword = searchKeyword.value
    const res = await getKnowledgeBaseList(params)
    list.value = res.data?.data?.records || []
    total.value = res.data?.data?.total || 0
  } catch {
    ElMessage.error('获取列表失败')
  } finally {
    loading.value = false
  }
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm(`确定删除知识库「${row.name}」吗？`, '提示')
  await deleteKnowledgeBase(row.id)
  ElMessage.success('删除成功')
  fetchList()
}

function typeLabel(type: string) {
  const map: Record<string, string> = {
    REGULATION: '规程规范', REPORT: '技术报告论文',
    TERM: '术语条目', GENERAL: '通用文档',
  }
  return map[type] || type
}

function formatTime(iso: string) {
  if (!iso) return '-'
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

onMounted(() => fetchList())
</script>

<style scoped>
.page { padding: 0; }
.filter-bar { display: flex; gap: 12px; margin-bottom: 16px; align-items: center; }
.batch-bar { margin-bottom: 12px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 600; margin: 0; }
.loading { padding: 40px; }
.pagination { display: flex; justify-content: flex-end; margin-top: 20px; }
</style>