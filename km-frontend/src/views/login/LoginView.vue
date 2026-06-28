<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const username = ref('')

function goSearch() {
  router.push('/search')
}
</script>

<template>
  <div class="login-page">
    <el-card class="login-card">
      <h2>知识管理系统</h2>
      <p class="hint">EPIC-01 登录功能开发中，可先进入检索页体验脚手架</p>
      <el-input v-model="username" placeholder="用户名（占位）" />
      <el-button type="primary" style="width: 100%; margin-top: 16px" @click="goSearch">
        进入系统
      </el-button>
    </el-card>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
}
.login-card {
  width: 400px;
}
.hint {
  color: #909399;
  font-size: 13px;
  margin: 12px 0 16px;
}
</style>
