<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ChatLineSquare, Document, Files, TrendCharts } from '@element-plus/icons-vue'
import { getQaStats, type QaStats } from '../../api'

const loading = ref(false)
const stats = ref<QaStats>({
  totalConversations: 0,
  totalMessages: 0,
  totalCitations: 0,
})

const cards = computed(() => [
  { label: '会话总数', value: stats.value.totalConversations, icon: ChatLineSquare, tone: 'blue' },
  { label: '消息总数', value: stats.value.totalMessages, icon: Files, tone: 'green' },
  { label: '引用片段', value: stats.value.totalCitations, icon: Document, tone: 'amber' },
])

async function loadStats() {
  loading.value = true
  try {
    stats.value = await getQaStats()
  } catch (error) {
    console.error(error)
    ElMessage.error('问答统计加载失败。')
  } finally {
    loading.value = false
  }
}

onMounted(loadStats)
</script>

<template>
  <div class="admin-page" v-loading="loading">
    <header class="admin-header">
      <div>
        <p>问答统计</p>
        <h1>智能问答运行概览</h1>
        <span>用于观察会话规模、消息数量和引用片段使用情况。</span>
      </div>
      <el-button :icon="TrendCharts" @click="loadStats">刷新</el-button>
    </header>

    <section class="stat-grid">
      <article v-for="item in cards" :key="item.label" class="stat-card">
        <span class="stat-icon" :class="item.tone"><component :is="item.icon" /></span>
        <div>
          <strong>{{ item.value }}</strong>
          <span>{{ item.label }}</span>
        </div>
      </article>
    </section>

    <section class="panel">
      <h2>联调说明</h2>
      <p>当前统计数据来自 <code>GET /api/admin/stats</code>。后端完成后只要保持字段一致，页面无需改动。</p>
    </section>
  </div>
</template>

<style scoped>
.admin-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.admin-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
}

.admin-header p {
  margin: 0;
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}

.admin-header h1 {
  margin: 4px 0;
  color: var(--text-primary);
  font-size: 26px;
}

.admin-header span,
.panel p {
  color: var(--text-secondary);
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.stat-card,
.panel {
  border: 1px solid var(--border-color);
  border-radius: var(--border-radius);
  background: var(--bg-container);
  box-shadow: var(--shadow-xs);
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 18px;
}

.stat-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: var(--border-radius-sm);
}

.stat-icon :deep(svg) {
  width: 20px;
  height: 20px;
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
  font-size: 26px;
}

.stat-card span {
  color: var(--text-tertiary);
}

.panel {
  padding: 18px;
}

.panel h2 {
  margin: 0 0 8px;
  font-size: 18px;
}

@media (max-width: 900px) {
  .admin-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .stat-grid {
    grid-template-columns: 1fr;
  }
}
</style>
