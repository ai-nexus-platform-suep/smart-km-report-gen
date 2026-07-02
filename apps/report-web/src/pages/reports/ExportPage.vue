<template>
  <div class="page">
    <PageHeader
      eyebrow="DOCX EXPORT"
      title="导出与下载"
      description="基于已保存报告数据生成 Word 文档，不重新执行 AI 生成；导出前检查空章节、失败章节与未保存内容。"
    >
      <el-button @click="$router.push('/reports')">返回历史记录</el-button>
      <el-button type="primary" :loading="exporting" :disabled="!report" @click="exportFile">导出 DOCX</el-button>
    </PageHeader>

    <div v-if="report" class="split-grid">
      <section class="surface">
        <div class="surface-title">
          <div>
            <span class="eyebrow">EXPORT CHECK</span>
            <h2>导出前检查</h2>
          </div>
          <StatusBadge :status="report.status" />
        </div>

        <div class="check-grid">
          <div class="check-item">
            <span class="status-dot" :class="checks.empty ? 'warning' : 'success'" />
            <strong>空章节</strong>
            <span>{{ checks.empty }} 项</span>
          </div>
          <div class="check-item">
            <span class="status-dot" :class="checks.failed ? 'danger' : 'success'" />
            <strong>失败章节</strong>
            <span>{{ checks.failed }} 项</span>
          </div>
          <div class="check-item">
            <span class="status-dot" :class="checks.unsaved ? 'warning' : 'success'" />
            <strong>人工编辑章节</strong>
            <span>{{ checks.unsaved }} 项</span>
          </div>
        </div>

        <el-alert
          type="info"
          :closable="false"
          title="正式后端会按模板配置统一字体、字号、标题层级、段落间距、表格样式、页眉页脚。"
        />
      </section>

      <section class="surface">
        <div class="surface-title">
          <div>
            <span class="eyebrow">FILE LIST</span>
            <h2>已生成文件</h2>
          </div>
        </div>
        <div class="file-list">
          <div v-for="file in report.files" :key="file.id" class="file-row">
            <div>
              <strong>{{ file.fileName }}</strong>
              <span>{{ formatBytes(file.fileSize) }} / {{ new Date(file.createdAt).toLocaleString() }}</span>
            </div>
            <el-button size="small" @click="download(file.id, file.fileName)">下载</el-button>
          </div>
          <el-empty v-if="report.files.length === 0" description="暂无导出文件" />
        </div>
      </section>
    </div>

    <el-empty v-else description="报告不存在或加载中" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { exportPrecheck } from '@/api/reports'
import { useReportStore } from '@/stores/reports'
import type { EntityId } from '@/types/domain'
import { formatBytes } from '@/utils/labels'

const route = useRoute()
const store = useReportStore()
const reportId = String(route.params.id)
const exporting = ref(false)
const report = computed(() => store.current)
const checks = computed(() => (report.value ? exportPrecheck(report.value) : { empty: 0, failed: 0, unsaved: 0 }))

onMounted(async () => {
  await store.fetchDetail(reportId)
})

async function exportFile() {
  if (!report.value) return
  exporting.value = true
  try {
    const file = await store.exportDocx(reportId)
    ElMessage.success(`DOCX 已生成：${file.fileName}`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'DOCX 导出失败')
  } finally {
    exporting.value = false
  }
}

async function download(fileId: EntityId, fileName: string) {
  try {
    await store.downloadFile(reportId, fileId, fileName)
    ElMessage.success('下载已开始')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'DOCX 下载失败')
  }
}
</script>

<style scoped>
.check-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  padding: 20px;
}

.check-item {
  display: grid;
  grid-template-columns: 8px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 16px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
}

.file-list {
  display: grid;
  gap: 10px;
  padding: 16px;
}

.file-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 14px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
}

.file-row strong,
.file-row span {
  display: block;
}

.file-row span {
  margin-top: 4px;
  color: var(--text-muted);
}
</style>
