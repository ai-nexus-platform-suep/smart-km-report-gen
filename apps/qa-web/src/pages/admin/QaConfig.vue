<script setup lang="ts">
import { reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { Setting } from '@element-plus/icons-vue'

const form = reactive({
  topK: 5,
  similarityThreshold: 0.7,
  rerankThreshold: 0.5,
  selectedKnowledgeBases: ['设备检修', '技术监督'],
  enableThinking: true,
  enableCitation: true,
})

function handleSave() {
  ElMessage.success('配置已保存到前端 mock 状态，等待后端配置接口联调。')
}
</script>

<template>
  <div class="config-page">
    <header class="page-header">
      <div>
        <p>问答配置</p>
        <h1>知识问答参数</h1>
        <span>维护检索召回、重排序和回答展示参数。</span>
      </div>
    </header>

    <section class="form-panel">
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="Top K">
            <el-input-number v-model="form.topK" :min="1" :max="20" />
          </el-form-item>
          <el-form-item label="相似度阈值">
            <el-slider v-model="form.similarityThreshold" :min="0" :max="1" :step="0.05" show-input />
          </el-form-item>
          <el-form-item label="重排序阈值">
            <el-slider v-model="form.rerankThreshold" :min="0" :max="1" :step="0.05" show-input />
          </el-form-item>
          <el-form-item label="默认知识库">
            <el-select v-model="form.selectedKnowledgeBases" multiple style="width: 100%">
              <el-option label="设备检修" value="设备检修" />
              <el-option label="技术监督" value="技术监督" />
              <el-option label="安全规程" value="安全规程" />
              <el-option label="报告规范" value="报告规范" />
            </el-select>
          </el-form-item>
          <el-form-item label="展示思考过程">
            <el-switch v-model="form.enableThinking" />
          </el-form-item>
          <el-form-item label="展示引用来源">
            <el-switch v-model="form.enableCitation" />
          </el-form-item>
        </div>
        <el-button type="primary" :icon="Setting" @click="handleSave">保存配置</el-button>
      </el-form>
    </section>
  </div>
</template>

<style scoped>
.config-page {
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

.form-panel {
  padding: 18px;
  border: 1px solid var(--border-color);
  border-radius: var(--border-radius);
  background: var(--bg-container);
  box-shadow: var(--shadow-xs);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 18px;
}

@media (max-width: 900px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
