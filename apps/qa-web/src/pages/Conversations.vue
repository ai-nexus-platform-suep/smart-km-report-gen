<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  ChatLineSquare,
  Clock,
  Delete,
  Document,
  EditPen,
  Files,
  Plus,
  Search,
  View,
} from '@element-plus/icons-vue'
import {
  createConversation,
  deleteConversation,
  getQaStats,
  listConversations,
  updateConversationTitle,
  type ConversationView,
  type QaStats,
} from '../api'

const keyword = ref('')
const status = ref('all')
const activeRowId = ref<string | null>(null)
const loading = ref(false)
const rows = ref<ConversationView[]>([])
const conversationTotal = ref(0)
const qaStats = ref<QaStats>({
  totalConversations: 0,
  totalMessages: 0,
  totalCitations: 0,
})
const router = useRouter()

// 顶部搜索和状态筛选，后续可以迁移为后端分页查询参数。
const filteredRows = computed(() => {
  const text = keyword.value.trim()
  return rows.value.filter((row) => {
    const matchesKeyword = !text || `${row.id}${row.title}${row.description}`.includes(text)
    const matchesStatus = status.value === 'all' || row.status === status.value
    return matchesKeyword && matchesStatus
  })
})

// 右侧详情面板跟随列表选中项变化。
const activeRow = computed(() => rows.value.find((row) => row.id === activeRowId.value) ?? rows.value[0])

const stats = computed(() => [
  { label: '会话总数', value: conversationTotal.value || rows.value.length, icon: ChatLineSquare, tone: 'blue' },
  { label: '知识问答次数', value: qaStats.value.totalMessages, icon: Files, tone: 'green' },
  { label: '空会话', value: rows.value.filter((row) => row.status === 'empty').length, icon: Document, tone: 'amber' },
])

function selectRow(id: string) {
  activeRowId.value = id
}

async function loadPageData() {
  loading.value = true
  try {
    const [conversationResult, statsResult] = await Promise.all([
      listConversations({ page: 1, size: 50 }),
      getQaStats(),
    ])
    rows.value = conversationResult.items
    conversationTotal.value = conversationResult.total
    qaStats.value = statsResult
    activeRowId.value = rows.value[0]?.id ?? null
  } catch (error) {
    console.error(error)
    ElMessage.error('会话管理数据加载失败。')
  } finally {
    loading.value = false
  }
}

async function handleCreateConversation() {
  try {
    const conversation = await createConversation('新对话')
    rows.value = [conversation, ...rows.value]
    activeRowId.value = conversation.id
    await router.push({ path: '/chat', query: { conversationId: conversation.id } })
  } catch (error) {
    console.error(error)
    ElMessage.error('新建对话失败。')
  }
}

async function handleRenameConversation(row: ConversationView) {
  try {
    const nextTitle = await ElMessageBox.prompt('请输入新的会话标题', '重命名会话', {
      inputValue: row.title,
      inputValidator(value) {
        return Boolean(value.trim()) || '标题不能为空'
      },
      confirmButtonText: '保存',
      cancelButtonText: '取消',
    })
    const title = nextTitle.value.trim()
    const updated = await updateConversationTitle(row.id, title)
    rows.value = rows.value.map((item) => (item.id === row.id ? updated : item))
    activeRowId.value = updated.id
    ElMessage.success('会话标题已更新')
  } catch (error) {
    if (error !== 'cancel') {
      console.error(error)
      ElMessage.error('重命名失败。')
    }
  }
}

function openConversation(id: string) {
  router.push({ path: '/chat', query: { conversationId: id } })
}

async function handleDeleteConversation(row: ConversationView) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.title}」吗？`, '删除会话', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await deleteConversation(row.id)
    rows.value = rows.value.filter((item) => item.id !== row.id)
    activeRowId.value = rows.value[0]?.id ?? null
    ElMessage.success('会话已删除')
  } catch (error) {
    if (error !== 'cancel') {
      console.error(error)
      ElMessage.error('删除失败。')
    }
  }
}

onMounted(() => {
  loadPageData()
})
</script>

<template>
  <div class="conversation-page">
    <!-- 页面头部：说明当前模块定位，并提供新建对话入口。 -->
    <header class="page-header">
      <div>
        <p class="eyebrow">会话管理</p>
        <h1>智能问答工作台</h1>
        <span>严格对接 qa-agent 的会话列表、消息数量、最近更新时间与删除/重命名能力。</span>
      </div>
      <el-button type="primary" :icon="Plus" :loading="loading" @click="handleCreateConversation">新建对话</el-button>
    </header>

    <!-- 总览统计：让管理页一眼看到问答使用情况。 -->
    <section class="stat-grid">
      <article v-for="item in stats" :key="item.label" class="stat-card">
        <span class="stat-icon" :class="item.tone">
          <component :is="item.icon" />
        </span>
        <div>
          <strong>{{ item.value }}</strong>
          <span>{{ item.label }}</span>
        </div>
      </article>
    </section>

    <!-- 主体：左侧列表管理，右侧展示当前会话详情。 -->
    <section class="conversation-layout">
      <main v-loading="loading" class="conversation-table-panel">
        <!-- 工具栏：搜索、筛选，后续可扩展时间排序和知识库筛选。 -->
        <div class="toolbar">
          <el-input
            v-model="keyword"
            class="search-input"
            :prefix-icon="Search"
            placeholder="搜索会话标题或会话 ID"
          />
          <el-segmented
            v-model="status"
            :options="[
              { label: '全部', value: 'all' },
              { label: '进行中', value: 'active' },
              { label: '空会话', value: 'empty' },
            ]"
          />
        </div>

        <!-- 表头采用自定义 grid，方便后续改成更像 FastGPT 的工作台列表。 -->
        <div class="table-head">
          <span>会话</span>
          <span>会话ID</span>
          <span>统计</span>
          <span>状态</span>
          <span>操作</span>
        </div>

        <div class="conversation-rows">
          <el-empty v-if="!filteredRows.length" description="暂无匹配会话" />
          <article
            v-for="row in filteredRows"
            :key="row.id"
            class="conversation-row"
            :class="{ active: row.id === activeRowId }"
            @click="selectRow(row.id)"
          >
            <div class="row-main">
              <span class="row-icon">
                <ChatLineSquare />
              </span>
              <div>
                <strong>{{ row.title }}</strong>
                <p>{{ row.description }}</p>
                <small>
                  <el-icon><Clock /></el-icon>
                  {{ row.updatedAt }}
                </small>
              </div>
            </div>

            <div class="session-id">
              #{{ row.id }}
            </div>

            <div class="row-stats">
              <span>{{ row.messageCount }} 条消息</span>
              <span>创建于 {{ row.createdAt ? new Date(row.createdAt).toLocaleDateString('zh-CN') : '--' }}</span>
            </div>

            <div class="owner">
              <el-tag size="small" :type="row.status === 'active' ? 'success' : 'info'">
                {{ row.status === 'active' ? '已有消息' : '空会话' }}
              </el-tag>
            </div>

            <div class="row-actions">
              <el-tooltip content="查看" placement="top">
                <el-button text circle :icon="View" @click.stop="openConversation(row.id)" />
              </el-tooltip>
              <el-tooltip content="重命名" placement="top">
                <el-button text circle :icon="EditPen" @click.stop="handleRenameConversation(row)" />
              </el-tooltip>
              <el-tooltip content="删除" placement="top">
                <el-button text circle :icon="Delete" @click.stop="handleDeleteConversation(row)" />
              </el-tooltip>
            </div>
          </article>
        </div>
      </main>

      <!-- 详情侧栏：用于快速查看当前会话上下文，不打断列表操作。 -->
      <aside v-if="activeRow" class="detail-panel">
        <div class="detail-header">
          <span class="detail-icon"><Document /></span>
          <div>
            <p>当前会话</p>
            <h2>{{ activeRow.title }}</h2>
          </div>
        </div>

        <dl class="detail-list">
          <div>
            <dt>负责人</dt>
            <dd>{{ activeRow.owner }}</dd>
          </div>
          <div>
            <dt>最近更新</dt>
            <dd>{{ activeRow.updatedAt }}</dd>
          </div>
          <div>
            <dt>会话 ID</dt>
            <dd>#{{ activeRow.id }}</dd>
          </div>
          <div>
            <dt>消息数量</dt>
            <dd>{{ activeRow.messageCount }}</dd>
          </div>
        </dl>

        <div class="preview-box">
          <strong>最近上下文</strong>
          <p>{{ activeRow.description }}</p>
        </div>

        <div class="detail-actions">
          <el-button type="primary" plain :icon="View" @click="openConversation(activeRow.id)">打开会话</el-button>
          <el-button :icon="EditPen" @click="handleRenameConversation(activeRow)">重命名</el-button>
        </div>
      </aside>
    </section>
  </div>
</template>

<style scoped>
/* 会话页偏管理工作台，布局比聊天页更紧凑，便于扫描和批量操作。 */
.conversation-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: calc(100vh - var(--header-height) - 48px);
}

.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
}

.eyebrow {
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}

.page-header h1 {
  margin: 2px 0 4px;
  color: var(--text-primary);
  font-size: 26px;
  letter-spacing: 0;
}

.page-header span {
  color: var(--text-secondary);
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.stat-card,
.conversation-table-panel,
.detail-panel {
  border: 1px solid var(--border-color);
  border-radius: var(--border-radius);
  background: var(--bg-container);
  box-shadow: var(--shadow-xs);
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
}

.stat-icon,
.row-icon,
.detail-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--border-radius-sm);
}

.stat-icon {
  width: 38px;
  height: 38px;
}

.stat-icon :deep(svg),
.row-icon :deep(svg),
.detail-icon :deep(svg) {
  width: 18px;
  height: 18px;
}

.stat-icon.blue {
  background: #eef3ff;
  color: #155eef;
}

.stat-icon.green {
  background: #eafaf3;
  color: #17b26a;
}

.stat-icon.amber {
  background: #fff5e6;
  color: #b54708;
}

.stat-card strong {
  display: block;
  color: var(--text-primary);
  font-size: 22px;
  line-height: 1.2;
}

.stat-card span {
  color: var(--text-tertiary);
}

.conversation-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 330px;
  gap: 16px;
  min-height: 0;
}

.conversation-table-panel {
  overflow: hidden;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 16px;
  border-bottom: 1px solid var(--border-color-light);
}

.search-input {
  max-width: 420px;
}

.table-head,
.conversation-row {
  display: grid;
  grid-template-columns: minmax(260px, 1.4fr) minmax(150px, 0.8fr) 150px 120px 130px;
  gap: 14px;
  align-items: center;
}

.table-head {
  padding: 11px 18px;
  border-bottom: 1px solid var(--border-color-light);
  background: #fbfcfd;
  color: var(--text-tertiary);
  font-size: 12px;
  font-weight: 700;
}

.conversation-row {
  padding: 16px 18px;
  border-bottom: 1px solid var(--border-color-light);
  cursor: pointer;
  transition: background var(--transition-fast);
}

.conversation-row:hover {
  background: #fbfcfd;
}

.conversation-row.active {
  background: #f0f7ff;
}

.row-main {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  gap: 12px;
  min-width: 0;
}

.row-icon {
  width: 38px;
  height: 38px;
  background: #edf6f5;
  color: #0f766e;
}

.row-main strong {
  display: block;
  overflow: hidden;
  color: var(--text-primary);
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-main p {
  display: -webkit-box;
  overflow: hidden;
  margin: 4px 0;
  color: var(--text-secondary);
  font-size: 13px;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.row-main small {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--text-tertiary);
}

.kb-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.row-stats,
.owner {
  display: flex;
  flex-direction: column;
  gap: 5px;
  color: var(--text-secondary);
  font-size: 13px;
}

.row-actions {
  display: flex;
  justify-content: flex-end;
  gap: 2px;
}

.detail-panel {
  align-self: start;
  padding: 18px;
}

.detail-header {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  gap: 12px;
  align-items: center;
  margin-bottom: 18px;
}

.detail-icon {
  width: 42px;
  height: 42px;
  background: #eef3ff;
  color: #155eef;
}

.detail-header p {
  margin: 0;
  color: var(--text-tertiary);
  font-size: 12px;
}

.detail-header h2 {
  margin: 2px 0 0;
  color: var(--text-primary);
  font-size: 18px;
  letter-spacing: 0;
}

.detail-list {
  display: grid;
  gap: 12px;
  margin: 0 0 16px;
}

.detail-list div {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--border-color-light);
}

.detail-list dt {
  color: var(--text-tertiary);
}

.detail-list dd {
  margin: 0;
  color: var(--text-primary);
  text-align: right;
}

.preview-box {
  padding: 14px;
  border: 1px solid var(--border-color);
  border-radius: var(--border-radius);
  background: #fbfcfd;
}

.preview-box strong {
  color: var(--text-primary);
}

.preview-box p {
  margin: 8px 0 0;
  color: var(--text-secondary);
  line-height: 1.7;
}

.detail-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 16px;
}

/* 小屏下表格降级为卡片流，保证手机和窄屏也能浏览。 */
@media (max-width: 1180px) {
  .conversation-layout {
    grid-template-columns: 1fr;
  }

  .detail-panel {
    order: -1;
  }
}

@media (max-width: 900px) {
  .page-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .stat-grid {
    grid-template-columns: 1fr;
  }

  .toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .search-input {
    max-width: none;
  }

  .table-head {
    display: none;
  }

  .conversation-row {
    grid-template-columns: 1fr;
  }

  .row-actions {
    justify-content: flex-start;
  }
}
</style>
