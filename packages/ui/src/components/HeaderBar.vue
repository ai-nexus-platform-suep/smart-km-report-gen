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
        <el-dropdown-item @click="handleLogout">
          <el-icon class="dropdown-icon"><SwitchButton /></el-icon>
          退出登录
        </el-dropdown-item>
      </template>
    </el-dropdown>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { API_QA, apiPost, clearToken, getStoredUser } from '@platform/core'
import type { UserInfo } from '@platform/core/types'
import { ArrowDown, Moon, Search, Sunny, SwitchButton, UserFilled } from '@element-plus/icons-vue'

const router = useRouter()
const isDark = ref(false)

const user = computed(() => getStoredUser<UserInfo>())
const username = computed(() => user.value?.nickname || user.value?.username || '用户')
const userDisplayName = computed(() => user.value?.nickname || user.value?.username || '未登录用户')
const userRole = computed(() => {
  if (user.value?.role === 'SUPER_ADMIN') return '超级管理员'
  if (user.value?.role === 'ADMIN') return '管理员'
  return '普通用户'
})

function toggleTheme() {
  isDark.value = !isDark.value
  document.documentElement.setAttribute('data-theme', isDark.value ? 'dark' : '')
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
</style>
