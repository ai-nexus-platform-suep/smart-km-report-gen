<template>
  <AuthGuard require-admin>
    <div class="page">
      <PageHeader
        eyebrow="TEMPLATE CENTER"
        title="模板管理"
        description="维护报告模板、版本与启用状态，为后续 DOCX 导出提供统一版式基础。"
      >
        <el-button type="primary" @click="add">新增模板</el-button>
      </PageHeader>

      <section class="surface table-surface">
        <el-table v-loading="loading" :data="templates" row-key="id" class="admin-table" empty-text="暂无模板">
          <el-table-column label="模板名称" min-width="220">
            <template #default="{ row }">
              <strong>{{ row.name }}</strong>
              <small>{{ row.version }}</small>
            </template>
          </el-table-column>
          <el-table-column label="报告类型" min-width="170">
            <template #default="{ row }">{{ reportTypeLabels[row.reportType] }}</template>
          </el-table-column>
          <el-table-column label="启用" width="100">
            <template #default="{ row }">
              <el-switch v-model="row.enabled" @change="() => saveTemplate(row)" />
            </template>
          </el-table-column>
          <el-table-column prop="createdBy" label="上传人" width="130" />
          <el-table-column label="创建时间" min-width="180">
            <template #default="{ row }">{{ new Date(row.createdAt).toLocaleString() }}</template>
          </el-table-column>
          <el-table-column label="操作" width="190" fixed="right">
            <template #default="{ row }">
              <div class="action-row">
                <el-button size="small" @click="openConfig(row)">配置</el-button>
                <el-button size="small" text type="danger" @click="remove(row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <el-dialog v-model="configVisible" title="模板结构化配置" width="760px">
        <div class="config-grid">
          <el-form label-position="top">
            <el-form-item label="一级标题字号">
              <el-input-number v-model="config.titleSize" :min="12" :max="32" />
            </el-form-item>
            <el-form-item label="正文字号">
              <el-input-number v-model="config.bodySize" :min="9" :max="18" />
            </el-form-item>
            <el-form-item label="段落行距">
              <el-input-number v-model="config.lineHeight" :min="1" :max="3" :step="0.1" />
            </el-form-item>
            <el-form-item label="页眉单位名称">
              <el-input v-model="config.header" />
            </el-form-item>
          </el-form>

          <div class="template-preview">
            <span class="terminal-label">DOCX PREVIEW</span>
            <h3 :style="{ fontSize: `${config.titleSize}px` }">一级标题示例</h3>
            <p :style="{ fontSize: `${config.bodySize}px`, lineHeight: config.lineHeight }">
              正文段落示例，实际导出以后端 DOCX 渲染结果为准。
            </p>
            <table>
              <tbody>
                <tr>
                  <td>表头</td>
                  <td>表格样式</td>
                </tr>
                <tr>
                  <td>内容</td>
                  <td>统一边框</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
        <template #footer>
          <el-button @click="configVisible = false">取消</el-button>
          <el-button type="primary" @click="saveConfig">保存配置</el-button>
        </template>
      </el-dialog>
    </div>
  </AuthGuard>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import AuthGuard from "@platform/ui/src/components/AuthGuard.vue";
import PageHeader from "@/components/PageHeader.vue";
import { addTemplate, deleteTemplate, listTemplates, updateTemplate } from "@/api/admin";
import type { TemplateRecord } from "@/types/domain";
import { reportTypeLabels } from "@/utils/labels";

const templates = ref<TemplateRecord[]>([]);
const loading = ref(false);
const configVisible = ref(false);
const currentTemplate = ref<TemplateRecord>();
const config = reactive({ titleSize: 18, bodySize: 12, lineHeight: 1.5, header: "示范电厂" });

onMounted(load);

async function load() {
  loading.value = true;
  try {
    templates.value = await listTemplates();
  } finally {
    loading.value = false;
  }
}

async function add() {
  await addTemplate({
    name: `新模板 ${templates.value.length + 1}`,
    reportType: "SUMMER_PEAK_CHECK",
    version: "v1.0",
    enabled: true
  });
  await load();
  ElMessage.success("模板已新增");
}

async function saveTemplate(template: TemplateRecord) {
  await updateTemplate(template);
  ElMessage.success("模板状态已保存");
}

function openConfig(template: TemplateRecord) {
  currentTemplate.value = template;
  configVisible.value = true;
}

function saveConfig() {
  configVisible.value = false;
  ElMessage.success(`${currentTemplate.value?.name ?? "模板"}配置已保存`);
}

async function remove(template: TemplateRecord) {
  try {
    await ElMessageBox.confirm(`删除后「${template.name}」将不再用于报告生成。`, "确认删除模板", {
      confirmButtonText: "删除",
      cancelButtonText: "取消",
      type: "warning"
    });
    await deleteTemplate(template.id);
    await load();
    ElMessage.success("模板已删除");
  } catch {
    // User cancelled.
  }
}
</script>

<style scoped>
.table-surface {
  padding: 0;
}

.admin-table {
  font-size: 15px;
}

.admin-table :deep(.el-table__cell) {
  padding: 13px 0;
}

.admin-table :deep(th.el-table__cell) {
  padding: 12px 0;
  font-size: 15px;
}

.admin-table :deep(strong),
.admin-table :deep(small) {
  display: block;
}

.admin-table :deep(strong) {
  font-size: 16px;
  line-height: 1.35;
}

.admin-table :deep(small) {
  margin-top: 5px;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.35;
}

.admin-table :deep(.el-button) {
  min-height: 34px;
  padding: 0 13px;
  font-size: 14px;
}

.config-grid {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 22px;
}

.template-preview {
  padding: 18px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background:
    linear-gradient(135deg, rgba(30, 107, 255, 0.06), transparent 42%),
    var(--bg-surface);
}

.template-preview h3 {
  margin: 14px 0 10px;
}

.template-preview table {
  width: 100%;
  border-collapse: collapse;
}

.template-preview td {
  padding: 8px;
  border: 1px solid var(--border-default);
}
</style>
