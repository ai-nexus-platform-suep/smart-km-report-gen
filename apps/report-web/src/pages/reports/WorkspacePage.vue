<template>
  <div class="page workspace-page">
    <PageHeader
      eyebrow="AI GENERATION TERMINAL"
      title="报告生成工作台"
      description="按叶子章节流式生成正文，目录级标题只作为结构导航。生成后可直接在线编辑 Markdown，并预览表格排版。"
    >
      <el-button @click="backToOutline">返回大纲</el-button>
      <el-button :disabled="store.streaming" @click="startGenerate('TEMPLATE')">模板生成正文</el-button>
      <el-button type="primary" :loading="store.streaming" @click="startGenerate('AI')">AI 生成正文</el-button>
      <el-button :disabled="!canExport" @click="$router.push(`/reports/${reportId}/export`)">导出 DOCX</el-button>
    </PageHeader>

    <section v-if="report" class="terminal-band workspace-band">
      <div>
        <span class="terminal-label">STREAM STATUS</span>
        <strong>{{ store.streamMessage }}</strong>
      </div>
      <div class="workspace-status">
        <span class="terminal-label">REPORT STATUS</span>
        <div class="workspace-status-badge">
          <StatusBadge :status="report.status" />
        </div>
      </div>
      <div>
        <span class="terminal-label">PROGRESS</span>
        <el-progress :percentage="store.progressPercent" :show-text="false" :stroke-width="8" />
        <small>{{ report.completedSections }} / {{ report.totalSections }}</small>
      </div>
    </section>

    <div v-if="report" class="workspace-layout">
      <section class="surface section-tree-surface">
        <div class="surface-title">
          <div>
            <span class="eyebrow">SECTION TREE</span>
            <h2>章节目录</h2>
          </div>
        </div>
        <div class="tree-scroll">
          <OutlineTree
            :nodes="report.outline"
            :sections="report.sections"
            :selected-id="selectedOutlineId"
            :table-items="tableDisplayMap"
            collapsible
            @select="selectOutline"
          />
        </div>
      </section>

      <section class="surface editor-surface">
        <div class="surface-title editor-title">
          <div>
            <span class="eyebrow">CONTENT EDITOR</span>
            <h2>{{ selectedOutlineNode?.number }} {{ selectedOutlineNode?.title }}</h2>
          </div>
          <div class="action-row">
            <StatusBadge v-if="selectedSection" :status="selectedSection.status" type="section" />
            <el-radio-group v-model="mode" class="mode-switch">
              <el-radio-button label="edit">编辑</el-radio-button>
              <el-radio-button label="preview">预览</el-radio-button>
            </el-radio-group>
            <el-button size="small" :disabled="!dirty || selectedSection?.status === 'GENERATING'" @click="save">
              保存章节
            </el-button>
          </div>
        </div>

        <div v-if="selectedSection" class="single-editor">
          <el-input
            v-show="mode === 'edit'"
            v-model="draft"
            class="markdown-textarea"
            type="textarea"
            :disabled="selectedSection.status === 'GENERATING'"
            :autosize="false"
            placeholder="这里编辑当前章节 Markdown 正文。表格按大纲中的结构化表格计划生成；没有表格计划的章节不会生成表格。"
            @input="dirty = true"
          />

          <article v-show="mode === 'preview'" class="markdown-reader" v-html="previewHtml" />

          <div v-if="plannedTables.length" class="table-inspector">
            <div class="surface-title mini-title">
              <div>
                <span class="eyebrow">TABLE PLAN</span>
                <h3>结构化表格计划</h3>
              </div>
            </div>
            <el-table :data="plannedTableRows" size="small">
              <el-table-column prop="caption" label="表名" min-width="150" />
              <el-table-column prop="columns" label="列" min-width="220" />
              <el-table-column prop="description" label="说明" />
            </el-table>
          </div>
        </div>

        <div v-else class="empty-section-note">
          <strong>{{ selectedOutlineNode?.number }} {{ selectedOutlineNode?.title }}</strong>
          <p>该节点是目录级标题，不直接生成正文。请选择它下面的子章节进行正文生成、编辑或预览。</p>
          <div v-if="childContentNodes.length" class="child-jump-list">
            <el-button v-for="node in childContentNodes" :key="node.id" size="small" @click="selectOutline(node.id)">
              {{ node.number }} {{ node.title }}
            </el-button>
          </div>
        </div>
      </section>

      <section class="surface right-rail sticky-rail">
        <div class="surface-title">
          <div>
            <span class="eyebrow">CONTROL PANEL</span>
            <h2>章节操作</h2>
          </div>
        </div>
        <div class="control-panel">
          <div class="data-line">
            <span>SSE 连接</span>
            <strong>{{ store.streaming ? 'CONNECTED' : 'IDLE' }}</strong>
          </div>
          <div class="data-line">
            <span>当前节点</span>
            <strong>{{ selectedOutlineNode?.number || '-' }}</strong>
          </div>
          <div class="data-line">
            <span>正文章节</span>
            <strong>{{ selectedSection ? 'YES' : 'NO' }}</strong>
          </div>
          <div class="data-line">
            <span>内容版本</span>
            <strong>v{{ selectedSection?.version || 0 }}</strong>
          </div>
          <div v-if="store.streaming && streamSection" class="data-line">
            <span>正在生成</span>
            <strong>{{ streamSection ? `${streamSection.number} ${streamSection.title}` : '-' }}</strong>
          </div>
          <el-alert v-if="dirty" type="warning" :closable="false" title="当前章节有未保存修改，切换前请保存。" />
          <el-button v-if="store.streaming && streamSection" @click="locateStreamSection">定位正在生成章节</el-button>
          <el-button :disabled="!selectedSection" @click="confirmRegenerate">重新生成本节</el-button>
          <el-button type="primary" :disabled="!canExport" @click="$router.push(`/reports/${reportId}/export`)">
            进入导出检查
          </el-button>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import OutlineTree from '@/components/OutlineTree.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { useReportStore } from '@/stores/reports'
import type { GenerationMode } from '@/api/reports'
import type { EntityId, OutlineNode, ReportSection } from '@/types/domain'
import { contentOutlineNodes, isContentOutlineNode } from '@/utils/outline'
import { renderMarkdownHtml } from '@/utils/markdown'

const route = useRoute()
const router = useRouter()
const store = useReportStore()
const reportId = String(route.params.id)
const selectedOutlineId = ref<EntityId>()
const sameId = (a?: EntityId, b?: EntityId) => String(a) === String(b)
const draft = ref('')
const dirty = ref(false)
const mode = ref<'edit' | 'preview'>('edit')
const streamSectionId = ref<EntityId>()

const report = computed(() => store.current)
const contentNodes = computed(() => (report.value ? contentOutlineNodes(report.value.outline) : []))
const selectedOutlineNode = computed(() => report.value?.outline.find((node) => sameId(node.id, selectedOutlineId.value)))
const selectedSection = computed(() => {
  if (!report.value || !selectedOutlineNode.value || !isContentOutlineNode(report.value.outline, selectedOutlineNode.value)) return undefined
  return report.value.sections.find((section) => sameId(section.outlineNodeId, selectedOutlineId.value))
})
const streamSection = computed(() => report.value?.sections.find((section) => sameId(section.id, streamSectionId.value)))
const previewHtml = computed(() => renderMarkdownHtml(draft.value || ''))
const canExport = computed(() => report.value?.status === 'CONTENT_READY' || report.value?.status === 'EXPORTED')
const plannedTables = computed(() => {
  if (selectedSection.value?.tableJson?.length) return selectedSection.value.tableJson
  return selectedOutlineNode.value?.tables || []
})
const plannedTableRows = computed(() =>
  plannedTables.value.map((table) => ({
    caption: table.caption,
    columns: table.columns.join('、'),
    description: table.description || '-',
  })),
)
const tableDisplayMap = computed(() => buildTableDisplayMap())
const childContentNodes = computed(() => {
  if (!report.value || !selectedOutlineNode.value) return []
  return contentNodes.value.filter((node) => isDescendantOf(node, selectedOutlineNode.value!.id))
})

onMounted(async () => {
  const detail = await store.fetchDetail(reportId)
  selectedOutlineId.value = contentOutlineNodes(detail.outline)[0]?.id ?? detail.outline[0]?.id
  draft.value = selectedSection.value?.contentMarkdown ?? ''
})

onBeforeUnmount(() => store.stopStream())

watch(
  () => selectedSection.value?.id,
  () => {
    dirty.value = false
    draft.value = selectedSection.value?.contentMarkdown ?? ''
  },
)

watch(
  () => selectedSection.value?.contentMarkdown,
  (value) => {
    if (!dirty.value) draft.value = value ?? ''
  },
)

watch(
  () => store.lastEvent,
  (event) => {
    if (!event || (event.type !== 'section_started' && event.type !== 'content_delta')) return
    streamSectionId.value = event.sectionId
  },
)

function selectOutline(id: EntityId) {
  selectedOutlineId.value = id
}

function isDescendantOf(node: OutlineNode, ancestorId: EntityId) {
  let parentId = node.parentId
  while (parentId) {
    if (sameId(parentId, ancestorId)) return true
    parentId = report.value?.outline.find((item) => sameId(item.id, parentId))?.parentId
  }
  return false
}

function sectionTableNames(node: OutlineNode, section?: ReportSection) {
  const plans = section?.tableJson?.length ? section.tableJson : node.tables || []
  return plans.map((table) => table.caption).filter(Boolean)
}

function buildTableDisplayMap() {
  const current = report.value
  const result: Record<string, string[]> = {}
  if (!current) return result

  let tableIndex = 0
  current.outline.forEach((node) => {
    const section = current.sections.find((item) => sameId(item.outlineNodeId, node.id))
    const tables = sectionTableNames(node, section)
    if (!tables.length) return

    result[String(node.id)] = tables.map((name) => {
      tableIndex += 1
      return `表${tableIndex} ${name}`
    })
  })

  return result
}

function locateStreamSection() {
  if (!streamSectionId.value) return
  selectSectionBySectionId(streamSectionId.value)
}

function selectSectionBySectionId(sectionId: EntityId) {
  const section = report.value?.sections.find((item) => sameId(item.id, sectionId))
  const node = report.value?.outline.find((item) => sameId(item.id, section?.outlineNodeId))
  if (section && node && isContentOutlineNode(report.value!.outline, node) && !sameId(section.outlineNodeId, selectedOutlineId.value)) {
    selectedOutlineId.value = section.outlineNodeId
  }
}

async function startGenerate(generationMode: GenerationMode) {
  await store.startGenerate(reportId, generationMode)
  ElMessage.info(generationMode === 'TEMPLATE' ? '模板正文生成任务已启动' : 'AI 正文生成任务已启动')
}

function backToOutline() {
  router.push(`/reports/${reportId}/outline`)
}

async function save() {
  if (!selectedSection.value) return
  await store.saveSection(reportId, selectedSection.value.id, draft.value)
  dirty.value = false
  ElMessage.success('章节已保存')
}

async function confirmRegenerate() {
  if (!selectedSection.value) return
  const sectionId = selectedSection.value.id
  await ElMessageBox.confirm('重新生成会覆盖当前章节正文。若内容已人工编辑，请先确认是否继续。', '重新生成章节', {
    confirmButtonText: '重新生成',
    cancelButtonText: '取消',
    type: 'warning',
  })
  await store.regenerateSection(reportId, sectionId)
  dirty.value = false
  draft.value = selectedSection.value?.contentMarkdown ?? ''
  ElMessage.success('章节重新生成任务已启动')
}
</script>

<style scoped>
.workspace-page {
  min-height: 100%;
}

.workspace-band {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) 220px 320px;
  gap: 18px;
  padding: 14px 18px;
  margin-bottom: 12px;
}

.workspace-band strong,
.workspace-band small {
  display: block;
  margin-top: 6px;
}

.workspace-status-badge {
  display: flex;
  align-items: center;
  margin-top: 10px;
}

.workspace-status-badge :deep(.status-badge) {
  gap: 10px;
  color: rgba(248, 251, 255, 0.72);
}

.workspace-layout {
  display: grid;
  grid-template-columns: 310px minmax(0, 1fr) 300px;
  align-items: start;
  gap: 16px;
}

.section-tree-surface,
.sticky-rail {
  position: sticky;
  top: 12px;
  align-self: start;
}

.tree-scroll {
  max-height: calc(100vh - 280px);
  overflow: auto;
  padding-bottom: 8px;
}

.editor-surface {
  min-height: calc(100vh - 230px);
}

.editor-title {
  align-items: flex-start;
}

.mode-switch {
  overflow: hidden;
  border: 1px solid rgba(30, 107, 255, 0.24);
  border-radius: 999px;
  box-shadow: 0 10px 22px rgba(30, 107, 255, 0.08);
}

.mode-switch :deep(.el-radio-button__inner) {
  min-width: 74px;
  height: 36px;
  border: 0;
  padding: 0 18px;
  font-size: 15px;
  font-weight: 800;
  line-height: 36px;
}

.single-editor {
  display: grid;
  gap: 14px;
  padding: 14px;
}

.markdown-textarea {
  min-height: calc(100vh - 355px);
}

.markdown-textarea :deep(.el-textarea__inner) {
  min-height: calc(100vh - 355px) !important;
  padding: 16px;
  font-family: "Cascadia Mono", "Microsoft YaHei UI", monospace;
  font-size: 15px;
  line-height: 1.75;
}

.markdown-reader {
  min-height: calc(100vh - 355px);
  padding: 18px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.74);
  color: var(--text-primary);
  line-height: 1.8;
}

.markdown-reader :deep(h1),
.markdown-reader :deep(h2),
.markdown-reader :deep(h3),
.markdown-reader :deep(h4) {
  margin: 0 0 12px;
  color: var(--text-primary);
  font-weight: 800;
}

.markdown-reader :deep(p) {
  margin: 0 0 14px;
}

.markdown-reader :deep(table) {
  width: 100%;
  margin: 14px 0;
  border-collapse: collapse;
  overflow: hidden;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
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

.markdown-reader :deep(code) {
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--bg-subtle);
}

.table-inspector {
  overflow: hidden;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
}

.mini-title {
  min-height: 48px;
  padding: 12px 14px 8px;
}

.empty-section-note {
  display: grid;
  gap: 14px;
  min-height: 340px;
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

.control-panel {
  display: grid;
  gap: 14px;
  padding: 16px;
}

@media (max-width: 1366px) {
  .workspace-layout {
    grid-template-columns: 290px minmax(0, 1fr);
  }

  .sticky-rail {
    position: static;
    grid-column: 1 / -1;
  }
}
</style>
