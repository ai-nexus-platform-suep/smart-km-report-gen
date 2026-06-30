<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Plus, Refresh, Star } from '@element-plus/icons-vue'
import {
  createModelConfig,
  deleteModelConfig,
  listModelConfigs,
  setDefaultModelConfig,
} from '../../api'
import type { ModelConfigPayload, ModelConfigVO, ModelProvider } from '@platform/core'

const loading = ref(false)
const saving = ref(false)
const configs = ref<ModelConfigVO[]>([])
const form = reactive<ModelConfigPayload>({
  provider: 'deepseek',
  baseUrl: 'https://api.deepseek.com',
  modelName: 'deepseek-chat',
  apiKey: '',
  scenario: 'chat',
})

const providerPresets: Array<{
  label: string
  value: ModelProvider
  baseUrl: string
  models: string[]
}> = [
  {
    label: 'DeepSeek',
    value: 'deepseek',
    baseUrl: 'https://api.deepseek.com',
    models: ['deepseek-chat', 'deepseek-reasoner'],
  },
  {
    label: 'OpenAI',
    value: 'openai',
    baseUrl: 'https://api.openai.com/v1',
    models: ['gpt-4o-mini', 'gpt-4o', 'gpt-4.1-mini'],
  },
  {
    label: '通义千问',
    value: 'qwen',
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    models: ['qwen-plus', 'qwen-max', 'qwen-turbo'],
  },
  {
    label: 'SiliconFlow',
    value: 'siliconflow',
    baseUrl: 'https://api.siliconflow.cn/v1',
    models: ['Qwen/Qwen2.5-72B-Instruct', 'deepseek-ai/DeepSeek-V3'],
  },
  {
    label: '智谱 GLM',
    value: 'zhipu',
    baseUrl: 'https://open.bigmodel.cn/api/paas/v4',
    models: ['glm-4-flash', 'glm-4-plus'],
  },
  {
    label: '自定义',
    value: 'custom',
    baseUrl: '',
    models: [],
  },
]

const activePreset = computed(() => providerPresets.find((item) => item.value === form.provider))
const modelOptions = computed(() => activePreset.value?.models ?? [])

async function loadConfigs() {
  loading.value = true
  try {
    configs.value = await listModelConfigs()
  } catch (error) {
    console.error(error)
    ElMessage.error('模型配置加载失败。')
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.provider = 'deepseek'
  applyProviderPreset()
  form.apiKey = ''
  form.scenario = 'chat'
}

function applyProviderPreset() {
  const preset = activePreset.value
  if (!preset || preset.value === 'custom') return
  form.baseUrl = preset.baseUrl
  form.modelName = preset.models[0] ?? ''
}

async function handleCreate() {
  if (!form.baseUrl.trim()) {
    ElMessage.warning('请输入 Base URL。')
    return
  }
  if (!form.modelName.trim()) {
    ElMessage.warning('请输入模型名称。')
    return
  }
  if (!form.apiKey.trim()) {
    ElMessage.warning('请输入 API Key。')
    return
  }

  saving.value = true
  try {
    await createModelConfig({ ...form })
    ElMessage.success('模型配置已保存')
    resetForm()
    await loadConfigs()
  } catch (error) {
    console.error(error)
    ElMessage.error('保存失败。')
  } finally {
    saving.value = false
  }
}

async function handleSetDefault(row: ModelConfigVO) {
  try {
    await setDefaultModelConfig(row.id)
    ElMessage.success('已设为默认配置')
    await loadConfigs()
  } catch (error) {
    console.error(error)
    ElMessage.error('设置失败。')
  }
}

async function handleDelete(row: ModelConfigVO) {
  try {
    await ElMessageBox.confirm(`确定删除模型配置「${row.modelName}」吗？`, '删除模型配置', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await deleteModelConfig(row.id)
    ElMessage.success('模型配置已删除')
    await loadConfigs()
  } catch (error) {
    if (error !== 'cancel') {
      console.error(error)
      ElMessage.error('删除失败。')
    }
  }
}

watch(
  () => form.provider,
  () => applyProviderPreset(),
)

onMounted(loadConfigs)
</script>

<template>
  <div class="llm-page">
    <header class="page-header">
      <div>
        <p>LLM 配置</p>
        <h1>模型服务配置</h1>
        <span>对接后端 <code>/api/model-configs</code>，用于问答模型切换和默认配置管理。</span>
      </div>
    </header>

    <section class="config-layout">
      <main class="table-panel" v-loading="loading">
        <div class="panel-title">
          <strong>已有配置</strong>
          <span>{{ configs.length }} 项</span>
        </div>
        <el-table :data="configs" row-key="id">
          <el-table-column prop="provider" label="供应商" width="120" />
          <el-table-column prop="modelName" label="模型" min-width="160" />
          <el-table-column prop="baseUrl" label="Base URL" min-width="240" show-overflow-tooltip />
          <el-table-column prop="apiKeyMasked" label="密钥" width="140" />
          <el-table-column label="状态" width="130">
            <template #default="{ row }">
              <el-tag :type="row.isDefault ? 'success' : 'info'">
                {{ row.isDefault ? '默认' : '备用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="170" fixed="right">
            <template #default="{ row }">
              <el-button text :icon="Star" :disabled="row.isDefault === 1" @click="handleSetDefault(row)">
                默认
              </el-button>
              <el-button text type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </main>

      <aside class="form-panel">
        <div class="panel-title">
          <strong>新增配置</strong>
          <span>chat</span>
        </div>
        <el-form label-position="top">
          <el-form-item label="供应商">
            <el-select v-model="form.provider" style="width: 100%">
              <el-option v-for="item in providerPresets" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="Base URL">
            <el-input v-model="form.baseUrl" placeholder="https://api.deepseek.com">
              <template #append>
                <el-button :icon="Refresh" @click="applyProviderPreset" />
              </template>
            </el-input>
          </el-form-item>
          <el-form-item label="模型名称">
            <el-select
              v-if="modelOptions.length"
              v-model="form.modelName"
              filterable
              allow-create
              default-first-option
              style="width: 100%"
              placeholder="选择或输入模型名称"
            >
              <el-option v-for="model in modelOptions" :key="model" :label="model" :value="model" />
            </el-select>
            <el-input v-else v-model="form.modelName" placeholder="输入自定义模型名称" />
          </el-form-item>
          <el-form-item label="API Key">
            <el-input v-model="form.apiKey" type="password" show-password placeholder="输入明文密钥，后端保存时加密" />
          </el-form-item>
          <el-button type="primary" :icon="Plus" :loading="saving" @click="handleCreate">保存配置</el-button>
        </el-form>
      </aside>
    </section>
  </div>
</template>

<style scoped>
.llm-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header p {
  margin: 0;
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}

.page-header h1 {
  margin: 4px 0;
  color: var(--text-primary);
  font-size: 26px;
}

.page-header span {
  color: var(--text-secondary);
}

.config-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 16px;
}

.table-panel,
.form-panel {
  border: 1px solid var(--border-color);
  border-radius: var(--border-radius);
  background: var(--bg-container);
  box-shadow: var(--shadow-xs);
}

.table-panel {
  overflow: hidden;
}

.form-panel {
  align-self: start;
  padding: 18px;
}

.panel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 18px;
  border-bottom: 1px solid var(--border-color-light);
}

.form-panel .panel-title {
  margin: -18px -18px 18px;
}

.panel-title strong {
  color: var(--text-primary);
}

.panel-title span {
  color: var(--text-tertiary);
  font-size: 13px;
}

@media (max-width: 1100px) {
  .config-layout {
    grid-template-columns: 1fr;
  }
}
</style>
