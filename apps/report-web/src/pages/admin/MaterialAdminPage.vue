<template>
  <AuthGuard require-admin>
    <div class="page">
      <PageHeader
        eyebrow="MATERIAL CENTER"
        title="素材管理"
        description="维护报告生成所需的专业素材，跟踪解析状态与 RAGFlow 数据集标识。"
      >
        <el-button type="primary" @click="add">新增素材</el-button>
      </PageHeader>

      <section class="surface table-surface">
        <el-table v-loading="loading" :data="materials" row-key="id" class="admin-table" empty-text="暂无素材">
          <el-table-column label="素材名称" min-width="240">
            <template #default="{ row }">
              <strong>{{ row.name }}</strong>
              <small>{{ row.ragflowDatasetId }}</small>
            </template>
          </el-table-column>
          <el-table-column label="报告类型" min-width="170">
            <template #default="{ row }">{{ reportTypeLabels[row.reportType] }}</template>
          </el-table-column>
          <el-table-column prop="specialty" label="专业" width="120" />
          <el-table-column label="解析状态" width="130">
            <template #default="{ row }">
              <el-tag size="small" :type="parseStatusTag(row.parseStatus)">{{ parseStatusLabel(row.parseStatus) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="uploadedBy" label="上传人" width="130" />
          <el-table-column label="上传时间" min-width="180">
            <template #default="{ row }">{{ new Date(row.createdAt).toLocaleString() }}</template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text type="danger" @click="remove(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </div>
  </AuthGuard>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import AuthGuard from "@platform/ui/src/components/AuthGuard.vue";
import PageHeader from "@/components/PageHeader.vue";
import { addMaterial, deleteMaterial, listMaterials } from "@/api/admin";
import type { MaterialRecord } from "@/types/domain";
import { reportTypeLabels } from "@/utils/labels";

const materials = ref<MaterialRecord[]>([]);
const loading = ref(false);

onMounted(load);

async function load() {
  loading.value = true;
  try {
    materials.value = await listMaterials();
  } finally {
    loading.value = false;
  }
}

async function add() {
  await addMaterial({
    name: `专业素材_${materials.value.length + 1}.docx`,
    reportType: "COAL_INVENTORY_AUDIT",
    specialty: "燃料"
  });
  await load();
  ElMessage.success("素材已新增，等待解析");
}

async function remove(material: MaterialRecord) {
  try {
    await ElMessageBox.confirm(`删除后「${material.name}」将不再参与报告生成检索。`, "确认删除素材", {
      confirmButtonText: "删除",
      cancelButtonText: "取消",
      type: "warning"
    });
    await deleteMaterial(material.id);
    await load();
    ElMessage.success("素材已删除");
  } catch {
    // User cancelled.
  }
}

function parseStatusLabel(status: MaterialRecord["parseStatus"]) {
  const labels: Record<MaterialRecord["parseStatus"], string> = {
    PENDING: "等待解析",
    PARSING: "解析中",
    READY: "可用",
    FAILED: "失败"
  };
  return labels[status];
}

function parseStatusTag(status: MaterialRecord["parseStatus"]) {
  if (status === "READY") return "success";
  if (status === "FAILED") return "danger";
  if (status === "PARSING") return "warning";
  return "info";
}
</script>

<style scoped>
.table-surface {
  padding: 0;
}

.admin-table :deep(strong),
.admin-table :deep(small) {
  display: block;
}

.admin-table :deep(small) {
  margin-top: 4px;
  color: var(--text-muted);
}
</style>
