<template>
  <div class="knowledge-page">
    <div class="page-header">
      <h2>知识库管理</h2>
      <el-button type="primary" :icon="Plus" @click="showCreate = true">新建知识库</el-button>
    </div>

    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索知识库名称..."
        clearable
        style="width: 280px"
        prefix-icon="Search"
        @input="onKeywordInput"
      />
    </div>

    <el-table :data="knowledgeBases" v-loading="loading" stripe style="width: 100%" @row-click="goToDocuments">
      <el-table-column prop="name" label="知识库名称" min-width="180">
        <template #default="{ row }">
          <span class="kb-name">{{ row.name }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="280" show-overflow-tooltip />
      <el-table-column prop="type" label="类型" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="typeTagType(row.type)" size="small">{{ typeLabel(row.type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="documentCount" label="文档数" width="90" align="center" />
      <el-table-column prop="creator" label="创建者" width="100" align="center" />
      <el-table-column label="操作" width="120" align="center" @click.stop>
        <template #default="{ row }">
          <el-button size="small" type="primary" link @click.stop="goToDocuments(row)">
            管理文档
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Create Dialog -->
    <el-dialog v-model="showCreate" title="新建知识库" width="500px">
      <el-form :model="createForm" label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="createForm.name" placeholder="知识库名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="createForm.description" type="textarea" :rows="3" placeholder="知识库描述" />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="createForm.type" placeholder="选择类型" style="width: 100%">
            <el-option label="规程规范" value="REGULATION" />
            <el-option label="技术报告论文" value="REPORT" />
            <el-option label="术语条目" value="TERM" />
            <el-option label="通用文档" value="GENERAL" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="handleCreate" :loading="creating">确定创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { apiGet, apiPost } from '@platform/core/utils/request'
import { API_KM } from '@platform/core/constants'
import type { KnowledgeBase } from '@platform/core/types'

const router = useRouter()

const loading = ref(false)
const knowledgeBases = ref<KnowledgeBase[]>([])
const keyword = ref('')
let keywordTimer: ReturnType<typeof setTimeout> | null = null

const showCreate = ref(false)
const creating = ref(false)
const createForm = reactive({ name: '', description: '', type: 'GENERAL' })

const TYPE_MAP: Record<string, { label: string; type: 'primary' | 'success' | 'warning' | 'info' }> = {
  REGULATION: { label: '规程规范', type: 'primary' },
  REPORT: { label: '技术报告', type: 'success' },
  TERM: { label: '术语条目', type: 'warning' },
  GENERAL: { label: '通用文档', type: 'info' },
}

function typeLabel(type: string) { return TYPE_MAP[type]?.label || type }
function typeTagType(type: string) { return TYPE_MAP[type]?.type || 'info' }

onMounted(() => fetchList())

function onKeywordInput() {
  if (keywordTimer) clearTimeout(keywordTimer)
  keywordTimer = setTimeout(() => fetchList(), 400)
}

async function fetchList() {
  loading.value = true
  try {
    const res = await apiGet<{ code: number; message: string; data: { records: KnowledgeBase[] } }>(
      API_KM.KB.LIST,
      keyword.value ? { keyword: keyword.value } : undefined,
    )
    knowledgeBases.value = res.data.data.records || []
  } catch (e: any) {
    ElMessage.error('获取知识库列表失败')
  } finally {
    loading.value = false
  }
}

function goToDocuments(kb: KnowledgeBase) {
  router.push({ name: 'DocumentList', params: { kbId: kb.id }, query: { name: kb.name } })
}

async function handleCreate() {
  if (!createForm.name) {
    ElMessage.warning('请输入知识库名称')
    return
  }
  creating.value = true
  try {
    await apiPost(API_KM.KB.CREATE, createForm)
    ElMessage.success('创建成功')
    showCreate.value = false
    createForm.name = ''
    createForm.description = ''
    createForm.type = 'GENERAL'
    fetchList()
  } catch (e: any) {
    ElMessage.error('创建失败：' + (e?.response?.data?.message || e.message))
  } finally {
    creating.value = false
  }
}
</script>

<style scoped>
.knowledge-page {
  max-width: 1200px;
  margin: 0 auto;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.page-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}
.toolbar {
  margin-bottom: 16px;
}
.kb-name {
  font-weight: 500;
  color: var(--el-color-primary, #409eff);
  cursor: pointer;
}
.kb-name:hover {
  text-decoration: underline;
}
</style>
