<template>
  <div class="search-page">
    <div class="page-header">
      <h2>知识检索</h2>
      <p class="page-desc">在知识库中搜索相关文档内容</p>
    </div>

    <!-- 搜索区 -->
    <div class="search-box">
      <!-- 检索范围：多知识库选择 -->
      <div class="kb-picker">
        <span class="label">检索范围：</span>
        <el-select
          v-model="kbIds"
          multiple
          placeholder="全部知识库"
          collapse-tags
          collapse-tags-tooltip
          :max-collapse-tags="2"
          clearable
          style="width: 380px"
        >
          <el-option v-for="kb in kbList" :key="kb.id" :label="kb.name" :value="kb.id" />
        </el-select>
        <el-tag v-if="!kbIds || kbIds.length === 0" size="small" type="info" effect="plain">
          不选择则搜索全部
        </el-tag>
      </div>

      <!-- 搜索输入框 -->
      <div class="search-row">
        <el-input
          v-model="query"
          placeholder="输入搜索关键词，按回车搜索"
          size="large"
          clearable
          @keyup.enter="doSearch"
          @input="onInput"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
          <template #append>
            <el-button type="primary" size="large" :loading="loading" @click="doSearch">
              <el-icon><Search /></el-icon>
              <span>搜索</span>
            </el-button>
          </template>
        </el-input>
      </div>

      <!-- 搜索模式 -->
      <div class="search-footer">
        <el-radio-group v-model="mode" size="small" @change="doSearch">
          <el-radio-button value="VECTOR">向量检索</el-radio-button>
          <el-radio-button value="VECTOR_RERANK">向量+重排序</el-radio-button>
        </el-radio-group>
        <span v-if="searched" class="total">共 <em>{{ total }}</em> 条结果</span>
      </div>
          <el-collapse v-model="advancedOpen" class="advanced-section">
        <el-collapse-item title="高级过滤" name="advanced">
          <el-row :gutter="24">
            <el-col :span="8">
              <div class="filter-item">
                <label>相似度阈值</label>
                <el-slider v-model="threshold" :min="0" :max="1" :step="0.05" :format-tooltip="v => (v * 100).toFixed(0) + '%'" />
                <span class="filter-val">{{ (threshold * 100).toFixed(0) }}%</span>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="filter-item">
                <label>Top-K</label>
                <el-input-number v-model="topK" :min="1" :max="50" :step="1" style="width:100%" />
              </div>
            </el-col>
            <el-col :span="8">
              <div class="filter-item">
                <label>标签过滤</label>
                <el-select v-model="tagKeys" multiple placeholder="选择标签" clearable style="width:100%">
                  <el-option v-for="t in availableTags" :key="t" :label="t" :value="t" />
                </el-select>
              </div>
            </el-col>
          </el-row>
        </el-collapse-item>
      </el-collapse>
    </div>

    <!-- 结果区 -->
    <div class="result-area">
      <!-- 加载中 -->
      <div v-if="loading" class="state-box">
        <el-skeleton :rows="4" animated />
      </div>

      <!-- 初始提示 -->
      <div v-else-if="!searched" class="state-box init">
        <el-icon :size="48" color="var(--text-tertiary)"><Search /></el-icon>
        <p class="t1">输入关键词开始搜索</p>
        <p class="t2">可在上方选择知识库缩小范围</p>
      </div>

      <!-- 无结果 -->
      <div v-else-if="list.length === 0" class="state-box empty">
        <el-icon :size="48" color="var(--color-warning)"><WarningFilled /></el-icon>
        <p class="t1">未找到相关结果</p>
        <p class="t2">换个关键词试试</p>
      </div>

      <!-- 结果列表 -->
      <div v-else class="result-list">
        <div v-for="(it, i) in list" :key="i" class="card">
          <div class="card-hd">
            <span class="idx">{{ i + 1 }}</span>
            <span class="name">
              <el-icon :size="13"><Document /></el-icon>
              {{ it.documentName }}
            </span>
            <el-tag :type="tagType(it.score)" size="small" effect="dark">
              {{ (it.score * 100).toFixed(1) }}%
            </el-tag>
          </div>
          <div class="card-bd" v-html="highlightContent(it.content)"></div>
          <div class="card-expand" @click.stop="toggleExpand(i)">
            <span v-if="expandedIdx !== i">展开详情 <el-icon><ArrowDown /></el-icon></span>
            <span v-else>收起 <el-icon><ArrowUp /></el-icon></span>
          </div>
          <transition name="fade">
            <div v-if="expandedIdx === i" class="card-detail">
              <el-divider />
              <div class="detail-row"><label>文档ID：</label><span>{{ it.documentId }}</span></div>
              <div class="detail-row"><label>相关度：</label><el-tag :type="tagType(it.score)" size="small">{{ (it.score * 100).toFixed(1) }}%</el-tag></div>
              <div class="detail-row"><label>完整内容：</label></div>
              <div class="detail-fulltext" v-html="highlightContent(it.content)"></div>
            </div>
          </transition>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Search, Document, WarningFilled, ArrowDown, ArrowUp } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { searchDocuments, fetchKbList } from '../api/search'
import type { SearchResultItem } from '@platform/core/types'

// === 搜索表单 ===
const query = ref('')
const kbIds = ref<number[]>([])
const mode = ref<'VECTOR' | 'VECTOR_RERANK'>('VECTOR_RERANK')

// === 知识库列表 ===
const kbList = ref<{ id: number; name: string }[]>([])

// === 结果 ===
const loading = ref(false)
const searched = ref(false)
const list = ref<SearchResultItem[]>([])
// === 高级过滤 ===
const advancedOpen = ref<string[]>([])
const threshold = ref(0)
const topK = ref(10)
const tagKeys = ref<string[]>([])
const tagValue = ref("")
const availableTags = ref<string[]>([])
const expandedIdx = ref<number | null>(null)

function escapeHtml(text: string) {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function highlightContent(text: string) {
  if (!query.value.trim() || !text) return text
  const kw = escapeHtml(query.value.trim())
  const safe = escapeHtml(text)
  const escaped = kw.replace(/[.*+?^${}()|[\]\\]/g, '\\const list = ref<SearchResultItem[]>([])')
  const re = new RegExp(escaped, 'gi')
  return safe.replace(re, function(m) { return '<em class="hl">' + m + '</em>' })
}

function toggleExpand(i: number) {
  expandedIdx.value = expandedIdx.value === i ? null : i
}
const total = ref(0)

// === 防抖 ===
let timer: ReturnType<typeof setTimeout> | null = null

onMounted(() => loadKbList())

async function loadKbList() {
  try {
    const res = await fetchKbList({ page: 1, pageSize: 100 })
    kbList.value = res.data?.data?.records || []
  } catch { /* ignore */ }
}

function onInput() {
  if (timer) clearTimeout(timer)
  timer = setTimeout(() => {
    if (query.value.trim()) doSearch()
  }, 300)
}

async function doSearch() {
  const q = query.value.trim()
  if (!q) {
    ElMessage.warning('请输入关键词')
    return
  }
  loading.value = true
  searched.value = true
  try {
    const res = await searchDocuments({
      query: q,
      kbIds: kbIds.value.length > 0 ? kbIds.value : undefined,
      mode: mode.value,
      threshold: threshold.value > 0 ? threshold.value : undefined,
      topN: topK.value !== 10 ? topK.value : undefined,
    })
    list.value = res.data?.data?.results || []
    total.value = res.data?.data?.total || 0
  } catch (e: any) {
    ElMessage.error('搜索失败：' + (e?.response?.data?.message || e.message))
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function tagType(s: number): 'success' | 'warning' | 'danger' {
  return s >= 0.8 ? 'success' : s >= 0.5 ? 'warning' : 'danger'
}
</script>

<style scoped>
.search-page {
  max-width: 960px;
  margin: 0 auto;
  padding: 20px 0;
}
.page-header { margin-bottom: 20px; }
.page-header h2 { margin: 0 0 4px; font-size: 22px; font-weight: 600; }
.page-desc { margin: 0; font-size: 14px; color: var(--text-secondary); }

/* 搜索框 */
.search-box {
  background: var(--bg-hover);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 20px 24px;
  margin-bottom: 20px;
}
.kb-picker {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.kb-picker .label { font-size: 14px; font-weight: 500; white-space: nowrap; }
.search-row { display: flex; }
.search-row :deep(.el-input-group__append) { padding: 0; }
.search-row :deep(.el-button) { height: 100%; border-radius: 0; padding: 0 24px; }
.search-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
}
.total { font-size: 13px; color: var(--text-secondary); }
.total em { color: #409eff; font-style: normal; font-weight: 600; }

/* 结果 */
.result-area { min-height: 200px; }
.state-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80px 0;
  color: var(--text-secondary);
}
.state-box .t1 { margin: 12px 0 4px; font-size: 15px; }
.state-box .t2 { margin: 0; font-size: 13px; }

.result-list { display: flex; flex-direction: column; gap: 12px; }
.card {
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 16px 20px;
  background: var(--bg-container);
  transition: all 0.2s;
}
.card:hover { border-color: var(--color-primary); box-shadow: 0 2px 8px rgba(64,158,255,.15); }
.card-hd {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.idx { color: #409eff; font-weight: 600; min-width: 24px; }
.name {
  flex: 1;
  font-size: 14px;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-bd {
  font-size: 13px;
  line-height: 1.8;
  color: var(--text-secondary);
  white-space: pre-wrap;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.advanced-section { margin-top: 12px; }
.filter-item { display: flex; flex-direction: column; gap: 6px; }
.filter-item label { font-size: 13px; color: var(--text-secondary); font-weight: 500; }
.filter-val { font-size: 13px; color: var(--text-primary); text-align: right; }
.card-expand { font-size: 12px; color: #409eff; cursor: pointer; margin-top: 4px; user-select: none; }
.card-detail { padding: 4px 0 0; }
.detail-row { display: flex; gap: 8px; margin-bottom: 6px; font-size: 13px; }
.detail-row label { color: var(--text-secondary); font-weight: 500; white-space: nowrap; }
.detail-fulltext { font-size: 13px; line-height: 1.7; color: var(--text-primary); padding: 12px; background: var(--bg-hover); border-radius: 6px; max-height: 300px; overflow-y: auto; white-space: pre-wrap; }
.card-bd :deep(.hl) { color: var(--color-warning); font-style: normal; background: rgba(247,144,9,0.15); padding: 0 2px; border-radius: 2px; }
.fade-enter-active, .fade-leave-active { transition: all .25s ease; overflow: hidden; }
.fade-enter-from, .fade-leave-to { opacity: 0; max-height: 0; padding: 0; }
.fade-enter-to, .fade-leave-from { opacity: 1; max-height: 500px; }
</style>
