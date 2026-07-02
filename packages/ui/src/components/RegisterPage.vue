<template>
  <div class="auth-page">
    <section class="auth-shell">
      <aside class="auth-intro" aria-label="开户注册">
        <div class="brand-lockup">
          <div class="brand-mark">监</div>
          <div>
            <p class="brand-eyebrow">Create workspace account</p>
            <h1>技术监督辅助平台</h1>
          </div>
        </div>

        <div class="scene-card" aria-hidden="true">
          <svg viewBox="0 0 420 260" role="img" class="scene-illustration">
            <defs>
              <linearGradient id="regBase" x1="96" y1="132" x2="330" y2="222" gradientUnits="userSpaceOnUse">
                <stop stop-color="#e0f2fe" />
                <stop offset="1" stop-color="#1d4ed8" />
              </linearGradient>
              <linearGradient id="regPanel" x1="138" y1="38" x2="292" y2="176" gradientUnits="userSpaceOnUse">
                <stop stop-color="#67e8f9" />
                <stop offset="1" stop-color="#2563eb" />
              </linearGradient>
              <filter id="regShadow" x="-30%" y="-30%" width="170%" height="180%">
                <feDropShadow dx="0" dy="16" stdDeviation="16" flood-color="#020617" flood-opacity="0.24" />
              </filter>
            </defs>

            <ellipse cx="208" cy="226" rx="142" ry="22" fill="rgba(15, 23, 42, 0.25)" />

            <g filter="url(#regShadow)">
              <path d="M80 142L206 76L342 144L216 214Z" fill="#eff6ff" />
              <path d="M80 142L216 214V236L80 164Z" fill="#3b82f6" />
              <path d="M216 214L342 144V166L216 236Z" fill="url(#regBase)" />
            </g>

            <g filter="url(#regShadow)">
              <path d="M140 76L238 24L296 56L198 112Z" fill="url(#regPanel)" />
              <path d="M198 112L296 56V150L198 206Z" fill="#1e40af" />
              <path d="M140 76L198 112V206L140 168Z" fill="#0f766e" />
              <circle cx="178" cy="132" r="18" fill="#dbeafe" />
              <path d="M169 132L176 139L189 122" stroke="#1d4ed8" stroke-width="6" stroke-linecap="round" stroke-linejoin="round" />
              <path d="M216 86L268 58" stroke="rgba(255,255,255,0.72)" stroke-width="7" stroke-linecap="round" />
              <path d="M216 116L258 92" stroke="rgba(219,234,254,0.68)" stroke-width="6" stroke-linecap="round" />
              <path d="M216 146L246 128" stroke="rgba(219,234,254,0.58)" stroke-width="6" stroke-linecap="round" />
            </g>

            <g filter="url(#regShadow)">
              <path d="M78 118L116 98L154 118L116 140Z" fill="#f8fafc" />
              <path d="M116 140L154 118V158L116 182Z" fill="#93c5fd" />
              <path d="M78 118L116 140V182L78 158Z" fill="#dbeafe" />
              <path d="M98 132H130" stroke="#2563eb" stroke-width="5" stroke-linecap="round" />
            </g>

            <g filter="url(#regShadow)">
              <path d="M286 128L326 106L364 128L324 152Z" fill="#ecfeff" />
              <path d="M324 152L364 128V168L324 192Z" fill="#67e8f9" />
              <path d="M286 128L324 152V192L286 168Z" fill="#bae6fd" />
              <path d="M306 146L324 156L346 142" stroke="#0e7490" stroke-width="5" stroke-linecap="round" />
            </g>

            <path d="M146 198C176 170 220 166 260 184" stroke="rgba(125, 211, 252, 0.68)" stroke-width="6" stroke-linecap="round" stroke-dasharray="2 14" />
            <circle cx="132" cy="78" r="7" fill="#38bdf8" />
            <circle cx="326" cy="88" r="6" fill="#93c5fd" />
            <circle cx="254" cy="214" r="6" fill="#22c55e" />
          </svg>
        </div>

        <p class="intro-copy">
          公开注册会创建普通用户账号，默认绑定基础访问权限；登录后可进入统一工作台。
        </p>

        <div class="intro-list">
          <span>普通用户</span>
          <span>JWT 登录</span>
          <span>统一入口</span>
        </div>
      </aside>

      <main class="auth-panel">
        <div class="form-container">
          <div class="form-header">
            <p class="form-eyebrow">Account setup</p>
            <h2>创建账号</h2>
            <p>支持用户名注册或邮箱验证注册</p>
          </div>

          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            class="reg-form"
            @submit.prevent="handleRegister"
          >
            <el-form-item prop="registerType" class="type-item">
              <el-radio-group v-model="form.registerType" class="register-type">
                <el-radio-button label="USERNAME">用户名注册</el-radio-button>
                <el-radio-button label="EMAIL">邮箱注册</el-radio-button>
              </el-radio-group>
            </el-form-item>

            <template v-if="form.registerType === 'USERNAME'">
              <el-form-item prop="username">
                <el-input
                  v-model="form.username"
                  placeholder="用户名，3-50 个字符"
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
                  placeholder="密码，至少 8 位且包含 3 类字符"
                  size="large"
                  autocomplete="new-password"
                  show-password
                  :prefix-icon="Lock"
                  class="auth-input"
                />
              </el-form-item>

              <el-form-item prop="confirmPassword">
                <el-input
                  v-model="form.confirmPassword"
                  type="password"
                  placeholder="再次输入密码"
                  size="large"
                  autocomplete="new-password"
                  show-password
                  :prefix-icon="Lock"
                  class="auth-input"
                  @keyup.enter="handleRegister"
                />
              </el-form-item>
            </template>

            <template v-else>
              <el-form-item prop="email">
                <el-input
                  v-model="form.email"
                  placeholder="邮箱地址"
                  size="large"
                  autocomplete="email"
                  :prefix-icon="Message"
                  class="auth-input"
                />
              </el-form-item>

              <el-form-item prop="emailCode" class="email-code-form-item">
                <div class="email-code-row">
                  <el-input
                    v-model="form.emailCode"
                    placeholder="邮箱验证码"
                    size="large"
                    autocomplete="one-time-code"
                    :prefix-icon="Key"
                    class="auth-input"
                    @keyup.enter="handleRegister"
                  />
                  <el-button
                    type="primary"
                    plain
                    size="large"
                    class="send-code-btn"
                    :loading="sendingCode"
                    :disabled="emailCodeCooldown > 0"
                    @click="sendEmailCode"
                  >
                    {{ sendCodeText }}
                  </el-button>
                </div>
              </el-form-item>
            </template>

            <el-form-item prop="captchaCode" class="captcha-form-item">
              <div class="captcha-row">
              <el-input
                v-model="form.captchaCode"
                placeholder="图形验证码"
                size="large"
                autocomplete="off"
                :prefix-icon="Key"
                class="auth-input"
                @keyup.enter="handleRegister"
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

            <el-form-item prop="acceptTerms" class="terms-item">
              <el-checkbox v-model="form.acceptTerms" label="我确认使用真实账号信息，并遵守平台使用规范" />
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
                {{ submitText }}
              </el-button>
            </el-form-item>
          </el-form>

          <div class="form-footer">
            <span>已有账号？</span>
            <router-link to="/login" class="link">去登录</router-link>
          </div>
        </div>
      </main>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Key, Lock, Message, Refresh, User } from '@element-plus/icons-vue'
import {
  API_QA,
  apiGet,
  apiPost,
  buildUserFromAuthResponse,
  buildUserFromCurrentUser,
  setAuthTokens,
  setStoredUser,
} from '@platform/core'
import type {
  ApiResponse,
  CaptchaResponse,
  CurrentUserResponse,
  LoginResponse,
  SendRegisterCodeRequest,
} from '@platform/core/types'
import {
  buildRegisterPayload,
  getRegisterErrorMessage,
  isRegisterAuthResponse,
  isRegisterSuccess,
  normalizeRegisterForm,
  type RegisterFormModel,
} from './register.helpers'

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)
const sendingCode = ref(false)
const captchaLoading = ref(false)
const captchaImage = ref('')
const emailCodeCooldown = ref(0)
let emailCodeTimer: ReturnType<typeof setInterval> | null = null

const form = reactive<RegisterFormModel>({
  registerType: 'USERNAME',
  username: '',
  password: '',
  confirmPassword: '',
  email: '',
  emailCode: '',
  captchaCode: '',
  captchaKey: '',
  acceptTerms: false,
})

const submitText = computed(() => (form.registerType === 'EMAIL' ? '邮箱验证注册' : '创建账号'))
const sendCodeText = computed(() => (emailCodeCooldown.value > 0 ? `${emailCodeCooldown.value}s` : '发送验证码'))

const validateUsername = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (form.registerType !== 'USERNAME') {
    callback()
    return
  }
  const username = value.trim()
  if (!username) {
    callback(new Error('请输入用户名'))
  } else if (username.length < 3 || username.length > 50) {
    callback(new Error('用户名长度需在 3-50 个字符之间'))
  } else {
    callback()
  }
}

const validatePasswordStrength = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (form.registerType !== 'USERNAME') {
    callback()
    return
  }
  const password = value.trim()
  if (!password) {
    callback(new Error('请输入密码'))
    return
  }
  if (password.length < 8 || password.length > 100) {
    callback(new Error('密码长度需在 8-100 个字符之间'))
    return
  }
  const weakPasswords = new Set([
    'password',
    '12345678',
    '123456789',
    'qwerty123',
    'admin123',
    'abc12345',
    '11111111',
    'aaaaaaaa',
    'password1',
    'pa$$w0rd',
  ])
  if (weakPasswords.has(password.toLowerCase())) {
    callback(new Error('密码过于简单，请使用更复杂的密码'))
    return
  }
  const typeCount = [
    /[A-Z]/.test(password),
    /[a-z]/.test(password),
    /\d/.test(password),
    /[!@#$%^&*()_+\-=[\]{};':"\\|,.<>/?`~]/.test(password),
  ].filter(Boolean).length
  if (typeCount < 3) {
    callback(new Error('密码需包含大写字母、小写字母、数字、特殊字符中的至少3种'))
    return
  }
  callback()
}

const validateConfirmPassword = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (form.registerType !== 'USERNAME') {
    callback()
    return
  }
  if (!value) {
    callback(new Error('请再次输入密码'))
  } else if (value.trim() !== form.password.trim()) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const validateEmail = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (form.registerType !== 'EMAIL') {
    callback()
    return
  }
  const email = value.trim()
  if (!email) {
    callback(new Error('请输入邮箱地址'))
  } else if (!/^[\w.+-]+@[\w-]+\.[\w.-]+$/.test(email)) {
    callback(new Error('邮箱格式不正确'))
  } else {
    callback()
  }
}

const validateEmailCode = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (form.registerType !== 'EMAIL') {
    callback()
    return
  }
  if (!value.trim()) {
    callback(new Error('请输入邮箱验证码'))
  } else {
    callback()
  }
}

const validateTerms = (_rule: unknown, value: boolean, callback: (error?: Error) => void) => {
  if (!value) {
    callback(new Error('请先确认平台使用规范'))
  } else {
    callback()
  }
}

const rules = {
  registerType: [{ required: true, message: '请选择注册方式', trigger: 'change' }],
  username: [{ validator: validateUsername, trigger: 'blur' }],
  password: [{ validator: validatePasswordStrength, trigger: 'blur' }],
  confirmPassword: [{ validator: validateConfirmPassword, trigger: 'blur' }],
  email: [{ validator: validateEmail, trigger: 'blur' }],
  emailCode: [{ validator: validateEmailCode, trigger: 'blur' }],
  captchaCode: [{ required: true, message: '请输入图形验证码', trigger: 'blur' }],
  acceptTerms: [{ validator: validateTerms, trigger: 'change' }],
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
    ElMessage.error(getRegisterErrorMessage(error, '验证码加载失败，请稍后重试'))
  } finally {
    captchaLoading.value = false
  }
}

function startEmailCodeCooldown() {
  emailCodeCooldown.value = 60
  if (emailCodeTimer) clearInterval(emailCodeTimer)
  emailCodeTimer = setInterval(() => {
    emailCodeCooldown.value -= 1
    if (emailCodeCooldown.value <= 0 && emailCodeTimer) {
      clearInterval(emailCodeTimer)
      emailCodeTimer = null
    }
  }, 1000)
}

async function sendEmailCode() {
  if (sendingCode.value || emailCodeCooldown.value > 0) return
  const valid = await formRef.value?.validateField('email').then(() => true).catch(() => false)
  if (!valid) return

  sendingCode.value = true
  try {
    const payload: SendRegisterCodeRequest = { email: form.email.trim().toLowerCase() }
    const res = await apiPost<ApiResponse<null>>(API_QA.AUTH.SEND_REGISTER_CODE, payload)
    if (res.data.code === 200) {
      ElMessage.success(res.data.message || '验证码已发送')
      startEmailCodeCooldown()
    } else {
      ElMessage.error(res.data.message || '验证码发送失败')
    }
  } catch (error) {
    ElMessage.error(getRegisterErrorMessage(error, '验证码发送失败，请稍后重试'))
  } finally {
    sendingCode.value = false
  }
}

async function completeAutoLogin(auth: LoginResponse) {
  setAuthTokens(auth)

  try {
    const profile = await apiGet<ApiResponse<CurrentUserResponse>>(API_QA.AUTH.ME)
    if (profile.data.data) {
      setStoredUser(buildUserFromCurrentUser(profile.data.data))
      return
    }
  } catch {
    // 注册后的 token 已可用；资料拉取失败时先用 token 内的基础身份进入系统。
  }

  setStoredUser(buildUserFromAuthResponse(auth))
}

async function handleRegister() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (!form.captchaKey) {
    ElMessage.error('图形验证码加载失败，请刷新后重试')
    await loadCaptcha()
    return
  }

  const normalized = normalizeRegisterForm(form)
  loading.value = true
  try {
    const res = await apiPost<ApiResponse<LoginResponse | null>>(API_QA.AUTH.REGISTER, buildRegisterPayload(normalized))
    if (!isRegisterSuccess({ status: res.status, body: res.data })) {
      ElMessage.error(res.data.message || '注册失败')
      await loadCaptcha()
      return
    }

    if (!isRegisterAuthResponse(res.data.data)) {
      ElMessage.warning('注册成功，但后端未返回登录态，请前往登录页登录')
      router.push('/login')
      return
    }

    await completeAutoLogin(res.data.data)

    if (normalized.registerType === 'EMAIL') {
      await ElMessageBox.alert(
        '注册成功，已为你自动登录。初始密码已通过邮箱发送，请在右上角“修改密码”中尽快修改密码。',
        '注册成功',
        {
          confirmButtonText: '进入平台',
          type: 'success',
        },
      ).catch(() => undefined)
    } else {
      ElMessage.success('注册成功，已自动登录')
    }
    router.push('/')
  } catch (error) {
    ElMessage.error(getRegisterErrorMessage(error))
    await loadCaptcha()
  } finally {
    loading.value = false
  }
}

watch(
  () => form.registerType,
  () => {
    formRef.value?.clearValidate()
  },
)

onMounted(() => {
  void loadCaptcha()
})

onBeforeUnmount(() => {
  if (emailCodeTimer) clearInterval(emailCodeTimer)
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
  min-height: 640px;
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
  margin: 22px 0 6px;
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
  max-width: 376px;
  padding: 0;
}

.form-header {
  margin-bottom: 28px;
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
  margin-bottom: 6px;
  color: var(--text-primary);
  font-size: 30px;
  font-weight: 800;
}

.form-header p {
  color: var(--text-secondary);
  font-size: var(--font-base);
}

.reg-form {
  margin-top: var(--gap-sm);
}

.auth-input :deep(.el-input__wrapper) {
  min-height: 48px;
  border: 1px solid var(--border-color);
  border-radius: 11px;
  background: #ffffff;
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

.type-item {
  margin-bottom: 18px;
}

.type-item :deep(.el-form-item__content) {
  display: block;
}

.register-type {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  width: 100%;
}

.register-type :deep(.el-radio-button__inner) {
  display: grid;
  width: 100%;
  height: 42px;
  place-items: center;
  font-weight: 700;
}

.email-code-form-item :deep(.el-form-item__content),
.captcha-form-item :deep(.el-form-item__content) {
  display: block;
}

.email-code-row,
.captcha-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 118px;
  gap: 10px;
}

.send-code-btn {
  width: 118px;
  height: 48px;
  border-radius: 11px;
  font-weight: 700;
  white-space: nowrap;
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

.terms-item {
  margin-bottom: 18px;
}

.terms-item :deep(.el-checkbox) {
  align-items: flex-start;
  height: auto;
  color: var(--text-secondary);
  line-height: 1.5;
  white-space: normal;
}

.submit-btn {
  height: 48px;
  border-radius: 11px;
  font-size: var(--font-lg);
  font-weight: 800;
}

.form-footer {
  color: var(--text-secondary);
  font-size: var(--font-sm);
  text-align: center;
}

.form-footer .link {
  margin-left: var(--gap-xs);
  color: var(--color-primary);
  font-weight: 600;
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
    gap: 28px;
    padding: 28px;
  }

  .scene-card {
    margin: 6px 0;
    padding: 0;
  }

  .intro-copy {
    max-width: none;
  }

  .form-container {
    max-width: none;
  }

  .auth-panel {
    padding: 30px 24px;
  }
}
</style>
