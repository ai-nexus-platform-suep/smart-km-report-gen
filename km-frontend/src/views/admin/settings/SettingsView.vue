<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getEmbeddingConfig,
  getParserConfig,
  getRerankConfig,
  updateEmbeddingConfig,
  updateParserConfig,
  updateRerankConfig,
} from '@/api/config'

const router = useRouter()
const activeTab = ref('embedding')
const saving = ref(false)

const embedding = reactive({
  modelName: '',
  apiUrl: '',
  apiKey: '',
  dimension: 1024,
})

const rerank = reactive({
  modelName: '',
  apiUrl: '',
  apiKey: '',
  topN: 20,
})

const parser = reactive({
  backend: 'tika',
  maxConcurrency: 3,
})

async function loadAll() {
  const [emb, rer, par] = await Promise.all([
    getEmbeddingConfig(),
    getRerankConfig(),
    getParserConfig(),
  ])
  Object.assign(embedding, emb)
  Object.assign(rerank, rer)
  Object.assign(parser, par)
}

async function saveEmbedding() {
  saving.value = true
  try {
    const updated = await updateEmbeddingConfig({ ...embedding })
    Object.assign(embedding, updated)
    ElMessage.success('嵌入模型配置已保存')
  } finally {
    saving.value = false
  }
}

async function saveRerank() {
  saving.value = true
  try {
    const updated = await updateRerankConfig({ ...rerank })
    Object.assign(rerank, updated)
    ElMessage.success('重排序模型配置已保存')
  } finally {
    saving.value = false
  }
}

async function saveParser() {
  saving.value = true
  try {
    const updated = await updateParserConfig({ ...parser })
    Object.assign(parser, updated)
    ElMessage.success('解析器配置已保存')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadAll().catch(() => ElMessage.error('加载配置失败，请确认后端已启动'))
})
</script>

<template>
  <div class="settings-page">
    <header class="header">
      <h1>系统设置</h1>
      <el-button link @click="router.push('/admin')">返回概览</el-button>
    </header>

    <el-card>
      <el-tabs v-model="activeTab">
        <el-tab-pane label="嵌入模型" name="embedding">
          <el-form label-width="120px" style="max-width: 560px">
            <el-form-item label="模型名称">
              <el-input v-model="embedding.modelName" placeholder="BAAI/bge-m3" />
            </el-form-item>
            <el-form-item label="API 地址">
              <el-input v-model="embedding.apiUrl" />
            </el-form-item>
            <el-form-item label="API Key">
              <el-input v-model="embedding.apiKey" type="password" show-password placeholder="留空或脱敏则不修改" />
            </el-form-item>
            <el-form-item label="向量维度">
              <el-input-number v-model="embedding.dimension" :min="128" :max="4096" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saving" @click="saveEmbedding">保存</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="重排序模型" name="rerank">
          <el-form label-width="120px" style="max-width: 560px">
            <el-form-item label="模型名称">
              <el-input v-model="rerank.modelName" placeholder="BAAI/bge-reranker-v2-m3" />
            </el-form-item>
            <el-form-item label="API 地址">
              <el-input v-model="rerank.apiUrl" />
            </el-form-item>
            <el-form-item label="API Key">
              <el-input v-model="rerank.apiKey" type="password" show-password />
            </el-form-item>
            <el-form-item label="Top N">
              <el-input-number v-model="rerank.topN" :min="1" :max="100" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saving" @click="saveRerank">保存</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="解析器" name="parser">
          <el-form label-width="120px" style="max-width: 560px">
            <el-form-item label="解析后端">
              <el-select v-model="parser.backend" style="width: 100%">
                <el-option label="Apache Tika" value="tika" />
              </el-select>
            </el-form-item>
            <el-form-item label="最大并发">
              <el-input-number v-model="parser.maxConcurrency" :min="1" :max="10" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saving" @click="saveParser">保存</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <p class="hint">EPIC-04 Python 管线与 EPIC-05 检索联调时将读取此处配置</p>
  </div>
</template>

<style scoped>
.settings-page {
  max-width: 800px;
  margin: 32px auto;
  padding: 0 16px;
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}
.hint {
  margin-top: 16px;
  color: #909399;
  font-size: 13px;
}
</style>
