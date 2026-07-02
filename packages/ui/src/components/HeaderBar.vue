<template>
  <div class="header-right">
    <button class="icon-btn" title="搜索">
      <el-icon><Search /></el-icon>
    </button>

    <button class="icon-btn" :title="isDark ? '切换浅色模式' : '切换深色模式'" @click="toggleTheme">
      <el-icon><Moon v-if="!isDark" /><Sunny v-else /></el-icon>
    </button>

    <el-dropdown trigger="click" popper-class="user-dropdown">
      <div class="user-trigger">
        <el-avatar :size="30" :icon="UserFilled" class="user-avatar" />
        <span class="user-name">{{ username }}</span>
        <el-icon class="chevron"><ArrowDown /></el-icon>
      </div>
      <template #dropdown>
        <div class="dropdown-user-info">
          <el-avatar :size="36" :icon="UserFilled" />
          <div>
            <div class="dropdown-name">{{ userDisplayName }}</div>
            <div class="dropdown-role">{{ userRole }}</div>
          </div>
        </div>
        <el-divider style="margin: 8px 0" />
        <el-dropdown-item @click="openPasswordDialog">
          <el-icon class="dropdown-icon"><Lock /></el-icon>
          修改密码
        </el-dropdown-item>
        <el-dropdown-item divided @click="handleLogout">
          <el-icon class="dropdown-icon"><SwitchButton /></el-icon>
          退出登录
        </el-dropdown-item>
      </template>
    </el-dropdown>

    <el-dialog
      v-model="passwordDialogVisible"
      title="修改密码"
      width="420px"
      class="password-dialog"
      modal-class="password-dialog-overlay"
      append-to-body
      align-center
      :close-on-click-modal="!changingPassword"
      :close-on-press-escape="!changingPassword"
      @closed="resetPasswordForm"
    >
      <el-form
        ref="passwordFormRef"
        :model="passwordForm"
        :rules="passwordRules"
        label-position="top"
        class="password-form"
        @submit.prevent="handleChangePassword"
      >
        <el-form-item label="原密码" prop="oldPassword">
          <el-input
            v-model="passwordForm.oldPassword"
            type="password"
            autocomplete="current-password"
            show-password
            :prefix-icon="Lock"
          />
        </el-form-item>

        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="passwordForm.newPassword"
            type="password"
            autocomplete="new-password"
            show-password
            :prefix-icon="Lock"
          />
        </el-form-item>

        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input
            v-model="passwordForm.confirmPassword"
            type="password"
            autocomplete="new-password"
            show-password
            :prefix-icon="Lock"
            @keyup.enter="handleChangePassword"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button :disabled="changingPassword" @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="changingPassword" @click="handleChangePassword">
          确认修改
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { API_QA, apiPost, apiPut, clearToken, getStoredUser } from '@platform/core'
import type { ApiResponse, UserInfo } from '@platform/core/types'
import { ArrowDown, Lock, Moon, Search, Sunny, SwitchButton, UserFilled } from '@element-plus/icons-vue'
import {
  buildChangePasswordPayload,
  getChangePasswordErrorMessage,
  getPasswordStrengthError,
  normalizeChangePasswordForm,
  type ChangePasswordFormModel,
} from './password.helpers'

const router = useRouter()
const isDark = ref(false)
const passwordDialogVisible = ref(false)
const changingPassword = ref(false)
const passwordFormRef = ref<FormInstance>()
const passwordForm = reactive<ChangePasswordFormModel>({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const user = computed(() => getStoredUser<UserInfo>())
const username = computed(() => user.value?.nickname || user.value?.username || '用户')
const userDisplayName = computed(() => user.value?.nickname || user.value?.username || '未登录用户')
const userRole = computed(() => {
  if (user.value?.role === 'SUPER_ADMIN') return '超级管理员'
  if (user.value?.role === 'ADMIN') return '管理员'
  return '普通用户'
})

const validateNewPassword = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  const error = getPasswordStrengthError(value, passwordForm.oldPassword)
  if (error) {
    callback(new Error(error))
    return
  }
  callback()
}

const validateConfirmPassword = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (!value.trim()) {
    callback(new Error('请再次输入新密码'))
    return
  }
  if (value.trim() !== passwordForm.newPassword.trim()) {
    callback(new Error('两次输入的新密码不一致'))
    return
  }
  callback()
}

const passwordRules: FormRules<ChangePasswordFormModel> = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [{ validator: validateNewPassword, trigger: 'blur' }],
  confirmPassword: [{ validator: validateConfirmPassword, trigger: 'blur' }],
}

function toggleTheme() {
  isDark.value = !isDark.value
  document.documentElement.setAttribute('data-theme', isDark.value ? 'dark' : '')
}

function resetPasswordForm() {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  void nextTick(() => passwordFormRef.value?.clearValidate())
}

function openPasswordDialog() {
  passwordDialogVisible.value = true
  resetPasswordForm()
}

async function handleChangePassword() {
  const valid = await passwordFormRef.value?.validate().catch(() => false)
  if (!valid) return

  changingPassword.value = true
  const normalized = normalizeChangePasswordForm(passwordForm)
  try {
    const res = await apiPut<ApiResponse<null>>(
      API_QA.AUTH.CHANGE_PASSWORD,
      buildChangePasswordPayload(normalized),
    )
    if (res.data.code !== 200) {
      ElMessage.error(res.data.message || '密码修改失败')
      return
    }

    ElMessage.success(res.data.message || '密码修改成功，请重新登录')
    passwordDialogVisible.value = false
    clearToken()
    router.push('/login')
  } catch (error) {
    ElMessage.error(getChangePasswordErrorMessage(error))
  } finally {
    changingPassword.value = false
  }
}

async function handleLogout() {
  try {
    await apiPost(API_QA.AUTH.LOGOUT)
  } catch {
    // 本地退出优先，后端 refresh token 失效由服务端过期策略兜底。
  }
  clearToken()
  router.push('/login')
}
</script>

<style scoped>
.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 1px solid var(--platform-border);
  border-radius: 8px;
  color: var(--text-secondary);
  background: rgba(255, 255, 255, 0.74);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.icon-btn .el-icon {
  font-size: 17px;
}

.icon-btn:hover {
  background: #ffffff;
  color: var(--text-primary);
  box-shadow: var(--shadow-sm);
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: var(--gap-sm);
  min-height: 36px;
  padding: 4px 10px 4px 5px;
  border: 1px solid var(--platform-border);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.74);
  cursor: pointer;
  transition:
    background var(--transition-fast),
    box-shadow var(--transition-fast);
  margin-left: var(--gap-xs);
}

.user-trigger:hover {
  background: #ffffff;
  box-shadow: var(--shadow-sm);
}

.user-avatar {
  flex-shrink: 0;
}

.user-name {
  font-size: var(--font-sm);
  color: var(--text-primary);
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chevron {
  color: var(--text-tertiary);
  font-size: 13px;
  flex-shrink: 0;
}

[data-theme='dark'] .icon-btn,
[data-theme='dark'] .user-trigger {
  border-color: rgba(148, 163, 184, 0.2);
  background: rgba(15, 23, 42, 0.72);
}

.password-form :deep(.el-form-item__label) {
  font-weight: 600;
  color: var(--text-primary);
}

.password-form :deep(.el-input__wrapper) {
  min-height: 42px;
  border-radius: 8px;
  box-shadow: none;
}

@media (max-width: 620px) {
  .user-name,
  .chevron {
    display: none;
  }

  .user-trigger {
    padding-right: 5px;
  }
}
</style>

<style>
/* 全局下拉面板样式 */
.user-dropdown {
  min-width: 200px !important;
  border-radius: var(--border-radius-lg) !important;
  box-shadow: var(--shadow-lg) !important;
}

.dropdown-user-info {
  display: flex;
  align-items: center;
  gap: var(--gap-md);
  padding: var(--gap-sm) var(--gap-md);
}

.dropdown-name {
  font-size: var(--font-base);
  font-weight: 600;
  color: var(--text-primary);
}

.dropdown-role {
  font-size: var(--font-xs);
  color: var(--text-tertiary);
  margin-top: 2px;
}

.dropdown-icon {
  margin-right: 8px;
}

[data-theme='dark'] .user-dropdown {
  border-color: rgba(148, 163, 184, 0.18) !important;
  background: #111827 !important;
}

.password-dialog {
  width: min(420px, calc(100vw - 32px)) !important;
  max-width: calc(100vw - 32px);
  margin: 0 !important;
  border-radius: 12px !important;
}

.password-dialog-overlay .el-overlay-dialog {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 16px;
}

.password-dialog .el-dialog__header {
  margin-right: 0;
  padding: 20px 22px 10px;
}

.password-dialog .el-dialog__body {
  padding: 10px 22px 4px;
}

.password-dialog .el-dialog__footer {
  padding: 12px 22px 20px;
}
</style>
