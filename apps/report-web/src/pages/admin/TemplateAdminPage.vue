<template>
  <AuthGuard require-admin>
    <div class="page">
      <PageHeader
        eyebrow="TEMPLATE CENTER"
        title="模板管理"
        description="维护报告模板文件、版本与启用状态，为后续 DOCX 导出提供统一版式基础。"
      >
        <el-button type="primary" @click="openUpload">上传模板</el-button>
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
          <el-table-column label="操作" width="430" fixed="right">
            <template #default="{ row }">
              <div class="action-row template-actions">
                <el-button size="small" :icon="ViewIcon" @click="openPreview(row)">预览</el-button>
                <el-button size="small" @click="openConfig(row)">配置</el-button>
                <el-button size="small" @click="openReplace(row)">替换</el-button>
                <el-button size="small" :icon="DownloadIcon" :loading="sameId(downloadingId, row.id)" @click="download(row)">下载</el-button>
                <el-button size="small" text type="danger" @click="remove(row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <el-dialog v-model="uploadVisible" title="上传模板文件" width="640px">
        <el-form ref="uploadFormRef" :model="uploadForm" :rules="uploadRules" label-position="top">
          <el-form-item label="模板名称" prop="name">
            <el-input v-model="uploadForm.name" placeholder="如：迎峰度夏检查报告模板" />
          </el-form-item>
          <div class="upload-grid">
            <el-form-item label="报告类型" prop="reportType">
              <el-select v-model="uploadForm.reportType">
                <el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="版本" prop="version">
              <el-input v-model="uploadForm.version" placeholder="v1.0" />
            </el-form-item>
          </div>
          <el-form-item label="启用状态">
            <el-switch v-model="uploadForm.enabled" active-text="启用" inactive-text="停用" />
          </el-form-item>
          <el-form-item label="模板文件">
            <div class="file-picker">
              <input ref="fileInputRef" type="file" accept=".docx" @change="handleFileChange" />
              <span>{{ uploadFile?.name || "请选择 .docx 模板文件" }}</span>
            </div>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="uploadVisible = false">取消</el-button>
          <el-button type="primary" :loading="uploading" @click="submitUpload">上传</el-button>
        </template>
      </el-dialog>

      <el-dialog v-model="replaceVisible" title="替换模板文件" width="560px">
        <div class="replace-panel">
          <strong>{{ currentTemplate?.name }}</strong>
          <p>仅替换 DOCX 模板文件，模板名称、报告类型、版本和启用状态仍通过列表元信息维护。</p>
          <div class="file-picker">
            <input ref="replaceFileInputRef" type="file" accept=".docx" @change="handleReplaceFileChange" />
            <span>{{ replaceFile?.name || "请选择新的 .docx 模板文件" }}</span>
          </div>
        </div>
        <template #footer>
          <el-button @click="replaceVisible = false">取消</el-button>
          <el-button type="primary" :loading="replacing" @click="submitReplace">替换文件</el-button>
        </template>
      </el-dialog>

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
            <el-form-item label="页脚说明">
              <el-input v-model="config.footer" placeholder="可选" />
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
          <el-button type="primary" :loading="configSaving" @click="saveConfig">保存配置</el-button>
        </template>
      </el-dialog>

      <el-dialog v-model="previewVisible" title="模板内容预览" width="920px">
        <div v-loading="previewLoading" class="preview-panel">
          <template v-if="previewTemplate">
            <div class="preview-meta-grid">
              <div class="data-line">
                <span>模板名称</span>
                <strong>{{ previewTemplate.name }}</strong>
              </div>
              <div class="data-line">
                <span>报告类型</span>
                <strong>{{ reportTypeLabels[previewTemplate.reportType] }}</strong>
              </div>
              <div class="data-line">
                <span>原始文件</span>
                <strong>{{ previewTemplate.originalFileName || "-" }}</strong>
              </div>
              <div class="data-line">
                <span>文件大小</span>
                <strong>{{ formatBytes(previewTemplate.fileSize || 0) }}</strong>
              </div>
              <div class="data-line">
                <span>存储位置</span>
                <strong>{{ previewTemplate.objectName || previewTemplate.filePath || "-" }}</strong>
              </div>
              <div class="data-line">
                <span>更新时间</span>
                <strong>{{ previewTemplate.updatedAt ? new Date(previewTemplate.updatedAt).toLocaleString() : "-" }}</strong>
              </div>
            </div>

            <div class="docx-preview">
              <span class="terminal-label">WORD CONTENT</span>
              <template v-if="previewDoc.sections.length">
                <section v-for="section in previewDoc.sections" :key="section.name" class="docx-section">
                  <h3>{{ section.name }}</h3>
                  <template v-for="(block, index) in section.blocks" :key="`${section.name}-${index}`">
                    <p v-if="block.type === 'paragraph'" class="docx-paragraph">{{ block.text }}</p>
                    <table v-else class="docx-table">
                      <tbody>
                        <tr v-for="(row, rowIndex) in block.rows" :key="rowIndex">
                          <td v-for="(cell, cellIndex) in row" :key="cellIndex">{{ cell }}</td>
                        </tr>
                      </tbody>
                    </table>
                  </template>
                </section>
              </template>
              <el-empty v-else :description="previewError || '未读取到模板正文内容'" />
            </div>
          </template>
        </div>
        <template #footer>
          <el-button @click="previewVisible = false">关闭</el-button>
          <el-button v-if="previewTemplate" :icon="DownloadIcon" type="primary" @click="download(previewTemplate)">
            下载模板文件
          </el-button>
        </template>
      </el-dialog>
    </div>
  </AuthGuard>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from "element-plus";
import { Download as DownloadIcon, View as ViewIcon } from "@element-plus/icons-vue";
import AuthGuard from "@platform/ui/src/components/AuthGuard.vue";
import PageHeader from "@/components/PageHeader.vue";
import {
  deleteTemplate,
  downloadTemplate,
  getTemplate,
  getTemplateConfig,
  listTemplates,
  replaceTemplateFile,
  updateTemplate,
  updateTemplateConfig,
  uploadTemplate,
  type TemplateStyleConfig
} from "@/api/admin";
import type { ReportType, TemplateRecord } from "@/types/domain";
import { assertValidDocxBlob } from "@/utils/docx";
import { readDocxPreview, type DocxPreview } from "@/utils/docxPreview";
import { formatBytes, reportTypeLabels } from "@/utils/labels";

const templates = ref<TemplateRecord[]>([]);
const loading = ref(false);
const uploadVisible = ref(false);
const uploading = ref(false);
const replaceVisible = ref(false);
const replacing = ref(false);
const configVisible = ref(false);
const configSaving = ref(false);
const previewVisible = ref(false);
const previewLoading = ref(false);
const currentTemplate = ref<TemplateRecord>();
const previewTemplate = ref<TemplateRecord>();
const previewDoc = ref<DocxPreview>({ sections: [] });
const previewError = ref("");
const downloadingId = ref<TemplateRecord["id"]>();
const uploadFormRef = ref<FormInstance>();
const fileInputRef = ref<HTMLInputElement>();
const replaceFileInputRef = ref<HTMLInputElement>();
const uploadFile = ref<File>();
const replaceFile = ref<File>();

const uploadForm = reactive({
  name: "",
  reportType: "SUMMER_PEAK_CHECK" as ReportType,
  version: "v1.0",
  enabled: true
});

const config = reactive<TemplateStyleConfig>({ titleSize: 18, bodySize: 12, lineHeight: 1.5, header: "示范电厂", footer: "" });

const typeOptions = Object.entries(reportTypeLabels).map(([value, label]) => ({ value, label }));
const sameId = (a?: TemplateRecord["id"], b?: TemplateRecord["id"]) => String(a) === String(b);

const uploadRules: FormRules = {
  name: [{ required: true, message: "请输入模板名称", trigger: "blur" }],
  reportType: [{ required: true, message: "请选择报告类型", trigger: "change" }],
  version: [{ required: true, message: "请输入版本号", trigger: "blur" }]
};

onMounted(load);

async function load() {
  loading.value = true;
  try {
    templates.value = await listTemplates();
  } finally {
    loading.value = false;
  }
}

function openUpload() {
  uploadForm.name = "";
  uploadForm.reportType = "SUMMER_PEAK_CHECK";
  uploadForm.version = "v1.0";
  uploadForm.enabled = true;
  uploadFile.value = undefined;
  if (fileInputRef.value) fileInputRef.value.value = "";
  uploadVisible.value = true;
}

function handleFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  uploadFile.value = input.files?.[0];
}

function isDocxFile(file?: File) {
  return Boolean(file && /\.docx$/i.test(file.name));
}

function openReplace(template: TemplateRecord) {
  currentTemplate.value = template;
  replaceFile.value = undefined;
  if (replaceFileInputRef.value) replaceFileInputRef.value.value = "";
  replaceVisible.value = true;
}

function handleReplaceFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  replaceFile.value = input.files?.[0];
}

async function submitUpload() {
  await uploadFormRef.value?.validate();
  if (!uploadFile.value) {
    ElMessage.warning("请选择模板文件");
    return;
  }
  if (!isDocxFile(uploadFile.value)) {
    ElMessage.warning("模板文件仅支持 .docx");
    return;
  }
  uploading.value = true;
  try {
    await uploadTemplate({ ...uploadForm, file: uploadFile.value });
    await load();
    uploadVisible.value = false;
    ElMessage.success("模板已上传");
  } finally {
    uploading.value = false;
  }
}

async function submitReplace() {
  if (!currentTemplate.value) return;
  if (!replaceFile.value) {
    ElMessage.warning("请选择新的模板文件");
    return;
  }
  if (!isDocxFile(replaceFile.value)) {
    ElMessage.warning("模板文件仅支持 .docx");
    return;
  }
  replacing.value = true;
  try {
    await replaceTemplateFile(currentTemplate.value.id, replaceFile.value);
    await load();
    replaceVisible.value = false;
    ElMessage.success("模板文件已替换");
  } finally {
    replacing.value = false;
  }
}

async function saveTemplate(template: TemplateRecord) {
  await updateTemplate(template);
  ElMessage.success("模板状态已保存");
}

async function openConfig(template: TemplateRecord) {
  currentTemplate.value = template;
  Object.assign(config, await getTemplateConfig(template.id));
  configVisible.value = true;
}

async function openPreview(template: TemplateRecord) {
  previewVisible.value = true;
  previewLoading.value = true;
  previewTemplate.value = template;
  previewDoc.value = { sections: [] };
  previewError.value = "";
  try {
    const [detail, file] = await Promise.all([getTemplate(template.id), downloadTemplate(template.id)]);
    previewTemplate.value = detail;
    await assertValidDocxBlob(file.blob);
    previewDoc.value = await readDocxPreview(file.blob);
  } catch (error) {
    previewError.value = error instanceof Error ? error.message : "模板预览失败";
    ElMessage.error(previewError.value);
  } finally {
    previewLoading.value = false;
  }
}

async function saveConfig() {
  if (!currentTemplate.value) return;
  configSaving.value = true;
  try {
    await updateTemplateConfig(currentTemplate.value.id, config);
    configVisible.value = false;
    ElMessage.success("模板配置已保存");
  } finally {
    configSaving.value = false;
  }
}

async function download(template: TemplateRecord) {
  downloadingId.value = template.id;
  try {
    const { blob, fileName } = await downloadTemplate(template.id);
    await assertValidDocxBlob(blob);
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = fileName || `${template.name}.docx`;
    link.style.display = "none";
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    ElMessage.success("模板下载已开始");
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "模板下载失败");
  } finally {
    downloadingId.value = undefined;
  }
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

.template-actions {
  flex-wrap: nowrap;
  justify-content: center;
  gap: 8px;
}

.template-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.upload-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 150px;
  gap: 14px;
}

.file-picker {
  display: grid;
  gap: 8px;
  width: 100%;
  padding: 12px;
  border: 1px dashed rgba(30, 107, 255, 0.42);
  border-radius: var(--radius-md);
  background: rgba(30, 107, 255, 0.04);
}

.file-picker span {
  color: var(--text-secondary);
  font-size: 13px;
}

.replace-panel {
  display: grid;
  gap: 12px;
}

.replace-panel p {
  margin: 0;
  color: var(--text-secondary);
  line-height: 1.7;
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

.preview-panel {
  min-height: 320px;
}

.preview-meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 22px;
  margin-bottom: 18px;
}

.docx-preview {
  display: grid;
  gap: 10px;
  padding: 16px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: rgba(247, 250, 254, 0.76);
}

.docx-section {
  display: grid;
  gap: 10px;
  max-height: 420px;
  overflow: auto;
  padding: 14px;
  border: 1px solid rgba(132, 151, 176, 0.22);
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.72);
}

.docx-section h3 {
  margin: 0 0 4px;
  color: var(--text-primary);
  font-size: 17px;
}

.docx-paragraph {
  margin: 0;
  color: var(--text-primary);
  line-height: 1.8;
  white-space: pre-wrap;
}

.docx-table {
  width: 100%;
  border-collapse: collapse;
  background: #fff;
}

.docx-table td {
  padding: 8px 10px;
  border: 1px solid var(--border-default);
  color: var(--text-primary);
  line-height: 1.55;
}
</style>
