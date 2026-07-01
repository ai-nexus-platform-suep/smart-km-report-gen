<template>
  <div class="page report-view-page">
    <PageHeader
      eyebrow="REPORT READER"
      title="报告正文查看"
      description="用于历史报告浏览和复核。正文已生成后优先进入该页面，避免把查看场景和生成工作台混在一起。"
    >
      <el-button @click="$router.push('/reports')">返回记录</el-button>
      <el-button @click="$router.push(`/reports/${reportId}/workspace`)">编辑正文</el-button>
      <el-button type="primary" :disabled="!canExport" @click="$router.push(`/reports/${reportId}/export`)">导出 DOCX</el-button>
    </PageHeader>

    <section v-if="report" class="terminal-band reader-band">
      <div>
        <span class="terminal-label">REPORT</span>
        <strong>{{ report.name }}</strong>
      </div>
      <div>
        <span class="terminal-label">STATUS</span>
        <StatusBadge :status="report.status" />
      </div>
      <div>
        <span class="terminal-label">PROGRESS</span>
        <strong>{{ report.completedSections }} / {{ report.totalSections }}</strong>
      </div>
    </section>

    <div v-if="report" class="reader-layout">
      <section class="surface reader-tree">
        <div class="surface-title">
          <div>
            <span class="eyebrow">SECTION TREE</span>
            <h2>章节目录</h2>
          </div>
        </div>
        <div class="reader-scroll">
          <OutlineTree
            :nodes="report.outline"
            :sections="report.sections"
            :selected-id="selectedOutlineId"
            collapsible
            @select="selectedOutlineId = $event"
          />
        </div>
      </section>

      <section class="surface reader-content">
        <div class="surface-title">
          <div>
            <span class="eyebrow">MARKDOWN VIEW</span>
            <h2>{{ selectedOutlineNode?.number }} {{ selectedOutlineNode?.title }}</h2>
          </div>
          <StatusBadge v-if="selectedSection" :status="selectedSection.status" type="section" />
        </div>

        <article v-if="selectedSection" class="markdown-reader" v-html="contentHtml" />

        <div v-else class="empty-section-note">
          <strong>{{ selectedOutlineNode?.number }} {{ selectedOutlineNode?.title }}</strong>
          <p>该节点是目录级标题，不承载正文。请选择下方子章节查看正文内容。</p>
          <div v-if="childContentNodes.length" class="child-jump-list">
            <el-button v-for="node in childContentNodes" :key="node.id" size="small" @click="selectedOutlineId = node.id">
              {{ node.number }} {{ node.title }}
            </el-button>
          </div>
        </div>
      </section>

      <section class="surface reader-meta">
        <div class="surface-title">
          <div>
            <span class="eyebrow">REPORT META</span>
            <h2>报告信息</h2>
          </div>
        </div>
        <div class="control-panel">
          <div class="data-line">
            <span>报告类型</span>
            <strong>{{ reportTypeLabels[report.type] }}</strong>
          </div>
          <div class="data-line">
            <span>专业</span>
            <strong>{{ report.specialty }}</strong>
          </div>
          <div class="data-line">
            <span>电厂</span>
            <strong>{{ report.powerPlant }}</strong>
          </div>
          <div class="data-line">
            <span>年份</span>
            <strong>{{ report.reportYear }}</strong>
          </div>
          <div class="data-line">
            <span>更新时间</span>
            <strong>{{ new Date(report.updatedAt).toLocaleString() }}</strong>
          </div>
          <el-button @click="$router.push(`/reports/${reportId}/workspace`)">进入编辑工作台</el-button>
          <el-button type="primary" :disabled="!canExport" @click="$router.push(`/reports/${reportId}/export`)">
            导出检查
          </el-button>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import OutlineTree from '@/components/OutlineTree.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { useReportStore } from '@/stores/reports'
import type { EntityId, OutlineNode } from '@/types/domain'
import { contentOutlineNodes, isContentOutlineNode } from '@/utils/outline'
import { reportTypeLabels } from '@/utils/labels'
import { renderMarkdownHtml } from '@/utils/markdown'

const route = useRoute()
const store = useReportStore()
const reportId = String(route.params.id)
const selectedOutlineId = ref<EntityId>()
const sameId = (a?: EntityId, b?: EntityId) => String(a) === String(b)

const report = computed(() => store.current)
const contentNodes = computed(() => (report.value ? contentOutlineNodes(report.value.outline) : []))
const selectedOutlineNode = computed(() => report.value?.outline.find((node) => sameId(node.id, selectedOutlineId.value)))
const selectedSection = computed(() => {
  if (!report.value || !selectedOutlineNode.value || !isContentOutlineNode(report.value.outline, selectedOutlineNode.value)) return undefined
  return report.value.sections.find((section) => sameId(section.outlineNodeId, selectedOutlineId.value))
})
const contentHtml = computed(() => renderMarkdownHtml(selectedSection.value?.contentMarkdown || ''))
const canExport = computed(() => report.value?.status === 'CONTENT_READY' || report.value?.status === 'EXPORTED')
const childContentNodes = computed(() => {
  if (!report.value || !selectedOutlineNode.value) return []
  return contentNodes.value.filter((node) => isDescendantOf(node, selectedOutlineNode.value!.id))
})

onMounted(async () => {
  const detail = await store.fetchDetail(reportId)
  selectedOutlineId.value = contentOutlineNodes(detail.outline)[0]?.id ?? detail.outline[0]?.id
})

function isDescendantOf(node: OutlineNode, ancestorId: EntityId) {
  let parentId = node.parentId
  while (parentId) {
    if (sameId(parentId, ancestorId)) return true
    parentId = report.value?.outline.find((item) => sameId(item.id, parentId))?.parentId
  }
  return false
}
</script>

<style scoped>
.report-view-page {
  min-height: 100%;
}

.reader-band {
  display: grid;
  grid-template-columns: minmax(280px, 1fr) 220px 180px;
  gap: 18px;
  padding: 14px 18px;
  margin-bottom: 12px;
}

.reader-band strong {
  display: block;
  margin-top: 6px;
}

.reader-band :deep(.status-badge) {
  margin-top: 8px;
  color: rgba(248, 251, 255, 0.78);
}

.reader-layout {
  display: grid;
  grid-template-columns: 310px minmax(0, 1fr) 300px;
  align-items: start;
  gap: 16px;
}

.reader-tree,
.reader-meta {
  position: sticky;
  top: 12px;
}

.reader-scroll {
  max-height: calc(100vh - 285px);
  overflow: auto;
  padding-bottom: 8px;
}

.reader-content {
  min-height: calc(100vh - 235px);
}

.markdown-reader {
  min-height: calc(100vh - 320px);
  padding: 22px;
  color: var(--text-primary);
  line-height: 1.85;
}

.markdown-reader :deep(h1),
.markdown-reader :deep(h2),
.markdown-reader :deep(h3),
.markdown-reader :deep(h4) {
  margin: 0 0 14px;
  color: var(--text-primary);
  font-weight: 800;
}

.markdown-reader :deep(p) {
  margin: 0 0 16px;
}

.markdown-reader :deep(table) {
  width: 100%;
  margin: 16px 0;
  border-collapse: collapse;
  border: 1px solid var(--border-default);
}

.markdown-reader :deep(th),
.markdown-reader :deep(td) {
  padding: 10px 12px;
  border: 1px solid var(--border-default);
  text-align: left;
}

.markdown-reader :deep(th) {
  background: #edf5ff;
  font-weight: 800;
}

.control-panel {
  display: grid;
  gap: 14px;
  padding: 16px;
}

.empty-section-note {
  display: grid;
  gap: 14px;
  min-height: 360px;
  align-content: center;
  padding: 28px;
  color: var(--text-secondary);
}

.empty-section-note strong {
  color: var(--text-primary);
  font-size: 22px;
}

.empty-section-note p {
  max-width: 560px;
  margin: 0;
  line-height: 1.8;
}

.child-jump-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

@media (max-width: 1366px) {
  .reader-layout {
    grid-template-columns: 290px minmax(0, 1fr);
  }

  .reader-meta {
    position: static;
    grid-column: 1 / -1;
  }
}
</style>
