<template>
  <div class="auth-page">
    <section class="auth-shell">
      <aside class="auth-intro" aria-label="平台介绍">
        <div class="brand-lockup">
          <div class="brand-mark">监</div>
          <div>
            <p class="brand-eyebrow">Unified workspace</p>
            <h1>技术监督辅助平台</h1>
          </div>
        </div>

        <div class="scene-card" aria-hidden="true">
          <svg viewBox="0 0 420 260" role="img" class="scene-illustration">
            <defs>
              <linearGradient id="deskTop" x1="84" y1="76" x2="344" y2="200" gradientUnits="userSpaceOnUse">
                <stop stop-color="#f8fbff" />
                <stop offset="1" stop-color="#bfdbfe" />
              </linearGradient>
              <linearGradient id="deskSide" x1="116" y1="168" x2="308" y2="238" gradientUnits="userSpaceOnUse">
                <stop stop-color="#60a5fa" />
                <stop offset="1" stop-color="#1d4ed8" />
              </linearGradient>
              <linearGradient id="panelBlue" x1="142" y1="34" x2="280" y2="158" gradientUnits="userSpaceOnUse">
                <stop stop-color="#38bdf8" />
                <stop offset="1" stop-color="#2563eb" />
              </linearGradient>
              <filter id="softShadow" x="-30%" y="-30%" width="160%" height="170%">
                <feDropShadow dx="0" dy="16" stdDeviation="16" flood-color="#020617" flood-opacity="0.24" />
              </filter>
            </defs>

            <ellipse cx="210" cy="224" rx="140" ry="22" fill="rgba(15, 23, 42, 0.24)" />

            <g filter="url(#softShadow)">
              <path d="M94 140L204 78L328 142L218 206Z" fill="url(#deskTop)" />
              <path d="M94 140L218 206V232L94 166Z" fill="#3b82f6" />
              <path d="M218 206L328 142V168L218 232Z" fill="url(#deskSide)" />
            </g>

            <g filter="url(#softShadow)">
              <path d="M142 68L242 18L294 48L194 100Z" fill="url(#panelBlue)" />
              <path d="M194 100L294 48V124L194 178Z" fill="#1e40af" />
              <path d="M142 68L194 100V178L142 146Z" fill="#0f766e" />
              <path d="M212 72L266 44" stroke="rgba(255,255,255,0.72)" stroke-width="7" stroke-linecap="round" />
              <path d="M210 100L260 74" stroke="rgba(219,234,254,0.68)" stroke-width="6" stroke-linecap="round" />
              <circle cx="176" cy="112" r="11" fill="#bfdbfe" />
              <path d="M171 112L176 117L184 106" stroke="#1d4ed8" stroke-width="4" stroke-linecap="round" stroke-linejoin="round" />
            </g>

            <g filter="url(#softShadow)">
              <path d="M78 120L122 96L168 120L124 146Z" fill="#eff6ff" />
              <path d="M124 146L168 120V164L124 190Z" fill="#93c5fd" />
              <path d="M78 120L124 146V190L78 164Z" fill="#dbeafe" />
              <path d="M96 132L122 146L150 130" stroke="#2563eb" stroke-width="5" stroke-linecap="round" />
            </g>

            <g filter="url(#softShadow)">
              <path d="M276 126L318 104L358 126L316 150Z" fill="#ecfeff" />
              <path d="M316 150L358 126V166L316 190Z" fill="#67e8f9" />
              <path d="M276 126L316 150V190L276 166Z" fill="#bae6fd" />
              <path d="M296 140H334" stroke="#0e7490" stroke-width="5" stroke-linecap="round" />
              <path d="M304 156H326" stroke="#0e7490" stroke-width="5" stroke-linecap="round" />
            </g>

            <path d="M158 192C184 166 216 160 248 178" stroke="rgba(125, 211, 252, 0.68)" stroke-width="6" stroke-linecap="round" stroke-dasharray="2 14" />
            <circle cx="138" cy="82" r="7" fill="#38bdf8" />
            <circle cx="322" cy="82" r="5" fill="#93c5fd" />
            <circle cx="250" cy="206" r="6" fill="#22c55e" />
          </svg>
        </div>

        <p class="intro-copy">
          资料入库、依据检索、问答分析和报告生成在一个工作台内完成。
        </p>

        <div class="intro-list">
          <span>知识沉淀</span>
          <span>智能问答</span>
          <span>报告生成</span>
        </div>
      </aside>

      <main class="auth-panel">
        <div class="form-container">
          <div class="form-header">
            <p class="form-eyebrow">Account access</p>
            <h2>欢迎回来</h2>
            <p>登录后继续访问平台能力</p>
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
                placeholder="用户名或邮箱"
                size="large"
                autocomplete="username"
                :prefix-icon="User"
                class="auth-input"
              />
            </el-form-item>

            <el-form-item prop="password">
              <el-input
                v-model="form.password"
                type="password"
                placeholder="密码"
                size="large"
                autocomplete="current-password"
                show-password
                :prefix-icon="Lock"
                class="auth-input"
                @keyup.enter="handleLogin"
              />
            </el-form-item>

            <el-form-item prop="captchaCode" class="captcha-form-item">
              <div class="captcha-row">
                <el-input
                  v-model="form.captchaCode"
                  placeholder="图形验证码"
                  size="large"
                  autocomplete="off"
                  :prefix-icon="Key"
                  class="auth-input"
                  @keyup.enter="handleLogin"
                />
                <button
                  type="button"
                  class="captcha-button"
                  :disabled="captchaLoading"
                  aria-label="刷新图形验证码"
                  @click="loadCaptcha"
                >
                  <img v-if="captchaImage" :src="captchaImage" alt="图形验证码" />
                  <el-icon v-else :size="20"><Refresh /></el-icon>
                </button>
              </div>
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
                登录
              </el-button>
            </el-form-item>
          </el-form>

          <div class="form-footer">
            <span>还没有账号？</span>
            <router-link to="/register" class="link">立即注册</router-link>
          </div>
        </div>
      </main>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance } from 'element-plus'
import { ElMessage } from 'element-plus'
import { Key, Lock, Refresh, User } from '@element-plus/icons-vue'
import {
  apiGet,
  apiPost,
  buildUserFromAuthResponse,
  buildUserFromCurrentUser,
  setAuthTokens,
  setStoredUser,
} from '@platform/core'
import { API_QA } from '@platform/core'
import type { ApiResponse, CaptchaResponse, CurrentUserResponse, LoginResponse } from '@platform/core/types'
import { buildLoginPayload, normalizeLoginForm, type LoginFormModel } from './login.helpers'

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)
const captchaLoading = ref(false)
const captchaImage = ref('')
const rememberMe = ref(false)
const form = reactive<LoginFormModel>({
  username: '',
  password: '',
  captchaCode: '',
  captchaKey: '',
})

const rules = {
  username: [{ required: true, message: '请输入用户名或邮箱', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captchaCode: [{ required: true, message: '请输入图形验证码', trigger: 'blur' }],
}

function getErrorMessage(error: unknown, fallback: string) {
  if (typeof error === 'object' && error && 'response' in error) {
    const response = (error as { response?: { data?: { message?: string } } }).response
    return response?.data?.message || fallback
  }
  return fallback
}

async function loadCaptcha() {
  captchaLoading.value = true
  try {
    const res = await apiGet<ApiResponse<CaptchaResponse>>(API_QA.AUTH.CAPTCHA, { _t: Date.now() })
    const captcha = res.data.data
    if (res.data.code === 200 && captcha?.captchaKey && captcha?.captchaImage) {
      form.captchaKey = captcha.captchaKey
      form.captchaCode = ''
      captchaImage.value = captcha.captchaImage
    } else {
      ElMessage.error(res.data.message || '验证码加载失败')
    }
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '验证码加载失败，请稍后重试'))
  } finally {
    captchaLoading.value = false
  }
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  const normalized = normalizeLoginForm(form)
  loading.value = true
  try {
    const res = await apiPost<ApiResponse<LoginResponse>>(API_QA.AUTH.LOGIN, buildLoginPayload(normalized))
    if (res.data.code === 200 && res.data.data) {
      const auth = res.data.data
      setAuthTokens(auth)

      try {
        const profile = await apiGet<ApiResponse<CurrentUserResponse>>(API_QA.AUTH.ME)
        setStoredUser(buildUserFromCurrentUser(profile.data.data))
      } catch {
        setStoredUser(buildUserFromAuthResponse(auth))
      }

      ElMessage.success('登录成功')
      router.push('/')
    } else {
      ElMessage.error(res.data.message || '登录失败')
      await loadCaptcha()
    }
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '登录失败，请检查用户名或密码'))
    await loadCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadCaptcha()
})
</script>

<style scoped>
.auth-page {
  display: grid;
  min-height: 100vh;
  place-items: center;
  padding: 32px;
  overflow: auto;
  background:
    radial-gradient(circle at 18% 12%, rgba(37, 99, 235, 0.12), transparent 28%),
    radial-gradient(circle at 86% 80%, rgba(14, 165, 233, 0.12), transparent 26%),
    linear-gradient(180deg, #f7fbff 0%, #edf4fb 100%);
}

.auth-shell {
  display: grid;
  grid-template-columns: minmax(420px, 0.92fr) minmax(390px, 1fr);
  width: min(1080px, 100%);
  min-height: 620px;
  overflow: hidden;
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 18px;
  background: var(--bg-container);
  box-shadow: 0 28px 72px rgba(15, 23, 42, 0.15);
}

.auth-intro {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 46px 46px 42px;
  color: #ffffff;
  background:
    linear-gradient(rgba(148, 163, 184, 0.055) 1px, transparent 1px),
    linear-gradient(90deg, rgba(148, 163, 184, 0.045) 1px, transparent 1px),
    radial-gradient(circle at 24% 12%, rgba(37, 99, 235, 0.28), transparent 28%),
    linear-gradient(145deg, #07111f 0%, #0b1626 58%, #10233a 100%);
  background-size: 34px 34px, 34px 34px, auto, auto;
  overflow: hidden;
}

.auth-intro::after {
  position: absolute;
  right: -80px;
  bottom: -80px;
  width: 260px;
  height: 260px;
  border-radius: 50%;
  background: rgba(37, 99, 235, 0.22);
  filter: blur(60px);
  content: "";
  pointer-events: none;
}

.brand-lockup {
  display: flex;
  align-items: center;
  gap: 14px;
}

.brand-mark {
  display: grid;
  width: 54px;
  height: 54px;
  place-items: center;
  flex-shrink: 0;
  border: 1px solid rgba(147, 197, 253, 0.34);
  border-radius: 13px;
  background: linear-gradient(135deg, #1d4ed8, #2563eb);
  color: #ffffff;
  font-size: 25px;
  font-weight: 800;
  box-shadow: 0 16px 30px rgba(37, 99, 235, 0.34);
}

.brand-eyebrow {
  margin-bottom: 4px;
  color: rgba(226, 232, 240, 0.66);
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
  text-transform: uppercase;
}

.brand-lockup h1 {
  color: #ffffff;
  font-size: 28px;
  font-weight: 800;
  line-height: 1.2;
}

.scene-card {
  position: relative;
  z-index: 1;
  display: grid;
  place-items: center;
  width: 100%;
  margin: 26px 0 8px;
  padding: 16px 8px;
}

.scene-illustration {
  display: block;
  width: min(100%, 410px);
  height: auto;
}

.intro-copy {
  position: relative;
  z-index: 1;
  max-width: 360px;
  margin-top: 0;
  color: rgba(226, 232, 240, 0.76);
  font-size: 15px;
  line-height: 1.8;
}

.intro-list {
  position: relative;
  z-index: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.intro-list span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 30px;
  padding: 0 10px;
  border: 1px solid rgba(226, 232, 240, 0.18);
  border-radius: 6px;
  color: rgba(226, 232, 240, 0.74);
  background: rgba(255, 255, 255, 0.055);
  font-size: 12px;
  font-weight: 600;
}

.auth-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 52px;
  background: var(--bg-container);
}

.form-container {
  width: 100%;
  max-width: 368px;
  padding: 0;
}

.form-header {
  margin-bottom: 30px;
}

.form-eyebrow {
  margin-bottom: 10px;
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 800;
  line-height: 1;
  text-transform: uppercase;
}

.form-header h2 {
  font-size: 30px;
  font-weight: 800;
  color: var(--text-primary);
  margin-bottom: 6px;
}

.form-header p {
  font-size: var(--font-base);
  color: var(--text-secondary);
}

.login-form {
  margin-top: var(--gap-sm);
}

.auth-input :deep(.el-input__wrapper) {
  min-height: 48px;
  border-radius: 11px;
  background: #ffffff;
  border: 1px solid var(--border-color);
  box-shadow: none;
  transition: border-color var(--transition-fast);
}

.auth-input :deep(.el-input__wrapper:hover) {
  border-color: var(--color-primary-hover);
}

.auth-input :deep(.el-input__wrapper.is-focus) {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(21, 94, 239, 0.10);
}

.captcha-form-item :deep(.el-form-item__content) {
  display: block;
}

.captcha-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 118px;
  gap: 10px;
}

.captcha-button {
  display: grid;
  min-width: 0;
  height: 48px;
  place-items: center;
  border: 1px solid var(--border-color);
  border-radius: 11px;
  background: #ffffff;
  color: var(--text-secondary);
  cursor: pointer;
  overflow: hidden;
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast);
}

.captcha-button:hover:not(:disabled) {
  border-color: var(--color-primary-hover);
  box-shadow: 0 0 0 3px rgba(21, 94, 239, 0.08);
}

.captcha-button:disabled {
  cursor: wait;
  opacity: 0.68;
}

.captcha-button img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.form-extra {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--gap-md);
}

.submit-btn {
  height: 48px;
  font-size: var(--font-lg);
  font-weight: 800;
  border-radius: 11px;
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

[data-theme='dark'] .auth-page {
  background:
    linear-gradient(135deg, rgba(80, 132, 255, 0.12), transparent 34%),
    linear-gradient(180deg, #0b1220 0%, #111827 100%);
}

[data-theme='dark'] .auth-shell {
  border-color: rgba(148, 163, 184, 0.18);
  background: #111827;
  box-shadow: 0 24px 64px rgba(0, 0, 0, 0.34);
}

[data-theme='dark'] .auth-panel {
  background: #111827;
}

[data-theme='dark'] .auth-input :deep(.el-input__wrapper) {
  border-color: rgba(148, 163, 184, 0.24);
  background: #0f172a;
}

[data-theme='dark'] .auth-input :deep(.el-input__wrapper:hover) {
  border-color: rgba(110, 168, 255, 0.54);
}

[data-theme='dark'] .captcha-button {
  border-color: rgba(148, 163, 184, 0.24);
  background: #0f172a;
  color: rgba(226, 232, 240, 0.72);
}

@media (max-width: 820px) {
  .auth-page {
    padding: 18px;
    place-items: start center;
  }

  .auth-shell {
    grid-template-columns: 1fr;
    min-height: auto;
  }

  .auth-intro {
    gap: 30px;
    padding: 28px;
  }

  .scene-card {
    margin: 8px 0;
    padding: 0;
  }

  .intro-copy {
    max-width: none;
    margin-top: 24px;
  }

  .form-container {
    max-width: none;
  }

  .auth-panel {
    padding: 30px 24px;
  }
}
</style>
