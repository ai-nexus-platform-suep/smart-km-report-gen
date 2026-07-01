<template>
  <div class="config-page">
    <h2>重排序模型配置</h2>
    <el-card shadow="never" class="config-card">
      <el-form ref="formRef" :model="form" label-width="140px" v-loading="loading">
        <el-form-item label="提供者">
          <el-select v-model="form.provider" style="width:320px">
            <el-option label="OpenAI" value="openai" /><el-option label="Cohere" value="cohere" />
            <el-option label="阿里巴巴 DashScope" value="dashscope" /><el-option label="本地模型" value="local" />
          </el-select>
        </el-form-item>
        <el-form-item label="模型名称">
          <el-input v-model="form.modelName" placeholder="例如 rerank-multilingual-v3" style="width:320px" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="form.apiKey" :type="showApiKey ? 'text' : 'password'" placeholder="输入 API Key" style="width:320px">
            <template #suffix>
              <el-icon style="cursor:pointer" @click="showApiKey = !showApiKey">
                <View v-if="!showApiKey" /><Hide v-else />
              </el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="API 地址">
          <el-input v-model="form.apiBase" placeholder="留空使用默认地址" style="width:320px" />
        </el-form-item>
        <el-form-item label="Top-K">
          <el-input-number v-model="form.topK" :min="1" :max="100" :step="1" style="width:320px" />
        </el-form-item>
        <el-form-item label="最低分数">
          <el-input-number v-model="form.minScore" :min="0" :max="1" :step="0.05" :precision="2" style="width:320px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSave" :loading="saving">保存配置</el-button>
          <el-button @click="fetchConfig">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { View, Hide } from '@element-plus/icons-vue'
import { getRerankConfig, saveRerankConfig } from '../../api/admin'

const formRef = ref()
const loading = ref(true)
const saving = ref(false)
const showApiKey = ref(false)
const form = reactive({ provider: 'openai', modelName: '', apiKey: '', apiBase: '', topK: 10, minScore: 0.3 })

async function fetchConfig() {
  loading.value = true
  try {
    const res = await getRerankConfig()
    const d = res.data?.data
    if (d) { form.provider = d.provider || 'openai'; form.modelName = d.modelName || ''; form.apiKey = d.apiKey || ''; form.apiBase = d.apiBase || ''; form.topK = d.topK ?? 10; form.minScore = d.minScore ?? 0.3 }
  } catch { ElMessage.error('获取配置失败') }
  finally { loading.value = false }
}
async function handleSave() {
  saving.value = true
  try { await saveRerankConfig({ ...form }); ElMessage.success('配置已保存') }
  catch { ElMessage.error('保存失败') }
  finally { saving.value = false }
}
onMounted(() => fetchConfig())
</script>

<style scoped>
.config-page { padding: 0; max-width: 1200px; }
.config-page h2 { font-size: 20px; font-weight: 600; margin: 0 0 20px; }
.config-card { border: 1px solid var(--border-color); border-radius: var(--border-radius); background: var(--bg-container); }
.config-card :deep(.el-card__body) { padding: 24px 32px; }
</style>
