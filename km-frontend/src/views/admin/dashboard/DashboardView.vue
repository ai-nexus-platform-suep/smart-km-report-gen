<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="dashboard">
    <header class="header">
      <h1>管理后台</h1>
      <div>
        <el-button link @click="router.push('/admin/settings')">系统设置</el-button>
        <el-button link @click="router.push('/search')">返回检索</el-button>
      </div>
    </header>
    <el-row :gutter="16">
      <el-col :span="8">
        <el-card><p>知识库</p><h2>—</h2></el-card>
      </el-col>
      <el-col :span="8">
        <el-card><p>文档</p><h2>—</h2></el-card>
      </el-col>
      <el-col :span="8">
        <el-card><p>切片</p><h2>—</h2></el-card>
      </el-col>
    </el-row>
    <p class="hint">EPIC-02~05 功能开发后在此展示统计与快捷入口</p>
  </div>
</template>

<style scoped>
.dashboard {
  max-width: 960px;
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
  margin-top: 24px;
  color: #909399;
  font-size: 13px;
}
</style>
