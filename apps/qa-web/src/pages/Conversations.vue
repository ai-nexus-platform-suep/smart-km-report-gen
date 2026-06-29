<script setup lang="ts">
import { computed, ref } from 'vue'
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

type ConversationRow = {
  id: number
  title: string
  description: string
  knowledgeBases: string[]
  messageCount: number
  citationCount: number
  owner: string
  updatedAt: string
  status: 'active' | 'archived'
}

// 当前页面先使用静态演示数据，后续替换为 API_QA.CHAT.LIST 的列表结果。
const keyword = ref('')
const status = ref('all')
const activeRowId = ref(1)

// 会话管理列表，字段设计贴近真实业务：会话、知识库、引用数、负责人、状态。
const rows: ConversationRow[] = [
  {
    id: 1,
    title: '汽轮机检修周期判断',
    description: '围绕运行小时数、启停次数、设备缺陷和规程条款进行问答。',
    knowledgeBases: ['设备检修', '技术监督'],
    messageCount: 18,
    citationCount: 7,
    owner: '王工',
    updatedAt: '2026-06-29 09:42',
    status: 'active',
  },
  {
    id: 2,
    title: '锅炉安全规范相关问题',
    description: '检索锅炉检修前工作票、隔离措施、危险点预控要求。',
    knowledgeBases: ['安全规程'],
    messageCount: 12,
    citationCount: 5,
    owner: '李工',
    updatedAt: '2026-06-28 17:10',
    status: 'active',
  },
  {
    id: 3,
    title: '电气设备试验报告解释',
    description: '结合预防性试验规程解释绝缘电阻、介损和结论口径。',
    knowledgeBases: ['电气设备', '报告规范'],
    messageCount: 9,
    citationCount: 4,
    owner: '赵工',
    updatedAt: '2026-06-27 14:36',
    status: 'active',
  },
  {
    id: 4,
    title: '煤库存审计口径确认',
    description: '用于说明库存盘点、异常波动、佐证材料与监督建议。',
    knowledgeBases: ['经营监督'],
    messageCount: 6,
    citationCount: 3,
    owner: '陈工',
    updatedAt: '2026-06-24 11:08',
    status: 'archived',
  },
]

// 顶部搜索和状态筛选，后续可以迁移为后端分页查询参数。
const filteredRows = computed(() => {
  const text = keyword.value.trim()
  return rows.filter((row) => {
    const matchesKeyword = !text || `${row.title}${row.description}${row.knowledgeBases.join('')}`.includes(text)
    const matchesStatus = status.value === 'all' || row.status === status.value
    return matchesKeyword && matchesStatus
  })
})

// 右侧详情面板跟随列表选中项变化。
const activeRow = computed(() => rows.find((row) => row.id === activeRowId.value) ?? rows[0])

// 顶部统计卡片，后续可以替换为 API_QA.ADMIN.STATS 返回值。
const stats = computed(() => [
  { label: '会话总数', value: rows.length, icon: ChatLineSquare, tone: 'blue' },
  { label: '消息总数', value: rows.reduce((sum, row) => sum + row.messageCount, 0), icon: Files, tone: 'green' },
  { label: '引用片段', value: rows.reduce((sum, row) => sum + row.citationCount, 0), icon: Document, tone: 'amber' },
])

function selectRow(id: number) {
  activeRowId.value = id
}
</script>

<template>
  <div class="conversation-page">
    <!-- 页面头部：说明当前模块定位，并提供新建对话入口。 -->
    <header class="page-header">
      <div>
        <p class="eyebrow">会话管理</p>
        <h1>智能问答工作台</h1>
        <span>管理技术监督问答记录、引用来源和知识库上下文。</span>
      </div>
      <el-button type="primary" :icon="Plus">新建对话</el-button>
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
      <main class="conversation-table-panel">
        <!-- 工具栏：搜索、筛选，后续可扩展时间排序和知识库筛选。 -->
        <div class="toolbar">
          <el-input
            v-model="keyword"
            class="search-input"
            :prefix-icon="Search"
            placeholder="搜索会话、知识库、监督主题"
          />
          <el-segmented
            v-model="status"
            :options="[
              { label: '全部', value: 'all' },
              { label: '进行中', value: 'active' },
              { label: '已归档', value: 'archived' },
            ]"
          />
        </div>

        <!-- 表头采用自定义 grid，方便后续改成更像 FastGPT 的工作台列表。 -->
        <div class="table-head">
          <span>会话</span>
          <span>知识库</span>
          <span>统计</span>
          <span>负责人</span>
          <span>操作</span>
        </div>

        <div class="conversation-rows">
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

            <div class="kb-tags">
              <el-tag v-for="kb in row.knowledgeBases" :key="kb" size="small" effect="plain">
                {{ kb }}
              </el-tag>
            </div>

            <div class="row-stats">
              <span>{{ row.messageCount }} 条消息</span>
              <span>{{ row.citationCount }} 个引用</span>
            </div>

            <div class="owner">
              <span>{{ row.owner }}</span>
              <el-tag size="small" :type="row.status === 'active' ? 'success' : 'info'">
                {{ row.status === 'active' ? '进行中' : '已归档' }}
              </el-tag>
            </div>

            <div class="row-actions">
              <el-tooltip content="查看" placement="top">
                <el-button text circle :icon="View" />
              </el-tooltip>
              <el-tooltip content="重命名" placement="top">
                <el-button text circle :icon="EditPen" />
              </el-tooltip>
              <el-tooltip content="删除" placement="top">
                <el-button text circle :icon="Delete" />
              </el-tooltip>
            </div>
          </article>
        </div>
      </main>

      <!-- 详情侧栏：用于快速查看当前会话上下文，不打断列表操作。 -->
      <aside class="detail-panel">
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
            <dt>关联知识库</dt>
            <dd>{{ activeRow.knowledgeBases.join('、') }}</dd>
          </div>
          <div>
            <dt>消息 / 引用</dt>
            <dd>{{ activeRow.messageCount }} / {{ activeRow.citationCount }}</dd>
          </div>
        </dl>

        <div class="preview-box">
          <strong>最近上下文</strong>
          <p>{{ activeRow.description }}</p>
        </div>

        <div class="detail-actions">
          <el-button type="primary" plain :icon="View">打开会话</el-button>
          <el-button :icon="EditPen">编辑信息</el-button>
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
