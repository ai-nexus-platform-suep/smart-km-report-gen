<template>
  <AuthGuard require-admin>
    <div class="page">
      <PageHeader
        eyebrow="MODEL CONFIG"
        title="模型配置"
        description="维护报告生成模型的 API 地址、模型名称、超时时间和启用状态。"
      >
        <el-button type="primary" @click="openCreate">新增模型</el-button>
      </PageHeader>

      <section v-loading="loading" class="config-list">
        <div v-for="config in configs" :key="config.id" class="surface llm-card interactive-lift">
          <div class="surface-title">
            <div>
              <span class="eyebrow">{{ config.provider }}</span>
              <h2>{{ config.modelName }}</h2>
            </div>
            <el-switch v-model="config.enabled" @change="() => save(config)" />
          </div>

          <el-form label-position="top" class="llm-form">
            <el-form-item label="API 地址">
              <el-input v-model="config.baseUrl" @blur="save(config)" />
            </el-form-item>
            <el-form-item label="模型名称">
              <el-input v-model="config.modelName" @blur="save(config)" />
            </el-form-item>
            <el-form-item label="超时时间（秒）">
              <el-input-number v-model="config.timeoutSeconds" :min="10" :max="600" @change="() => save(config)" />
            </el-form-item>
            <el-form-item label="API Key">
              <el-input type="password" placeholder="已配置时不回显，修改时重新输入" show-password />
            </el-form-item>
            <el-alert
              v-if="testResult[String(config.id)]"
              :type="testResult[String(config.id)]?.success ? 'success' : 'error'"
              :closable="false"
              show-icon
            >
              <template #title>{{ testResult[String(config.id)]?.message }}</template>
            </el-alert>
            <el-button :loading="testingId === config.id" @click="test(config.id)">连通性测试</el-button>
          </el-form>
        </div>
      </section>

      <el-dialog v-model="createVisible" title="新增模型配置" width="640px">
        <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-position="top">
          <div class="create-grid">
            <el-form-item label="模型供应商" prop="provider">
              <el-select v-model="createForm.provider">
                <el-option v-for="item in providerOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="是否启用" prop="enabled">
              <el-switch v-model="createForm.enabled" />
            </el-form-item>
          </div>
          <el-form-item label="API 地址" prop="baseUrl">
            <el-input v-model="createForm.baseUrl" placeholder="例如：http://127.0.0.1:11434" />
          </el-form-item>
          <el-form-item label="模型名称" prop="modelName">
            <el-input v-model="createForm.modelName" placeholder="例如：qwen2.5:14b" />
          </el-form-item>
          <el-form-item label="API Key">
            <el-input v-model="createForm.apiKey" type="password" show-password placeholder="保存后只显示已配置，不回显明文" />
          </el-form-item>
          <el-form-item label="超时时间（秒）" prop="timeoutSeconds">
            <el-input-number v-model="createForm.timeoutSeconds" :min="10" :max="600" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="createVisible = false">取消</el-button>
          <el-button type="primary" @click="createModel">保存模型</el-button>
        </template>
      </el-dialog>
    </div>
  </AuthGuard>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage, type FormInstance, type FormRules } from "element-plus";
import AuthGuard from "@platform/ui/src/components/AuthGuard.vue";
import PageHeader from "@/components/PageHeader.vue";
import { listLlmConfigs, saveLlmConfig, testLlmConfig } from "@/api/admin";
import type { EntityId, LlmConfig } from "@/types/domain";

const configs = ref<LlmConfig[]>([]);
const loading = ref(false);
const testingId = ref<EntityId>();
const testResult = reactive<Record<string, { success: boolean; message: string }>>({});
const createVisible = ref(false);
const createFormRef = ref<FormInstance>();

const providerOptions: Array<{ label: string; value: LlmConfig["provider"] }> = [
  { label: "OPENAI_COMPATIBLE", value: "OPENAI_COMPATIBLE" },
  { label: "OLLAMA", value: "OLLAMA" },
  { label: "RAGFLOW", value: "RAGFLOW" }
];

const createForm = reactive({
  provider: "OPENAI_COMPATIBLE" as LlmConfig["provider"],
  baseUrl: "",
  apiKey: "",
  modelName: "",
  timeoutSeconds: 120,
  enabled: true
});

const createRules: FormRules = {
  provider: [{ required: true, message: "请选择模型供应商", trigger: "change" }],
  baseUrl: [
    { required: true, message: "请输入 API 地址", trigger: "blur" },
    {
      validator: (_rule, value: string, callback) => {
        if (/^https?:\/\/.+/i.test(value)) callback();
        else callback(new Error("API 地址必须以 http:// 或 https:// 开头"));
      },
      trigger: "blur"
    }
  ],
  modelName: [{ required: true, message: "请输入模型名称", trigger: "blur" }],
  timeoutSeconds: [{ required: true, type: "number", message: "请输入超时时间", trigger: "blur" }]
};

onMounted(load);

async function load() {
  loading.value = true;
  try {
    configs.value = await listLlmConfigs();
  } finally {
    loading.value = false;
  }
}

async function save(config: LlmConfig) {
  await saveLlmConfig(config);
}

function resetCreateForm() {
  createForm.provider = "OPENAI_COMPATIBLE";
  createForm.baseUrl = "";
  createForm.apiKey = "";
  createForm.modelName = "";
  createForm.timeoutSeconds = 120;
  createForm.enabled = true;
}

function openCreate() {
  resetCreateForm();
  createVisible.value = true;
}

async function createModel() {
  await createFormRef.value?.validate();
  await saveLlmConfig({
    id: 0,
    provider: createForm.provider,
    baseUrl: createForm.baseUrl,
    apiKeyConfigured: Boolean(createForm.apiKey.trim()),
    modelName: createForm.modelName,
    timeoutSeconds: createForm.timeoutSeconds,
    enabled: createForm.enabled
  });
  await load();
  createVisible.value = false;
  ElMessage.success("模型配置已新增");
}

async function test(id: EntityId) {
  testingId.value = id;
  try {
    const result = await testLlmConfig(id);
    testResult[String(id)] = result;
    if (result.success) ElMessage.success("模型连通性正常");
  } finally {
    testingId.value = undefined;
  }
}
</script>

<style scoped>
.config-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.llm-card {
  overflow: hidden;
}

.llm-form {
  display: grid;
  padding: 18px;
}

.create-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 120px;
  gap: 14px;
}
</style>
