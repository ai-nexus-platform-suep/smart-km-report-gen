<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { checkHealth, type HealthData } from '@/api/health'

const query = ref('')
const health = ref<HealthData | null>(null)
const loading = ref(false)

onMounted(async () => {
  try {
    health.value = await checkHealth()
  } catch {
    health.value = null
  }
})

async function handleSearch() {
  loading.value = true
  // EPIC-06 接入真实检索
  setTimeout(() => {
    loading.value = false
  }, 500)
}
</script>

<template>
  <div class="search-page">
    <header class="header">
      <h1>知识检索</h1>
      <el-tag v-if="health" type="success" size="small">
        后端 {{ health.status }} · {{ health.version }}
      </el-tag>
      <el-tag v-else type="danger" size="small">后端未连接</el-tag>
    </header>

    <el-card>
      <el-input
        v-model="query"
        placeholder="输入检索问题，例如：变压器油温异常如何处理"
        size="large"
        @keyup.enter="handleSearch"
      >
        <template #append>
          <el-button :loading="loading" type="primary" @click="handleSearch">检索</el-button>
        </template>
      </el-input>
      <p class="placeholder-hint">检索功能将在 EPIC-06 实现，当前为工程脚手架页面</p>
    </el-card>
  </div>
</template>

<style scoped>
.search-page {
  max-width: 800px;
  margin: 48px auto;
  padding: 0 16px;
}
.header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
}
.placeholder-hint {
  margin-top: 16px;
  color: #909399;
  font-size: 13px;
}
</style>
