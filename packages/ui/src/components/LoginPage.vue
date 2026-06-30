<template>
  <div class="auth-page">
    <!-- 左侧品牌面板 -->
    <div class="auth-brand">
      <div class="brand-bg-shapes">
        <div class="shape shape-1"></div>
        <div class="shape shape-2"></div>
        <div class="shape shape-3"></div>
      </div>
      <div class="brand-content">
        <div class="brand-logo">
          <div class="logo-icon">
            <svg viewBox="0 0 40 40" fill="none">
              <rect width="40" height="40" rx="10" fill="rgba(255,255,255,0.15)"/>
              <path d="M12 20L18 26L28 14" stroke="white" stroke-width="2.5"
                    stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </div>
          <h1 class="brand-name">技术监督辅助平台</h1>
        </div>
        <p class="brand-desc">
          智能知识管理 · 文档解析检索 · 报告自动生成
        </p>
        <div class="brand-features">
          <div class="feature-item">
            <div class="feature-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4M7 10l5 5 5-5M12 15V3"/>
              </svg>
            </div>
            <span>多格式文档知识库管理</span>
          </div>
          <div class="feature-item">
            <div class="feature-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M12 20V10M18 20V4M6 20v-4"/>
              </svg>
            </div>
            <span>RAG 增强智能问答</span>
          </div>
          <div class="feature-item">
            <div class="feature-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/>
                <path d="M14 2v6h6M16 13H8M16 17H8M10 9H8"/>
              </svg>
            </div>
            <span>专业报告一键生成导出</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧表单面板 -->
    <div class="auth-form-panel">
      <div class="form-container">
        <div class="form-header">
          <h2>欢迎回来</h2>
          <p>请登录您的账号以继续</p>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          class="login-form"
          @submit.prevent="handleLogin"
        >
          <el-form-item prop="username">
            <el-input
              v-model="form.username"
              placeholder="请输入用户名"
              size="large"
              :prefix-icon="User"
              class="auth-input"
            />
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              size="large"
              show-password
              :prefix-icon="Lock"
              class="auth-input"
              @keyup.enter="handleLogin"
            />
          </el-form-item>

          <div class="form-extra">
            <el-checkbox v-model="rememberMe" label="记住登录" size="small" />
          </div>

          <el-form-item>
            <el-button
              type="primary"
              size="large"
              :loading="loading"
              block
              class="submit-btn"
              @click="handleLogin"
            >
              登 录
            </el-button>
          </el-form-item>
        </el-form>

        <div class="form-footer">
          <span>还没有账号？</span>
          <router-link to="/register" class="link">立即注册</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance } from 'element-plus'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { apiPost, buildUserFromAuthResponse, setAuthTokens, setStoredUser } from '@platform/core'
import { API_QA } from '@platform/core'
import type { LoginRequest, LoginResponse, ApiResponse } from '@platform/core/types'

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)
const rememberMe = ref(false)
const form = reactive<LoginRequest>({ username: '', password: '' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const res = await apiPost<ApiResponse<LoginResponse>>(API_QA.AUTH.LOGIN, form)
    if (res.data.code === 200 && res.data.data) {
      setAuthTokens(res.data.data)
      setStoredUser(buildUserFromAuthResponse(res.data.data))
      ElMessage.success('登录成功')
      router.push('/')
    } else {
      ElMessage.error(res.data.message || '登录失败')
    }
  } catch {
    ElMessage.error('网络错误，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

/* ======= 左侧品牌面板 ======= */
.auth-brand {
  position: relative;
  flex: 0 0 44%;
  background: linear-gradient(160deg, #0f1729 0%, #1a2744 40%, #1e3a5f 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.brand-bg-shapes {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.shape {
  position: absolute;
  border-radius: 50%;
  opacity: 0.06;
  background: #ffffff;
}

.shape-1 {
  width: 500px;
  height: 500px;
  top: -15%;
  right: -20%;
}

.shape-2 {
  width: 300px;
  height: 300px;
  bottom: 10%;
  left: -10%;
}

.shape-3 {
  width: 200px;
  height: 200px;
  top: 40%;
  right: 30%;
  opacity: 0.04;
}

.brand-content {
  position: relative;
  z-index: 1;
  padding: var(--gap-2xl);
  max-width: 420px;
}

.brand-logo {
  display: flex;
  align-items: center;
  gap: var(--gap-md);
  margin-bottom: var(--gap-lg);
}

.logo-icon svg {
  width: 48px;
  height: 48px;
}

.brand-name {
  font-size: var(--font-2xl);
  font-weight: 700;
  color: #ffffff;
  letter-spacing: 0.5px;
}

.brand-desc {
  font-size: var(--font-base);
  color: rgba(255, 255, 255, 0.55);
  line-height: 1.6;
  margin-bottom: var(--gap-2xl);
}

.brand-features {
  display: flex;
  flex-direction: column;
  gap: var(--gap-lg);
}

.feature-item {
  display: flex;
  align-items: center;
  gap: var(--gap-md);
  color: rgba(255, 255, 255, 0.65);
  font-size: var(--font-sm);
}

.feature-icon {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.08);
  border-radius: var(--border-radius-sm);
  color: rgba(255, 255, 255, 0.7);
}

.feature-icon svg {
  width: 18px;
  height: 18px;
}

/* ======= 右侧表单面板 ======= */
.auth-form-panel {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-page);
}

.form-container {
  width: 400px;
  padding: var(--gap-2xl);
}

.form-header {
  margin-bottom: var(--gap-xl);
}

.form-header h2 {
  font-size: var(--font-2xl);
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: var(--gap-xs);
}

.form-header p {
  font-size: var(--font-base);
  color: var(--text-secondary);
}

.login-form {
  margin-top: var(--gap-sm);
}

.auth-input :deep(.el-input__wrapper) {
  box-shadow: none;
  border: 1px solid var(--border-color);
  border-radius: var(--border-radius-sm);
  transition: border-color var(--transition-fast);
}

.auth-input :deep(.el-input__wrapper:hover) {
  border-color: var(--color-primary-hover);
}

.auth-input :deep(.el-input__wrapper.is-focus) {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(21, 94, 239, 0.10);
}

.form-extra {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--gap-md);
}

.submit-btn {
  height: 44px;
  font-size: var(--font-lg);
  font-weight: 600;
  letter-spacing: 2px;
  border-radius: var(--border-radius-sm);
}

.form-footer {
  text-align: center;
  font-size: var(--font-sm);
  color: var(--text-secondary);
}

.form-footer .link {
  color: var(--color-primary);
  font-weight: 500;
  margin-left: var(--gap-xs);
}

.form-footer .link:hover {
  color: var(--color-primary-hover);
}

/* ======= 响应式 ======= */
@media (max-width: 768px) {
  .auth-brand {
    display: none;
  }

  .auth-form-panel {
    flex: 1;
  }

  .form-container {
    width: 100%;
    max-width: 400px;
    padding: var(--gap-lg);
  }
}
</style>
