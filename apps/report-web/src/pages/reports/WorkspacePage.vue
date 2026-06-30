<template>
  <div class="page">
    <PageHeader
      eyebrow="AI GENERATION TERMINAL"
      title="报告生成工作台"
      description="按章节流式生成正文，实时查看进度，生成后可在线编辑 Markdown 正文。"
    >
      <el-button @click="$router.push(`/reports/${reportId}/outline`)">返回大纲</el-button>
      <el-button type="primary" :loading="store.streaming" @click="startGenerate">启动正文生成</el-button>
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

    <div v-if="report" class="three-column">
      <section class="surface">
        <div class="surface-title">
          <div>
            <span class="eyebrow">SECTION TREE</span>
            <h2>章节目录</h2>
          </div>
        </div>
        <OutlineTree
          :nodes="report.outline"
          :sections="report.sections"
          :selected-id="selectedOutlineId"
          @select="selectOutline"
        />
      </section>

      <section class="surface editor-surface">
        <div class="surface-title">
          <div>
            <span class="eyebrow">CONTENT EDITOR</span>
            <h2>{{ selectedSection?.number }} {{ selectedSection?.title }}</h2>
          </div>
          <div class="action-row">
            <StatusBadge v-if="selectedSection" :status="selectedSection.status" type="section" />
            <el-button size="small" :disabled="!dirty || selectedSection?.status === 'GENERATING'" @click="save">
              保存章节
            </el-button>
          </div>
        </div>

        <div v-if="selectedSection" class="editor-grid">
          <el-input
            v-model="draft"
            type="textarea"
            :disabled="selectedSection.status === 'GENERATING'"
            :autosize="{ minRows: 12, maxRows: 12 }"
            placeholder="章节正文将随生成流写入，也可以在生成完成后编辑。"
            @input="dirty = true"
          />
          <div class="preview">
            <span class="terminal-label">PREVIEW</span>
            <pre>{{ preview }}</pre>
          </div>
        </div>
      </section>

      <section class="surface right-rail">
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
            <span>当前章节</span>
            <strong>{{ selectedSection?.number || '-' }}</strong>
          </div>
          <div class="data-line">
            <span>内容版本</span>
            <strong>v{{ selectedSection?.version || 0 }}</strong>
          </div>
          <el-alert v-if="dirty" type="warning" :closable="false" title="当前章节有未保存修改，离开前请保存。" />
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
import { useRoute } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import OutlineTree from '@/components/OutlineTree.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { useReportStore } from '@/stores/reports'
import type { EntityId } from '@/types/domain'
import { sanitizeMarkdown } from '@/utils/markdown'

const route = useRoute()
const store = useReportStore()
const reportId = String(route.params.id)
const selectedOutlineId = ref<EntityId>()
const sameId = (a?: EntityId, b?: EntityId) => String(a) === String(b)
const draft = ref('')
const dirty = ref(false)

const report = computed(() => store.current)
const selectedSection = computed(() =>
  report.value?.sections.find((section) => sameId(section.outlineNodeId, selectedOutlineId.value)),
)
const preview = computed(() => sanitizeMarkdown(draft.value))
const canExport = computed(() => report.value?.status === 'CONTENT_READY' || report.value?.status === 'EXPORTED')

onMounted(async () => {
  const detail = await store.fetchDetail(reportId)
  selectedOutlineId.value = detail.outline[0]?.id
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
    selectSectionBySectionId(event.sectionId)
  },
)

function selectOutline(id: EntityId) {
  selectedOutlineId.value = id
}

function selectSectionBySectionId(sectionId: EntityId) {
  const section = report.value?.sections.find((item) => sameId(item.id, sectionId))
  if (section && !sameId(section.outlineNodeId, selectedOutlineId.value)) {
    selectedOutlineId.value = section.outlineNodeId
  }
}

async function startGenerate() {
  await store.startGenerate(reportId)
  ElMessage.info('正文生成任务已启动')
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
  ElMessage.success('章节已重新生成')
}
</script>

<style scoped>
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

.editor-surface {
  min-height: 0;
}

.editor-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(300px, 0.8fr);
  gap: 12px;
  padding: 14px;
}

.preview {
  min-height: 300px;
  padding: 14px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: #fbfcfe;
}

.preview pre {
  margin: 12px 0 0;
  color: var(--text-primary);
  font-family: var(--font-body);
  line-height: 1.7;
  white-space: pre-wrap;
}

.control-panel {
  display: grid;
  gap: 14px;
  padding: 16px;
}
</style>
