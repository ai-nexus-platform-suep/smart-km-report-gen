<template>
  <AuthGuard require-admin>
    <div class="page">
      <PageHeader
        eyebrow="ASSET CENTER"
        title="素材管理"
        description="维护后端素材库文件，供报告生成服务按分类检索使用。"
      >
        <el-button :icon="DocumentAdd" :loading="importing" @click="importSeeds">导入 seed</el-button>
        <el-button :icon="UploadIcon" type="primary" @click="openUpload">上传素材</el-button>
      </PageHeader>

      <section class="surface asset-filter-bar">
        <el-input v-model="filters.keyword" placeholder="素材名称 / 文件名 / 标签" clearable />
        <el-select v-model="filters.category" placeholder="素材分类" clearable>
          <el-option v-for="item in categories" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="filters.enabled" placeholder="启用状态" clearable>
          <el-option label="启用" :value="true" />
          <el-option label="停用" :value="false" />
        </el-select>
        <el-button :icon="Search" type="primary" @click="applyFilters">查询</el-button>
        <el-button :icon="Refresh" @click="resetFilters">重置</el-button>
      </section>

      <section class="surface table-surface">
        <el-table v-loading="loading" :data="assetPage.items" row-key="id" class="admin-table" empty-text="暂无素材">
          <el-table-column label="素材名称" min-width="240">
            <template #default="{ row }">
              <strong>{{ row.name }}</strong>
              <small>{{ row.originalFileName }}</small>
            </template>
          </el-table-column>
          <el-table-column label="分类" width="130">
            <template #default="{ row }">
              <el-tag effect="plain">{{ categoryText(row) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="文件" min-width="150">
            <template #default="{ row }">
              <span class="mono file-type">{{ row.fileType || "-" }}</span>
              <small>{{ formatBytes(row.fileSize || 0) }}</small>
            </template>
          </el-table-column>
          <el-table-column label="说明 / 标签" min-width="260">
            <template #default="{ row }">
              <span class="description-text">{{ row.description || "—" }}</span>
              <div v-if="splitTags(row.tags).length" class="tag-list">
                <el-tag v-for="tag in splitTags(row.tags)" :key="tag" size="small" effect="plain">{{ tag }}</el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="启用" width="100">
            <template #default="{ row }">
              <el-switch v-model="row.enabled" @change="() => saveEnabled(row)" />
            </template>
          </el-table-column>
          <el-table-column label="上传信息" min-width="190">
            <template #default="{ row }">
              <strong>{{ row.createdBy || "-" }}</strong>
              <small>{{ new Date(row.updatedAt || row.createdAt).toLocaleString() }}</small>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="380" fixed="right">
            <template #default="{ row }">
              <div class="action-row asset-actions">
                <el-button size="small" :icon="ViewIcon" @click="openDetail(row)">查看</el-button>
                <el-button size="small" :icon="Edit" @click="openEdit(row)">编辑</el-button>
                <el-button size="small" :icon="DownloadIcon" @click="download(row)">下载</el-button>
                <el-button size="small" text type="danger" :icon="DeleteIcon" @click="remove(row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
        <div class="pagination-row">
          <el-pagination
            layout="prev, pager, next, total"
            :current-page="assetPage.page"
            :page-size="assetPage.pageSize"
            :total="assetPage.total"
            @current-change="changePage"
          />
        </div>
      </section>

      <el-dialog v-model="uploadVisible" title="上传素材" width="680px">
        <el-form ref="uploadFormRef" :model="uploadForm" :rules="assetRules" label-position="top">
          <el-form-item label="素材名称" prop="name">
            <el-input v-model="uploadForm.name" placeholder="如：电力行业标准" />
          </el-form-item>
          <div class="form-grid">
            <el-form-item label="素材分类" prop="category">
              <el-select v-model="uploadForm.category">
                <el-option v-for="item in categories" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="启用状态">
              <el-switch v-model="uploadForm.enabled" active-text="启用" inactive-text="停用" />
            </el-form-item>
          </div>
          <el-form-item label="描述">
            <el-input v-model="uploadForm.description" type="textarea" :autosize="{ minRows: 3 }" />
          </el-form-item>
          <el-form-item label="标签">
            <el-input v-model="uploadForm.tags" placeholder="标准,迎峰度夏" />
          </el-form-item>
          <el-form-item label="素材文件">
            <div class="file-picker">
              <input ref="uploadFileInputRef" type="file" :accept="assetAccept" @change="handleUploadFileChange" />
              <span>{{ uploadFile?.name || "请选择 pdf/xlsx/xls/docx/doc/csv 文件" }}</span>
            </div>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="uploadVisible = false">取消</el-button>
          <el-button type="primary" :loading="uploading" @click="submitUpload">上传</el-button>
        </template>
      </el-dialog>

      <el-dialog v-model="editVisible" title="编辑素材信息" width="640px">
        <el-form ref="editFormRef" :model="editForm" :rules="assetRules" label-position="top">
          <el-form-item label="素材名称" prop="name">
            <el-input v-model="editForm.name" />
          </el-form-item>
          <div class="form-grid">
            <el-form-item label="素材分类" prop="category">
              <el-select v-model="editForm.category">
                <el-option v-for="item in categories" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="启用状态">
              <el-switch v-model="editForm.enabled" active-text="启用" inactive-text="停用" />
            </el-form-item>
          </div>
          <el-form-item label="描述">
            <el-input v-model="editForm.description" type="textarea" :autosize="{ minRows: 3 }" />
          </el-form-item>
          <el-form-item label="标签">
            <el-input v-model="editForm.tags" placeholder="标准,检查" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="editVisible = false">取消</el-button>
          <el-button type="primary" :loading="editing" @click="submitEdit">保存</el-button>
        </template>
      </el-dialog>

      <el-dialog v-model="detailVisible" title="素材详情" width="960px">
        <div v-loading="detailLoading" class="asset-detail">
          <template v-if="detailAsset">
            <div class="excel-title-row">
              <div>
                <strong>{{ detailAsset.name }}</strong>
                <span>{{ detailAsset.originalFileName }} / {{ formatBytes(detailAsset.fileSize || 0) }}</span>
              </div>
              <el-tag effect="plain">{{ categoryText(detailAsset) }}</el-tag>
            </div>

            <template v-if="isExcelAsset(detailAsset) && excelPreview.sheets.length">
              <section v-for="sheet in excelPreview.sheets" :key="sheet.name" class="sheet-preview">
                <div class="sheet-title">
                  <span class="terminal-label">{{ sheet.name }}</span>
                  <strong>{{ sheet.rows.length }} 行</strong>
                </div>
                <div class="sheet-scroll">
                  <table>
                    <tbody>
                      <tr v-for="(row, rowIndex) in sheet.rows" :key="rowIndex">
                        <td v-for="cellIndex in maxColumns(sheet.rows)" :key="cellIndex">
                          {{ row[cellIndex - 1] || "" }}
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </section>
            </template>
            <el-empty v-else-if="isExcelAsset(detailAsset)" :description="detailError || '未读取到 Excel 表格内容'" />
            <el-alert
              v-else
              type="info"
              :closable="false"
              title="当前文件类型没有后端预览接口，可通过下载查看原始文件。"
            />
          </template>
        </div>
        <template #footer>
          <el-button @click="detailVisible = false">关闭</el-button>
          <el-button v-if="detailAsset" :icon="DownloadIcon" type="primary" :loading="downloadingId === detailAsset.id" @click="download(detailAsset)">下载文件</el-button>
        </template>
      </el-dialog>
    </div>
  </AuthGuard>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from "element-plus";
import {
  Delete as DeleteIcon,
  DocumentAdd,
  Download as DownloadIcon,
  Edit,
  Refresh,
  Search,
  Upload as UploadIcon,
  View as ViewIcon
} from "@element-plus/icons-vue";
import AuthGuard from "@platform/ui/src/components/AuthGuard.vue";
import PageHeader from "@/components/PageHeader.vue";
import {
  deleteAsset,
  downloadAsset,
  getAsset,
  importSeedAssets,
  listAssetCategories,
  listAssets,
  updateAsset,
  uploadAsset
} from "@/api/admin";
import type { AssetCategory, AssetCategoryOption, AssetRecord, PageResult } from "@/types/domain";
import { assetCategoryLabels, formatBytes } from "@/utils/labels";
import { assertValidXlsxBlob, readXlsxPreview, type ExcelPreview } from "@/utils/xlsx";

const assetAccept = ".pdf,.xlsx,.xls,.docx,.doc,.csv";
const allowedAssetPattern = /\.(pdf|xlsx|xls|docx|doc|csv)$/i;

const loading = ref(false);
const uploading = ref(false);
const editing = ref(false);
const importing = ref(false);
const detailLoading = ref(false);
const uploadVisible = ref(false);
const editVisible = ref(false);
const detailVisible = ref(false);
const uploadFormRef = ref<FormInstance>();
const editFormRef = ref<FormInstance>();
const uploadFileInputRef = ref<HTMLInputElement>();
const uploadFile = ref<File>();
const categories = ref<AssetCategoryOption[]>([]);
const detailAsset = ref<AssetRecord>();
const excelPreview = ref<ExcelPreview>({ sheets: [] });
const detailError = ref("");
const downloadingId = ref<AssetRecord["id"]>();

const assetPage = ref<PageResult<AssetRecord>>({
  items: [],
  total: 0,
  page: 1,
  pageSize: 10
});

const filters = reactive<{
  page: number;
  size: number;
  keyword: string;
  category: AssetCategory | null;
  enabled: boolean | null;
}>({
  page: 1,
  size: 10,
  keyword: "",
  category: null,
  enabled: null
});

const uploadForm = reactive({
  name: "",
  category: "STANDARD_DOC" as AssetCategory,
  description: "",
  tags: "",
  enabled: true
});

const editForm = reactive({
  id: "" as AssetRecord["id"],
  name: "",
  category: "STANDARD_DOC" as AssetCategory,
  description: "",
  tags: "",
  enabled: true
});

const assetRules: FormRules = {
  name: [{ required: true, message: "请输入素材名称", trigger: "blur" }],
  category: [{ required: true, message: "请选择素材分类", trigger: "change" }]
};

onMounted(async () => {
  await Promise.all([loadCategories(), loadAssets()]);
});

async function loadCategories() {
  categories.value = await listAssetCategories();
}

async function loadAssets() {
  loading.value = true;
  try {
    assetPage.value = await listAssets({ ...filters });
  } finally {
    loading.value = false;
  }
}

function applyFilters() {
  filters.page = 1;
  loadAssets();
}

function resetFilters() {
  filters.keyword = "";
  filters.category = null;
  filters.enabled = null;
  filters.page = 1;
  loadAssets();
}

function changePage(page: number) {
  filters.page = page;
  loadAssets();
}

function openUpload() {
  uploadForm.name = "";
  uploadForm.category = "STANDARD_DOC";
  uploadForm.description = "";
  uploadForm.tags = "";
  uploadForm.enabled = true;
  uploadFile.value = undefined;
  if (uploadFileInputRef.value) uploadFileInputRef.value.value = "";
  uploadVisible.value = true;
}

function handleUploadFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  uploadFile.value = input.files?.[0];
}

async function submitUpload() {
  await uploadFormRef.value?.validate();
  if (!uploadFile.value) {
    ElMessage.warning("请选择素材文件");
    return;
  }
  if (!allowedAssetPattern.test(uploadFile.value.name)) {
    ElMessage.warning("素材文件仅支持 pdf/xlsx/xls/docx/doc/csv");
    return;
  }
  uploading.value = true;
  try {
    await uploadAsset({ ...uploadForm, file: uploadFile.value });
    uploadVisible.value = false;
    await loadAssets();
    ElMessage.success("素材已上传");
  } finally {
    uploading.value = false;
  }
}

async function openEdit(asset: AssetRecord) {
  const detail = await getAsset(asset.id);
  editForm.id = detail.id;
  editForm.name = detail.name;
  editForm.category = detail.category;
  editForm.description = detail.description || "";
  editForm.tags = detail.tags || "";
  editForm.enabled = detail.enabled;
  editVisible.value = true;
}

async function submitEdit() {
  await editFormRef.value?.validate();
  editing.value = true;
  try {
    await updateAsset({ ...editForm });
    editVisible.value = false;
    await loadAssets();
    ElMessage.success("素材信息已保存");
  } finally {
    editing.value = false;
  }
}

async function saveEnabled(asset: AssetRecord) {
  await updateAsset({
    id: asset.id,
    name: asset.name,
    category: asset.category,
    description: asset.description,
    tags: asset.tags,
    enabled: asset.enabled
  });
  ElMessage.success("素材状态已保存");
}

async function openDetail(asset: AssetRecord) {
  detailVisible.value = true;
  detailLoading.value = true;
  detailAsset.value = undefined;
  excelPreview.value = { sheets: [] };
  detailError.value = "";
  try {
    const detail = await getAsset(asset.id);
    detailAsset.value = detail;
    if (isExcelAsset(detail)) {
      const { blob } = await downloadAsset(detail);
      await assertValidXlsxBlob(blob);
      excelPreview.value = await readXlsxPreview(blob);
    }
  } catch (error) {
    detailError.value = error instanceof Error ? error.message : "Excel 内容读取失败";
    ElMessage.error(detailError.value);
  } finally {
    detailLoading.value = false;
  }
}

async function download(asset: AssetRecord) {
  downloadingId.value = asset.id;
  try {
    const { blob, fileName } = await downloadAsset(asset);
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = normalizeAssetFileName(fileName || asset.originalFileName || asset.name);
    link.style.display = "none";
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    ElMessage.success("文件下载已开始");
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "文件下载失败");
  } finally {
    downloadingId.value = undefined;
  }
}

async function remove(asset: AssetRecord) {
  try {
    await ElMessageBox.confirm(`删除后「${asset.name}」将从素材库移除。`, "确认删除素材", {
      confirmButtonText: "删除",
      cancelButtonText: "取消",
      type: "warning"
    });
    await deleteAsset(asset.id);
    await loadAssets();
    ElMessage.success("素材已删除");
  } catch {
    // User cancelled.
  }
}

async function importSeeds() {
  importing.value = true;
  try {
    const result = await importSeedAssets();
    await loadAssets();
    ElMessage.success(`已扫描 ${result.scanned} 个，导入 ${result.imported} 个，跳过 ${result.skipped} 个`);
  } finally {
    importing.value = false;
  }
}

function categoryText(asset: AssetRecord) {
  return asset.categoryLabel || assetCategoryLabels[asset.category] || asset.category;
}

function splitTags(tags?: string) {
  return (tags || "")
    .split(",")
    .map((tag) => tag.trim())
    .filter(Boolean);
}

function maxColumns(rows: string[][]) {
  return Math.max(1, ...rows.map((row) => row.length));
}

function isExcelAsset(asset?: Pick<AssetRecord, "fileType" | "originalFileName">) {
  return Boolean(asset && (/^(xlsx)$/i.test(asset.fileType || "") || /\.xlsx$/i.test(asset.originalFileName || "")));
}

function normalizeAssetFileName(name: string) {
  const safeName = name.replace(/[\\/:*?"<>|]/g, "_").trim() || "asset";
  return safeName;
}
</script>

<style scoped>
.asset-filter-bar {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) minmax(170px, 0.45fr) minmax(140px, 0.35fr) 96px 96px;
  gap: 12px;
  align-items: center;
  padding: 14px 16px;
  margin-bottom: 12px;
}

.asset-filter-bar :deep(.el-button) {
  width: 100%;
}

.table-surface {
  padding: 0 0 12px;
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
  color: var(--text-primary);
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
  padding: 0 11px;
  font-size: 14px;
}

.file-type {
  display: inline-flex;
  min-width: 54px;
  color: var(--accent-blue);
  font-weight: 900;
  text-transform: uppercase;
}

.description-text {
  display: block;
  color: var(--text-secondary);
  line-height: 1.5;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.asset-actions {
  flex-wrap: nowrap;
  justify-content: center;
  gap: 8px;
}

.asset-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  padding: 12px 16px 0;
}

.form-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 180px;
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

.asset-detail {
  min-height: 320px;
}

.excel-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.excel-title-row strong,
.excel-title-row span {
  display: block;
}

.excel-title-row span {
  margin-top: 5px;
  color: var(--text-muted);
}

.sheet-preview {
  display: grid;
  gap: 10px;
  margin-bottom: 18px;
}

.sheet-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sheet-scroll {
  max-height: 420px;
  overflow: auto;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: #fff;
}

.sheet-scroll table {
  min-width: 100%;
  border-collapse: collapse;
}

.sheet-scroll td {
  min-width: 120px;
  max-width: 320px;
  padding: 8px 10px;
  border: 1px solid var(--border-default);
  color: var(--text-primary);
  line-height: 1.55;
  vertical-align: top;
}

@media (max-width: 1180px) {
  .asset-filter-bar {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
