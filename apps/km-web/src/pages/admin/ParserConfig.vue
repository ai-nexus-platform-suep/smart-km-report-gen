<template>
  <div class="config-page">
    <h2>解析器配置</h2>
    <el-card shadow="never" class="config-card">
      <el-form ref="formRef" :model="form" label-width="180px" v-loading="loading">
        <el-form-item label="并发解析任务数">
          <el-input-number v-model="form.concurrency" :min="1" :max="20" :step="1" style="width:320px" />
          <div class="form-tip">同时处理的文档解析任务数量，根据服务器性能调整</div>
        </el-form-item>
        <el-form-item label="最大文件大小 (MB)">
          <el-input-number v-model="form.maxFileSize" :min="1" :max="200" :step="5" style="width:320px" />
          <div class="form-tip">超过此大小的文件将被拒绝上传</div>
        </el-form-item>
        <el-form-item label="支持的文件类型">
          <el-checkbox-group v-model="form.supportedTypes">
            <el-checkbox label="PDF" value="pdf" />
            <el-checkbox label="Word" value="docx" />
            <el-checkbox label="Markdown" value="md" />
            <el-checkbox label="纯文本" value="txt" />
            <el-checkbox label="HTML" value="html" />
            <el-checkbox label="CSV" value="csv" />
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="解析超时 (秒)">
          <el-input-number v-model="form.timeoutSeconds" :min="10" :max="600" :step="10" style="width:320px" />
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
import { getParserConfig, saveParserConfig } from '../../api/admin'

const formRef = ref()
const loading = ref(true)
const saving = ref(false)
const form = reactive({ concurrency: 3, maxFileSize: 50, supportedTypes: ['pdf', 'docx', 'md', 'txt'], timeoutSeconds: 120 })

async function fetchConfig() {
  loading.value = true
  try {
    const res = await getParserConfig()
    const d = res.data?.data
    if (d) { form.concurrency = d.concurrency ?? 3; form.maxFileSize = d.maxFileSize ?? 50; form.supportedTypes = d.supportedTypes ?? ['pdf', 'docx', 'md', 'txt']; form.timeoutSeconds = d.timeoutSeconds ?? 120 }
  } catch { ElMessage.error('获取配置失败') }
  finally { loading.value = false }
}
async function handleSave() {
  saving.value = true
  try { await saveParserConfig({ ...form }); ElMessage.success('配置已保存') }
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
.form-tip { font-size: 12px; color: var(--text-secondary); margin-top: 4px; }
</style>
