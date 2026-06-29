<template>
  <div class="page">
    <PageHeader
      eyebrow="OUTLINE EDITOR"
      title="报告大纲"
      description="根据固定报告类型生成多级大纲，支持编辑、删除、顺序调整和自动编号。保存后进入正文流式生成。"
    >
      <el-button :loading="store.loading" @click="generate">生成大纲</el-button>
      <el-button :disabled="outline.length === 0" @click="save">保存大纲</el-button>
      <el-button type="primary" :disabled="outline.length === 0" @click="startContent">生成正文</el-button>
    </PageHeader>

    <div v-if="report" class="terminal-band outline-status">
      <div>
        <span class="terminal-label">REPORT / {{ report.id }}</span>
        <strong>{{ report.name }}</strong>
      </div>
      <StatusBadge :status="report.status" />
      <span>{{ report.subject }}</span>
    </div>

    <div v-if="report" class="split-grid">
      <section class="surface">
        <div class="surface-title">
          <div>
            <span class="eyebrow">TREE STRUCTURE</span>
            <h2>章节结构</h2>
          </div>
          <el-button size="small" @click="addChapter">新增章节</el-button>
        </div>
        <OutlineTree :nodes="outline" :selected-id="selectedId" @select="selectedId = $event" />
      </section>

      <section class="surface">
        <div class="surface-title">
          <div>
            <span class="eyebrow">NODE CONFIG</span>
            <h2>节点编辑</h2>
          </div>
          <div class="action-row">
            <el-button size="small" :disabled="!selectedNode" @click="move(-1)">上移</el-button>
            <el-button size="small" :disabled="!selectedNode" @click="move(1)">下移</el-button>
            <el-button size="small" :disabled="!selectedNode" @click="addChild">新增子节</el-button>
            <el-button size="small" text type="danger" :disabled="!selectedNode" @click="removeNode">删除</el-button>
          </div>
        </div>
        <div v-if="selectedNode" class="node-form">
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
          <el-alert type="info" :closable="false" show-icon title="前端会即时预览自动编号，最终编号以后端保存结果为准。" />
        </div>
        <el-empty v-else description="请选择左侧章节节点" />
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
import { moveOutlineNode, renumberOutline } from '@/utils/outline'

const route = useRoute()
const router = useRouter()
const store = useReportStore()
const reportId = String(route.params.id)
const outline = ref<OutlineNode[]>([])
const selectedId = ref<EntityId>()
const sameId = (a?: EntityId, b?: EntityId) => String(a) === String(b)

const report = computed(() => store.current)
const selectedNode = computed(() => outline.value.find((node) => sameId(node.id, selectedId.value)))

onMounted(async () => {
  const detail = await store.fetchDetail(reportId)
  outline.value = detail.outline.map((node) => ({ ...node }))
  selectedId.value = outline.value[0]?.id
})

async function generate() {
  const detail = await store.generateOutline(reportId)
  outline.value = detail.outline.map((node) => ({ ...node }))
  selectedId.value = outline.value[0]?.id
  ElMessage.success('大纲已生成')
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
    reportId,
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
    reportId,
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

function move(direction: -1 | 1) {
  if (!selectedId.value) return
  outline.value = moveOutlineNode(outline.value, selectedId.value, direction)
}

async function removeNode() {
  if (!selectedNode.value) return
  const node = selectedNode.value
  const childCount = outline.value.filter((item) => sameId(item.parentId, node.id)).length
  await ElMessageBox.confirm(
    childCount > 0 ? `该章节包含 ${childCount} 个子章节，删除后会一起移除。` : `确认删除「${node.title}」？`,
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
  await store.saveOutline(reportId, outline.value)
  ElMessage.success('大纲已保存')
}

async function startContent() {
  const saved = await store.saveOutline(reportId, outline.value)
  ElMessage.success('大纲已保存')
  router.push(`/reports/${saved?.id ?? reportId}/workspace`)
}
</script>

<style scoped>
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

.node-form {
  display: grid;
  gap: 14px;
  padding: 20px;
}
</style>