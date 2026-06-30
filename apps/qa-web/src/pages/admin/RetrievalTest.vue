<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { runRetrievalTest } from '../../api'
import type { Citation } from '@platform/core'

const question = ref('汽轮机大修周期是否必须固定为 4 年？')
const loading = ref(false)
const results = ref<Citation[]>([])

async function handleSearch() {
  const text = question.value.trim()
  if (!text) {
    ElMessage.warning('请输入测试问题。')
    return
  }

  loading.value = true
  try {
    const data = await runRetrievalTest(text)
    results.value = data.results
  } catch (error) {
    console.error(error)
    ElMessage.error('检索测试失败。')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="retrieval-page">
    <header class="page-header">
      <div>
        <p>检索测试</p>
        <h1>知识召回调试</h1>
        <span>用于验证问题是否能召回正确文档片段。</span>
      </div>
    </header>

    <section class="query-panel">
      <el-input
        v-model="question"
        type="textarea"
        :autosize="{ minRows: 3, maxRows: 5 }"
        placeholder="输入测试问题"
      />
      <div class="actions">
        <el-button type="primary" :icon="Search" :loading="loading" @click="handleSearch">开始检索</el-button>
      </div>
    </section>

    <section class="result-panel" v-loading="loading">
      <div class="result-head">
        <strong>召回结果</strong>
        <span>{{ results.length }} 条</span>
      </div>
      <el-empty v-if="!results.length" description="暂无检索结果" />
      <article v-for="item in results" :key="`${item.documentName}-${item.score}`" class="result-card">
        <div class="card-head">
          <strong>{{ item.documentName }}</strong>
          <el-tag type="success">{{ Math.round(item.score * 100) }}%</el-tag>
        </div>
        <p>{{ item.content }}</p>
        <small>{{ item.source ?? '知识库片段' }}</small>
      </article>
    </section>
  </div>
</template>

<style scoped>
.retrieval-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header p {
  margin: 0;
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}

.page-header h1 {
  margin: 4px 0;
  color: var(--text-primary);
  font-size: 26px;
}

.page-header span {
  color: var(--text-secondary);
}

.query-panel,
.result-panel {
  border: 1px solid var(--border-color);
  border-radius: var(--border-radius);
  background: var(--bg-container);
  box-shadow: var(--shadow-xs);
}

.query-panel {
  padding: 18px;
}

.actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.result-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 18px;
}

.result-head,
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.result-head span {
  color: var(--text-tertiary);
}

.result-card {
  padding: 14px;
  border: 1px solid var(--border-color-light);
  border-radius: var(--border-radius-sm);
  background: #fbfcfd;
}

.result-card p {
  margin: 10px 0;
  color: var(--text-secondary);
  line-height: 1.7;
}

.result-card small {
  color: var(--text-tertiary);
}
</style>
