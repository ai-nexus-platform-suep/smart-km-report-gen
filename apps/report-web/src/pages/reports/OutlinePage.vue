<template>
  <div class="page outline-page">
    <PageHeader
      eyebrow="OUTLINE EDITOR"
      title="报告大纲"
      description="大纲按模板生成，支持章、节、子节等多级结构。表格属于章节内容，不作为独立章节节点。"
    >
      <el-button class="header-action" :loading="store.loading" @click="generate">重新生成大纲</el-button>
      <el-button class="header-action" :disabled="outline.length === 0" @click="save">保存大纲</el-button>
      <el-button class="header-action" type="primary" :disabled="outline.length === 0" @click="startContent">
        进入正文生成
      </el-button>
    </PageHeader>

    <div v-if="report" class="terminal-band outline-status">
      <div>
        <span class="terminal-label">REPORT / {{ report.id }}</span>
        <strong>{{ report.name }}</strong>
      </div>
      <StatusBadge :status="report.status" />
      <span>{{ report.subject }}</span>
    </div>

    <div v-if="report" class="outline-workspace">
      <section class="surface outline-preview-surface">
        <div class="surface-title">
          <div>
            <span class="eyebrow">OUTLINE PREVIEW</span>
            <h2>大纲预览</h2>
          </div>
          <el-button class="outline-action" @click="addChapter">新增章</el-button>
        </div>

        <div class="outline-tools">
          <el-select v-model="numberingMode" size="small">
            <el-option label="全局编号（表1，表2）" value="global" />
            <el-option label="章节编号（表1-1，表2-1）" value="section" />
          </el-select>
          <span>拖拽章节可调整顺序；点击箭头可收起子章节；表格会挂在对应章节下方。</span>
        </div>

        <div class="outline-tree-scroll">
          <OutlineTree
            :nodes="outline"
            :selected-id="selectedId"
            :table-items="tableDisplayMap"
            collapsible
            draggable
            @select="selectedId = $event"
            @move="handleMove"
          />
        </div>
      </section>

      <section class="surface outline-config-surface">
        <div class="surface-title">
          <div>
            <span class="eyebrow">NODE CONFIG</span>
            <h2>节点编辑</h2>
          </div>
          <div class="action-row outline-actions">
            <el-button class="outline-action" :disabled="!selectedNode" @click="addChild">新增子节</el-button>
            <el-button class="outline-action" :disabled="!canAttachTable" @click="addTableToNode">添加表格</el-button>
            <el-button class="delete-node-button" :disabled="!selectedNode" @click="removeNode">删除</el-button>
          </div>
        </div>

        <div v-if="selectedNode" class="node-form node-form-scroll">
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="一级标题只作为目录结构；正文和表格应挂在具体节或子节中。"
          />

          <el-form label-position="top">
            <el-form-item label="章节编号">
              <el-input :model-value="selectedNode.number" readonly />
            </el-form-item>
            <el-form-item label="章节标题">
              <el-input v-model="selectedNode.title" @input="renumber" />
            </el-form-item>
            <el-form-item label="生成提示">
              <el-input v-model="selectedNode.promptHint" type="textarea" :autosize="{ minRows: 6 }" />
            </el-form-item>
          </el-form>

          <div v-if="selectedTables.length" class="table-preview">
            <div class="surface-title mini-title">
              <div>
                <span class="eyebrow">TABLE STRUCTURE</span>
                <h3>本节表格</h3>
              </div>
            </div>
            <el-table :data="selectedTableRows" size="small">
              <el-table-column prop="label" label="编号" width="100" />
              <el-table-column prop="name" label="表名" />
              <el-table-column prop="note" label="说明" />
            </el-table>
          </div>
        </div>

        <el-empty v-else description="请选择左侧大纲节点" />
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import OutlineTree from '@/components/OutlineTree.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { useReportStore } from '@/stores/reports'
import type { EntityId, OutlineNode } from '@/types/domain'
import { moveOutlineNodeToTarget, renumberOutline } from '@/utils/outline'

const route = useRoute()
const router = useRouter()
const store = useReportStore()
const reportId = ref(String(route.params.id))
const outline = ref<OutlineNode[]>([])
const selectedId = ref<EntityId>()
const numberingMode = ref<'global' | 'section'>('global')
const sameId = (a?: EntityId, b?: EntityId) => String(a) === String(b)

const report = computed(() => store.current)
const selectedNode = computed(() => outline.value.find((node) => sameId(node.id, selectedId.value)))
const canAttachTable = computed(() => Boolean(selectedNode.value && selectedNode.value.level > 1))
const tableDisplayMap = computed(() => buildTableDisplayMap())
const selectedTables = computed(() => (selectedNode.value ? tableEntriesFor(selectedNode.value) : []))
const selectedTableRows = computed(() =>
  selectedTables.value.map((table) => ({
    label: table.label,
    name: table.name,
    note: '生成正文时作为本节内部表格输出，不作为独立章节',
  })),
)

onMounted(async () => {
  const detail = await store.fetchDetail(reportId.value)
  setOutline(detail.outline)
  if (!outline.value.length) {
    await generate(true)
  }
})

function setOutline(nodes: OutlineNode[]) {
  outline.value = normalizeOutline(nodes.map((node) => ({ ...node })))
  selectedId.value = outline.value[0]?.id
}

async function generate(auto = false) {
  const detail = await store.generateOutline(reportId.value)
  setOutline(detail.outline)
  ElMessage.success(auto ? '大纲已按模板自动生成' : '大纲已按模板重新生成')
}

function renumber() {
  outline.value = renumberOutline(outline.value)
}

function nextId() {
  return `local-${Date.now()}-${outline.value.length + 1}`
}

function addChapter() {
  outline.value.push({
    id: nextId(),
    reportId: reportId.value,
    level: 1,
    sortOrder: outline.value.filter((node) => !node.parentId).length + 1,
    number: '',
    title: '新增章节',
    promptHint: '请补充本章节生成提示。',
  })
  renumber()
  selectedId.value = outline.value[outline.value.length - 1]?.id
}

function addChild() {
  if (!selectedNode.value) return
  const parent = selectedNode.value
  outline.value.push({
    id: nextId(),
    reportId: reportId.value,
    parentId: parent.id,
    level: parent.level + 1,
    sortOrder: outline.value.filter((node) => sameId(node.parentId, parent.id)).length + 1,
    number: '',
    title: '新增子章节',
    promptHint: '请补充子章节生成提示。',
  })
  renumber()
  selectedId.value = outline.value[outline.value.length - 1]?.id
}

function addTableToNode() {
  if (!selectedNode.value) return
  if (selectedNode.value.level <= 1) {
    ElMessage.warning('表格应添加到具体节或子节下，不能直接挂在章标题下')
    return
  }
  const nextIndex = rawTableNames(selectedNode.value).length + 1
  selectedNode.value.promptHint = appendTableHint(selectedNode.value.promptHint, `检查结果 ${nextIndex}`)
}

function handleMove(payload: { dragId: EntityId; targetId: EntityId }) {
  outline.value = moveOutlineNodeToTarget(outline.value, payload.dragId, payload.targetId)
  selectedId.value = payload.dragId
}

async function removeNode() {
  if (!selectedNode.value) return
  const node = selectedNode.value
  const childCount = outline.value.filter((item) => sameId(item.parentId, node.id)).length
  await ElMessageBox.confirm(
    childCount > 0 ? `该章节包含 ${childCount} 个子节点，删除后会一并移除。` : `确认删除“${node.title}”？`,
    '确认删除章节',
    { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
  )
  const removeIds = new Set<string>([String(node.id)])
  let changed = true
  while (changed) {
    changed = false
    outline.value.forEach((item) => {
      if (item.parentId && removeIds.has(String(item.parentId)) && !removeIds.has(String(item.id))) {
        removeIds.add(String(item.id))
        changed = true
      }
    })
  }
  outline.value = renumberOutline(outline.value.filter((item) => !removeIds.has(String(item.id))))
  selectedId.value = outline.value[0]?.id
}

async function save() {
  const saved = await store.saveOutline(reportId.value, outline.value)
  if (saved?.id) {
    reportId.value = String(saved.id)
    outline.value = normalizeOutline(saved.outline.map((node) => ({ ...node })))
    if (String(route.params.id) !== reportId.value) router.replace(`/reports/${reportId.value}/outline`)
  }
  ElMessage.success('大纲已确认保存')
}

async function startContent() {
  const saved = await store.saveOutline(reportId.value, outline.value)
  if (saved?.id) {
    reportId.value = String(saved.id)
    outline.value = normalizeOutline(saved.outline.map((node) => ({ ...node })))
  }
  ElMessage.success('大纲已保存')
  router.push(`/reports/${reportId.value}/workspace`)
}

function normalizeOutline(nodes: OutlineNode[]) {
  const next: OutlineNode[] = []
  const pendingTables = new Map<string, string[]>()

  nodes.forEach((node) => {
    if (isLegacyTableNode(node) && node.parentId) {
      const key = String(node.parentId)
      const list = pendingTables.get(key) ?? []
      list.push(cleanLegacyTableName(node.title))
      pendingTables.set(key, list)
      return
    }
    next.push(node)
  })

  return renumberOutline(
    next.map((node) => {
      const additions = pendingTables.get(String(node.id)) ?? []
      if (!additions.length) return node
      return {
        ...node,
        promptHint: additions.reduce((hint, table) => appendTableHint(hint, table), node.promptHint || ''),
      }
    }),
  )
}

function isLegacyTableNode(node: OutlineNode) {
  return /^表格项[:：]?/.test(node.title) || /^TABLE\s+/i.test(node.title)
}

function cleanLegacyTableName(title: string) {
  return title.replace(/^表格项[:：]?/, '').replace(/^TABLE\s+/i, '').trim() || '检查结果'
}

function rawTableNames(node: OutlineNode) {
  return (node.promptHint || '')
    .split(/\r?\n/)
    .map((line) => /^表格[:：]\s*(.+)$/.exec(line.trim())?.[1]?.trim())
    .filter(Boolean) as string[]
}

function appendTableHint(promptHint = '', tableName: string) {
  const current = promptHint.trim()
  if (rawTableNames({ promptHint } as OutlineNode).includes(tableName)) return promptHint
  return `${current}${current ? '\n' : ''}表格：${tableName}`
}

function buildTableDisplayMap() {
  const result: Record<string, string[]> = {}
  let globalIndex = 0

  outline.value.forEach((node) => {
    const raw = rawTableNames(node)
    if (!raw.length) return
    result[String(node.id)] = raw.map((name, index) => {
      globalIndex += 1
      const label = numberingMode.value === 'global' ? `表${globalIndex}` : `表${node.number}-${index + 1}`
      return `${label} ${name}`
    })
  })

  return result
}

function tableEntriesFor(node: OutlineNode) {
  return (tableDisplayMap.value[String(node.id)] ?? []).map((label) => {
    const match = /^(表[\d.-]+)\s+(.+)$/.exec(label)
    return { label: match?.[1] || '表', name: match?.[2] || label }
  })
}
</script>

<style scoped>
.outline-page {
  min-height: 100%;
}

.outline-status {
  display: grid;
  grid-template-columns: minmax(260px, 1fr) auto minmax(300px, 0.8fr);
  align-items: center;
  gap: 20px;
  min-height: 86px;
  padding: 18px 20px;
  margin-bottom: 16px;
}

.outline-status strong {
  display: block;
  margin-top: 6px;
}

.outline-workspace {
  display: grid;
  grid-template-columns: minmax(420px, 0.9fr) minmax(520px, 1.1fr);
  align-items: start;
  gap: 16px;
}

.outline-preview-surface,
.outline-config-surface {
  max-height: calc(100vh - 230px);
}

.outline-tools {
  display: grid;
  grid-template-columns: minmax(220px, 320px) minmax(0, 1fr);
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-default);
}

.outline-tools span {
  color: var(--text-muted);
  font-size: 13px;
}

.outline-tree-scroll,
.node-form-scroll {
  max-height: calc(100vh - 345px);
  overflow: auto;
}

.outline-config-surface {
  position: sticky;
  top: 12px;
  align-self: start;
}

.node-form {
  display: grid;
  gap: 14px;
  padding: 20px;
}

.outline-actions {
  gap: 12px;
}

.header-action,
.outline-action,
.delete-node-button {
  min-height: 38px;
  padding: 0 18px;
  font-weight: 800;
}

.delete-node-button {
  border: 1px solid rgba(220, 38, 38, 0.34);
  border-radius: 999px;
  color: #ffffff;
  background:
    linear-gradient(135deg, rgba(248, 113, 113, 0.9), rgba(220, 38, 38, 0.96)),
    #dc2626;
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.22),
    0 12px 24px rgba(220, 38, 38, 0.18);
}

.delete-node-button:hover {
  border-color: rgba(220, 38, 38, 0.58);
  color: #ffffff;
  transform: translateY(-1px);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.28),
    0 16px 30px rgba(220, 38, 38, 0.24);
}

.delete-node-button.is-disabled {
  color: rgba(255, 255, 255, 0.72);
  opacity: 0.45;
}

.table-preview {
  overflow: hidden;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
}

.mini-title {
  min-height: 48px;
  padding: 12px 14px 8px;
}
</style>
