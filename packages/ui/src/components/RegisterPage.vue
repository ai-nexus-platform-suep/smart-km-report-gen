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
              <path d="M20 12L26 20L20 28L14 20Z" stroke="white" stroke-width="2"
                    stroke-linecap="round" stroke-linejoin="round"/>
              <circle cx="20" cy="20" r="3" fill="white"/>
            </svg>
          </div>
          <h1 class="brand-name">技术监督辅助平台</h1>
        </div>
        <p class="brand-desc">
          注册账号，即刻体验智能知识管理与报告生成
        </p>
        <div class="brand-features">
          <div class="feature-item">
            <div class="feature-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M16 21v-2a4 4 0 00-4-4H6a4 4 0 00-4-4v2M4 21v-2"/>
                <circle cx="9" cy="7" r="4"/>
                <path d="M20 21v-2M20 7v4M22 9h-4"/>
              </svg>
            </div>
            <span>一键注册，即刻开始使用</span>
          </div>
          <div class="feature-item">
            <div class="feature-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
              </svg>
            </div>
            <span>企业级数据安全保障</span>
          </div>
          <div class="feature-item">
            <div class="feature-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/>
              </svg>
            </div>
            <span>高效稳定的服务体验</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧表单面板 -->
    <div class="auth-form-panel">
      <div class="form-container">
        <div class="form-header">
          <h2>创建账号</h2>
          <p>填写信息完成注册</p>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          class="reg-form"
          @submit.prevent="handleRegister"
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
              placeholder="请输入密码（至少6位）"
              size="large"
              show-password
              :prefix-icon="Lock"
            />
          </el-form-item>

          <el-form-item prop="confirmPassword">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              placeholder="请再次输入密码"
              size="large"
              show-password
              :prefix-icon="Lock"
              @keyup.enter="handleRegister"
            />
          </el-form-item>

          <el-form-item>
            <el-button
              type="primary"
              size="large"
              :loading="loading"
              block
              class="submit-btn"
              @click="handleRegister"
            >
              注 册
            </el-button>
          </el-form-item>
        </el-form>

        <div class="form-footer">
          <span>已有账号？</span>
          <router-link to="/login" class="link">去登录</router-link>
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
import { apiPost } from '@platform/core'
import { API_QA } from '@platform/core'
import type { RegisterRequest, ApiResponse } from '@platform/core/types'

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive<RegisterRequest & { confirmPassword: string }>({
  username: '',
  password: '',
  confirmPassword: '',
})

const validateConfirmPassword = (_rule: any, value: string, callback: any) => {
  if (!value) {
    callback(new Error('请再次输入密码'))
  } else if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度需在 3-50 个字符之间', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 100, message: '密码长度需在 6-100 个字符之间', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' },
  ],
}

function getErrorMessage(error: unknown, fallback: string) {
  if (typeof error === 'object' && error && 'response' in error) {
    const response = (error as { response?: { data?: { message?: string } } }).response
    return response?.data?.message || fallback
  }
  return fallback
}

async function handleRegister() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const res = await apiPost<ApiResponse<null>>(API_QA.AUTH.REGISTER, {
      username: form.username,
      password: form.password,
    })
    if (res.data.code === 200) {
      ElMessage.success('注册成功，请登录')
      router.push('/login')
    } else {
      ElMessage.error(res.data.message || '注册失败')
    }
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '注册失败，请稍后重试'))
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

.reg-form {
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

.submit-btn {
  height: 44px;
  font-size: var(--font-lg);
  font-weight: 600;
  letter-spacing: 2px;
  border-radius: var(--border-radius-sm);
  margin-top: var(--gap-sm);
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
