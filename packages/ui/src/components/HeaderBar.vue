<template>
  <div class="header-right">
    <div class="integration-chip" title="统一入口当前聚合知识管理、智能问答、报告生成三个模块">
      <span class="chip-dot"></span>
      三模块已聚合
    </div>

    <!-- 搜索按钮（预留） -->
    <button class="icon-btn" title="搜索">
      <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.6"
           stroke-linecap="round" width="18" height="18">
        <circle cx="9" cy="9" r="5"/><path d="M13 13l4 4"/>
      </svg>
    </button>

    <!-- 主题切换 -->
    <button class="icon-btn" :title="isDark ? '切换浅色模式' : '切换深色模式'" @click="toggleTheme">
      <svg v-if="!isDark" viewBox="0 0 20 20" fill="none" stroke="currentColor"
           stroke-width="1.6" stroke-linecap="round" width="18" height="18">
        <path d="M17 12.5A7.5 7.5 0 0110 3a7.5 7.5 0 000 15 7.5 7.5 0 007-5.5"/>
      </svg>
      <svg v-else viewBox="0 0 20 20" fill="currentColor" width="18" height="18">
        <path d="M10 2a1 1 0 011 1v1a1 1 0 11-2 0V3a1 1 0 011-1zm4 8a4 4 0 11-8 0 4 4 0 018 0zm-.464 4.95l.707.707a1 1 0 001.414-1.414l-.707-.707a1 1 0 00-1.414 1.414zm2.12-10.607a1 1 0 010 1.414l-.706.707a1 1 0 11-1.414-1.414l.707-.707a1 1 0 011.414 0zM17 11a1 1 0 100-2h-1a1 1 0 100 2h1zm-7 4a1 1 0 011 1v1a1 1 0 11-2 0v-1a1 1 0 011-1zM5.05 6.464A1 1 0 106.465 5.05l-.708-.707a1 1 0 00-1.414 1.414l.707.707zm1.414 8.486l-.707.707a1 1 0 01-1.414-1.414l.707-.707a1 1 0 011.414 1.414zM4 11a1 1 0 100-2H3a1 1 0 000 2h1z"/>
      </svg>
    </button>

    <!-- 用户下拉 -->
    <el-dropdown trigger="click" popper-class="user-dropdown">
      <div class="user-trigger">
        <el-avatar :size="30" :icon="UserFilled" class="user-avatar" />
        <span class="user-name">{{ username }}</span>
        <svg viewBox="0 0 16 16" fill="currentColor" width="14" height="14" class="chevron">
          <path d="M4 6l4 4 4-4" stroke="currentColor" stroke-width="1.5" fill="none"
                stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
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
          <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5"
               stroke-linecap="round" width="15" height="15" style="margin-right:8px">
            <path d="M6 2H3a1 1 0 00-1 1v10a1 1 0 001 1h3M11 11l3-3-3-3M14 8H6"/>
          </svg>
          退出登录
        </el-dropdown-item>
      </template>
    </el-dropdown>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { clearToken, getStoredUser } from '@platform/core'
import type { UserInfo } from '@platform/core/types'
import { UserFilled } from '@element-plus/icons-vue'

const router = useRouter()
const isDark = ref(false)

const user = computed(() => getStoredUser<UserInfo>())
const username = computed(() => user.value?.nickname || user.value?.username || '用户')
const userDisplayName = computed(() => user.value?.nickname || user.value?.username || '未登录用户')
const userRole = computed(() => (user.value?.role === 'ADMIN' ? '管理员' : '普通用户'))

function toggleTheme() {
  isDark.value = !isDark.value
  document.documentElement.setAttribute('data-theme', isDark.value ? 'dark' : '')
}

function handleLogout() {
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

.integration-chip {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 34px;
  padding: 0 12px;
  border: 1px solid rgba(21, 94, 239, 0.14);
  border-radius: 999px;
  color: var(--accent-blue);
  background: rgba(232, 240, 255, 0.78);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.02em;
}

.chip-dot {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: var(--state-success);
  box-shadow: 0 0 0 5px rgba(22, 163, 74, 0.12);
}

/* 图标按钮 */
.icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 12px;
  color: var(--text-secondary);
  background: rgba(255, 255, 255, 0.62);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.icon-btn:hover {
  background: #ffffff;
  color: var(--text-primary);
  box-shadow: var(--shadow-sm);
}

/* 用户触发区 */
.user-trigger {
  display: flex;
  align-items: center;
  gap: var(--gap-sm);
  min-height: 36px;
  padding: 4px 10px 4px 5px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.62);
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
  flex-shrink: 0;
}

[data-theme='dark'] .integration-chip {
  border-color: rgba(110, 168, 255, 0.24);
  color: var(--platform-accent);
  background: rgba(37, 99, 235, 0.14);
}

[data-theme='dark'] .icon-btn,
[data-theme='dark'] .user-trigger {
  background: rgba(15, 23, 42, 0.68);
}

@media (max-width: 860px) {
  .integration-chip {
    display: none;
  }
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
</style>
