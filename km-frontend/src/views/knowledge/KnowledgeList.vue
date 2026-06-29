<template>
  <div class="knowledge-list-page">
    <div class="header-action">
      <el-input 
        v-model="searchQuery" 
        placeholder="搜索知识库名称..." 
        style="width: 300px" 
        clearable 
        @keyup.enter="fetchData"
      />
      <el-button type="primary" icon="Plus" @click="handleCreate">新建知识库</el-button>
    </div>

    <el-table 
      :data="tableData" 
      v-loading="loading" 
      border 
      stripe 
      style="width: 100%"
    >
      <el-table-column prop="name" label="知识库名称" min-width="180" />
      <el-table-column prop="type" label="类型" width="150" />
      <el-table-column prop="documentCount" label="文档数" width="120" align="center" />
      <el-table-column prop="creator" label="创建人" width="120" />
      <el-table-column label="操作" width="160" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="danger" size="small" @click="handleDelete(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-container">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="fetchData"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getKnowledgeBaseList, deleteKnowledgeBase } from '../api/knowledge'

// 接口定义
interface KnowledgeBase {
  id: number
  name: string
  documentCount: number
  creator: string
  type: string
}

const tableData = ref<KnowledgeBase[]>([])
const loading = ref(false)
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const searchQuery = ref('')

// 获取列表数据
const fetchData = async () => {
  loading.value = true
  try {
    const response = await getKnowledgeBaseList({
      page: currentPage.value,
      pageSize: pageSize.value,
      keyword: searchQuery.value
    }) as any
    
    // 适配后端返回结构
    if (response.data && response.data.code === 200) {
      tableData.value = response.data.data.records
      total.value = response.data.data.total
    }
  } catch (error) {
    ElMessage.error('获取列表失败')
  } finally {
    loading.value = false
  }
}

const handleDelete = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定删除吗？', '提示', { type: 'warning' })
    await deleteKnowledgeBase(id)
    ElMessage.success('删除成功')
    fetchData()
  } catch {}
}

const handleCreate = () => ElMessage.info('功能开发中')

onMounted(fetchData)
</script>

<style scoped>
/* 1. 页面最外层背景，使用项目的深色变量 */
.knowledge-list-page { 
  padding: 24px; 
  background-color: var(--bg-page, #0f0f12); 
  border-radius: 8px;
  min-height: calc(100vh - 100px);
  
  /* 核心修复：直接在局部强行篡改 Element Plus 的官方变量，让它们强行等于你们的深色变量 */
  --el-bg-color: var(--bg-container, #1a1a1f);
  --el-fill-color-blank: var(--bg-container, #1a1a1f);
  --el-text-color-primary: var(--text-primary, rgba(255, 255, 255, 0.85));
  --el-text-color-regular: var(--text-secondary, rgba(255, 255, 255, 0.55));
  --el-border-color-lighter: var(--border-color, #2a2a32);
  --el-border-color: var(--border-color, #2a2a32);
  --el-table-bg-color: var(--bg-container, #1a1a1f);
  --el-table-tr-bg-color: var(--bg-container, #1a1a1f);
  --el-table-header-bg-color: var(--bg-container, #1a1a1f);
  --el-table-border-color: var(--border-color, #2a2a32);
}

/* 2. 暴力兜底：防止任何子组件漏网，强制注入深色背景 */
:deep(.el-table),
:deep(.el-table__inner-wrapper),
:deep(.el-table__header-wrapper),
:deep(.el-table__body-wrapper),
:deep(.el-table tr),
:deep(.el-table td),
:deep(.el-table th),
:deep(.el-input__wrapper),
:deep(.el-input__inner),
:deep(.el-pagination button),
:deep(.el-pagination .el-pager li),
:deep(.el-pagination .el-input__inner) {
  background-color: var(--bg-container, #1a1a1f) !important;
  color: var(--text-primary, rgba(255, 255, 255, 0.85)) !important;
  border-color: var(--border-color, #2a2a32) !important;
}

/* 3. 分页器选中状态高亮 */
:deep(.el-pagination.is-background .el-pager li.is-active) {
  background-color: var(--color-primary, #155eef) !important;
  color: #ffffff !important;
  border: none !important;
}

/* 4. 分页器和输入框禁用透明度带来的泛白 */
:deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--border-color, #2a2a32) inset !important;
}

.header-action { display: flex; justify-content: space-between; margin-bottom: 20px; }
.pagination-container { margin-top: 20px; display: flex; justify-content: flex-end; }
</style>