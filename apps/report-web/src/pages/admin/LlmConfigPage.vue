<template>
  <AuthGuard require-admin>
    <div class="page">
      <PageHeader
        eyebrow="MODEL CONFIG"
        title="模型配置"
        description="维护报告生成服务唯一生效的 LLM 配置，模型名称可手动填写或替换。"
      >
        <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
      </PageHeader>

      <section v-loading="loading" class="surface llm-config-panel">
        <div class="surface-title">
          <div>
            <span class="eyebrow">SINGLE LLM PROFILE</span>
            <h2>{{ form.modelName || "未配置模型" }}</h2>
          </div>
          <el-tag v-if="form.apiKeyConfigured" type="success" effect="plain">API Key 已配置</el-tag>
          <el-tag v-else type="warning" effect="plain">API Key 未配置</el-tag>
        </div>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="llm-form">
          <el-form-item label="API 地址" prop="baseUrl">
            <el-input v-model="form.baseUrl" placeholder="例如：http://localhost:9000/v1" />
          </el-form-item>
          <el-form-item label="模型名称" prop="modelName">
            <el-input v-model="form.modelName" placeholder="例如：qwen2.5:14b 或 deepseek-chat" />
          </el-form-item>
          <el-form-item label="超时时间（秒）" prop="timeoutSeconds">
            <el-input-number v-model="form.timeoutSeconds" :min="10" :max="600" :controls="false" />
          </el-form-item>
          <el-form-item label="API Key">
            <el-input v-model="apiKey" type="password" show-password placeholder="不修改可留空，填写后会覆盖后端密钥" />
          </el-form-item>
        </el-form>
      </section>
    </div>
  </AuthGuard>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage, type FormInstance, type FormRules } from "element-plus";
import AuthGuard from "@platform/ui/src/components/AuthGuard.vue";
import PageHeader from "@/components/PageHeader.vue";
import { getLlmConfig, updateLlmConfig } from "@/api/admin";
import type { LlmConfig } from "@/types/domain";

const loading = ref(false);
const saving = ref(false);
const apiKey = ref("");
const formRef = ref<FormInstance>();

const form = reactive<LlmConfig>({
  id: "global-llm",
  provider: "OPENAI_COMPATIBLE",
  baseUrl: "",
  apiKeyConfigured: false,
  modelName: "",
  timeoutSeconds: 120,
  enabled: true
});

const rules: FormRules = {
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
    Object.assign(form, await getLlmConfig());
  } finally {
    loading.value = false;
  }
}

async function save() {
  await formRef.value?.validate();
  saving.value = true;
  try {
    const updated = await updateLlmConfig({
      baseUrl: form.baseUrl,
      modelName: form.modelName,
      timeoutSeconds: form.timeoutSeconds,
      apiKey: apiKey.value.trim() || undefined
    });
    Object.assign(form, updated);
    apiKey.value = "";
    ElMessage.success("模型配置已保存");
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped>
.llm-config-panel {
  overflow: hidden;
}

.llm-config-panel :deep(.surface-title h2) {
  font-size: 24px;
}

.llm-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-content: start;
  gap: 18px 22px;
  padding: 22px;
}

.llm-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.llm-form :deep(.el-form-item:nth-child(1)),
.llm-form :deep(.el-form-item:nth-child(4)) {
  grid-column: 1 / -1;
}

.llm-form :deep(.el-form-item__label) {
  margin-bottom: 8px;
  color: var(--text-secondary);
  font-size: 15px;
  font-weight: 700;
}

.llm-form :deep(.el-input__wrapper),
.llm-form :deep(.el-input-number),
.llm-form :deep(.el-input-number .el-input__wrapper) {
  width: 100%;
  min-height: 46px;
  font-size: 15px;
}

.llm-form :deep(.el-input__inner) {
  font-size: 15px;
}
</style>
