<template>
  <div class="page">
    <PageHeader
      eyebrow="REPORT INDEX"
      title="报告记录"
      description="查看历史报告、恢复未完成任务、重新导出 Word 文档。筛选条件会映射到后端分页查询参数。"
    >
      <el-button type="primary" @click="$router.push('/reports/new')">新建报告</el-button>
    </PageHeader>

    <section class="surface filter-bar">
      <el-input v-model="filters.subject" placeholder="报告主题" clearable />
      <el-input v-model="filters.powerPlant" placeholder="电厂" clearable />
      <el-input v-model="filters.specialty" placeholder="专业" clearable />
      <el-select v-model="filters.type" placeholder="报告类型" clearable>
        <el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-select v-model="filters.status" placeholder="报告状态" clearable>
        <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-input-number v-model="filters.year" placeholder="年份" :controls="false" />
      <el-button @click="applyFilters">查询</el-button>
    </section>

    <section class="surface table-surface">
      <el-table v-loading="store.loading" :data="store.list.items" class="report-table" empty-text="暂无报告记录">
        <el-table-column label="报告名称" min-width="240">
          <template #default="{ row }">
            <strong>{{ row.name }}</strong>
            <small>{{ row.subject }}</small>
          </template>
        </el-table-column>
        <el-table-column label="类型" min-width="150">
          <template #default="{ row }">{{ reportTypeLabels[row.type] }}</template>
        </el-table-column>
        <el-table-column label="专业 / 电厂" min-width="160">
          <template #default="{ row }">{{ row.specialty }} / {{ row.powerPlant }}</template>
        </el-table-column>
        <el-table-column prop="reportYear" label="年份" width="90" />
        <el-table-column label="状态" width="130">
          <template #default="{ row }"><StatusBadge :status="row.status" /></template>
        </el-table-column>
        <el-table-column label="进度" min-width="160">
          <template #default="{ row }">
            <el-progress :percentage="progress(row)" :show-text="false" :stroke-width="8" />
            <small>{{ row.completedSections }} / {{ row.totalSections || 0 }}</small>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="180">
          <template #default="{ row }">{{ new Date(row.updatedAt).toLocaleString() }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <div class="action-row record-action-row">
              <el-button size="small" @click="goNext(row)">查看</el-button>
              <el-button size="small" text type="danger" @click="confirmDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-row">
        <el-pagination
          layout="prev, pager, next, total"
          :current-page="store.list.page"
          :page-size="store.list.pageSize"
          :total="store.list.total"
          @current-change="(page: number) => store.fetchList({ page })"
        />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { listReportStatusOptions, listReportTypeOptions } from '@/api/reports'
import { useReportStore } from '@/stores/reports'
import type { Report, ReportStatus, ReportType } from '@/types/domain'
import { reportStatusLabels, reportTypeLabels } from '@/utils/labels'

const router = useRouter()
const store = useReportStore()

const filters = reactive<{
  subject: string
  powerPlant: string
  specialty: string
  type: ReportType | null
  status: ReportStatus | null
  year: number | null
}>({
  subject: '',
  powerPlant: '',
  specialty: '',
  type: null,
  status: null,
  year: null,
})

const typeOptions = ref(Object.entries(reportTypeLabels).map(([value, label]) => ({ value: value as ReportType, label })))
const statusOptions = ref(
  Object.entries(reportStatusLabels)
    .filter(([value]) => value !== 'DELETED')
    .map(([value, label]) => ({ value: value as ReportStatus, label })),
)

onMounted(async () => {
  await Promise.all([loadFilterOptions(), store.fetchList()])
})

async function loadFilterOptions() {
  const [types, statuses] = await Promise.all([listReportTypeOptions(), listReportStatusOptions()])
  typeOptions.value = types.map((item) => ({ value: item.code, label: item.label || reportTypeLabels[item.code] }))
  statusOptions.value = statuses
    .filter((item) => item.code !== 'DELETED')
    .map((item) => ({ value: item.code, label: item.label || reportStatusLabels[item.code] }))
}

function progress(report: Report) {
  if (!report.totalSections) return 0
  return Math.round((report.completedSections / report.totalSections) * 100)
}

function applyFilters() {
  store.fetchList({ ...filters, page: 1 })
}

function goNext(report: Report) {
  if (report.status === 'DRAFT' || report.status === 'OUTLINE_READY') router.push(`/reports/${report.id}/outline`)
  else if (report.status === 'EXPORTED') router.push(`/reports/${report.id}/view`)
  else router.push(`/reports/${report.id}/workspace`)
}

async function confirmDelete(report: Report) {
  await ElMessageBox.confirm(`删除后「${report.name}」将不再出现在记录列表中。`, '确认删除报告', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning',
  })
  await store.remove(report.id)
  ElMessage.success('已删除报告')
}
</script>

<style scoped>
.filter-bar {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) minmax(150px, 0.8fr) minmax(120px, 0.6fr) minmax(170px, 0.75fr) minmax(150px, 0.7fr) 120px 96px;
  gap: 12px;
  align-items: center;
  padding: 14px 16px;
  margin-bottom: 12px;
}

.filter-bar :deep(.el-input-number),
.filter-bar :deep(.el-button) {
  width: 100%;
}

.filter-bar :deep(.el-input-number .el-input__wrapper) {
  width: 100%;
}

.table-surface {
  padding: 0 0 12px;
}

.report-table {
  font-size: 15px;
}

.report-table :deep(.el-table__cell) {
  padding: 12px 0;
}

.report-table :deep(th.el-table__cell) {
  padding: 12px 0;
  font-size: 15px;
}

.report-table :deep(strong),
.report-table :deep(small) {
  display: block;
}

.report-table :deep(strong) {
  color: var(--text-primary);
  font-size: 16px;
  font-weight: 800;
  line-height: 1.35;
}

.report-table :deep(small) {
  margin-top: 5px;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.35;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  padding: 12px 16px 0;
}

.record-action-row {
  width: 100%;
  flex-wrap: nowrap;
  justify-content: center;
  gap: 12px;
}

.record-action-row :deep(.el-button + .el-button) {
  margin-left: 0;
}

.record-action-row :deep(.el-button) {
  flex: 0 0 auto;
  min-height: 34px;
  padding: 0 13px;
  font-size: 14px;
}

</style>
