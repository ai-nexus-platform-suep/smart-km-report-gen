<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { AppLayout } from '@platform/ui'
import type { NavItem } from '@platform/ui/src/components/SideNav.vue'
import { ChatDotRound, ChatLineSquare, Setting } from '@element-plus/icons-vue'

const route = useRoute()
const showLayout = computed(() => route.meta.requiresAuth !== false)

const navItems: NavItem[] = [
  { path: '/chat', title: '智能问答', icon: ChatDotRound },
  { path: '/conversations', title: '会话列表', icon: ChatLineSquare },
  {
    path: '/admin',
    title: '管理后台',
    icon: Setting,
    admin: true,
    children: [
      { path: '/admin/qa/dashboard', title: '问答统计' },
      { path: '/admin/qa/config', title: '问答配置' },
      { path: '/admin/qa/retrieval-test', title: '检索测试' },
      { path: '/admin/qa/llm', title: 'LLM配置' },
    ],
  },
]
</script>

<template>
  <AppLayout v-if="showLayout" :nav-items="navItems">
    <RouterView />
  </AppLayout>
  <RouterView v-else />
</template>

<style>
/* B 组只在 qa-web 内做应用级视觉覆盖，不改共享组件结构，避免影响 A/C 组页面。 */
:root {
  --qa-brand: #0f766e;
  --qa-brand-strong: #0b5f59;
  --qa-brand-soft: #e9fbf6;
  --qa-blue: #2563eb;
  --qa-amber: #d97706;
}

[data-theme='dark'] {
  --color-primary-bg: rgba(37, 99, 235, 0.18);
  --bg-page: #09111f;
  --bg-container: #101827;
  --bg-hover: rgba(148, 163, 184, 0.12);
  --text-primary: rgba(248, 250, 252, 0.94);
  --text-secondary: rgba(226, 232, 240, 0.76);
  --text-tertiary: rgba(203, 213, 225, 0.58);
  --border-color: rgba(148, 163, 184, 0.22);
  --border-color-light: rgba(148, 163, 184, 0.14);
  --sidebar-bg: #07111f;
  --sidebar-text: rgba(226, 232, 240, 0.72);
  --sidebar-text-active: #ffffff;
  --sidebar-item-hover: rgba(148, 163, 184, 0.12);
  --sidebar-item-active: linear-gradient(135deg, rgba(15, 118, 110, 0.92), rgba(37, 99, 235, 0.78));
  --sidebar-logo-border: rgba(148, 163, 184, 0.16);
}

.layout .sidebar {
  border-right: 1px solid rgba(148, 163, 184, 0.14);
  background:
    radial-gradient(circle at 20% 8%, rgba(45, 212, 191, 0.16), transparent 30%),
    linear-gradient(180deg, #0b1626 0%, #0a0f1a 100%);
}

.layout .sidebar-logo {
  height: 64px;
  padding: 0 18px;
  border-bottom-color: rgba(255, 255, 255, 0.10);
}

.layout .logo-svg {
  border-radius: 12px;
  filter: drop-shadow(0 8px 18px rgba(37, 99, 235, 0.28));
}

.layout .logo-text {
  font-size: 15px;
  letter-spacing: 0.2px;
}

.layout .side-nav {
  --sidebar-text-color: rgba(226, 232, 240, 0.72);
  background: transparent;
}

.layout .nav-menu {
  padding: 12px 10px;
}

.layout .nav-menu .el-menu-item,
.layout .nav-menu .el-sub-menu__title {
  height: 46px;
  margin: 6px 0;
  border: 1px solid transparent;
  border-radius: 14px;
  font-weight: 600;
}

.layout .nav-menu .el-menu-item:hover,
.layout .nav-menu .el-sub-menu__title:hover {
  border-color: rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.08);
}

.layout .nav-menu .el-menu-item.is-active {
  border-color: rgba(45, 212, 191, 0.28);
  background: linear-gradient(135deg, rgba(15, 118, 110, 0.95), rgba(37, 99, 235, 0.78)) !important;
  box-shadow: 0 14px 28px rgba(15, 23, 42, 0.22);
}

.layout .menu-icon {
  width: 28px;
  height: 28px;
  margin-right: 10px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.08);
}

.layout .side-footer {
  padding: 14px;
  border-top-color: rgba(255, 255, 255, 0.10);
}

.layout .user-badge {
  padding: 10px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.06);
}

.layout .main-content {
  background:
    radial-gradient(circle at 18% 10%, rgba(15, 118, 110, 0.08), transparent 30%),
    radial-gradient(circle at 92% 4%, rgba(37, 99, 235, 0.08), transparent 26%),
    var(--bg-page);
}

[data-theme='dark'] .layout .header {
  background: rgba(16, 24, 39, 0.92);
  backdrop-filter: blur(18px);
}

/* qa-web 聊天页暗色兜底：放在全局样式里，避免 scoped 样式无法覆盖 html 主题属性。 */
[data-theme='dark'] .qa-chat-shell .conversation-panel {
  background:
    linear-gradient(180deg, rgba(16, 24, 39, 0.98), rgba(15, 23, 42, 0.98)),
    radial-gradient(circle at 12% 4%, rgba(45, 212, 191, 0.14), transparent 30%) !important;
}

[data-theme='dark'] .qa-chat-shell .panel-hero {
  border-color: rgba(45, 212, 191, 0.20) !important;
  background:
    radial-gradient(circle at 86% 12%, rgba(59, 130, 246, 0.18), transparent 28%),
    linear-gradient(135deg, rgba(15, 118, 110, 0.18), rgba(30, 41, 59, 0.88)) !important;
}

[data-theme='dark'] .qa-chat-shell .panel-hero strong,
[data-theme='dark'] .qa-chat-shell .panel-hero p {
  color: var(--text-primary) !important;
}

[data-theme='dark'] .qa-chat-shell .hero-stats span,
[data-theme='dark'] .qa-chat-shell .conversation-search .el-input__wrapper,
[data-theme='dark'] .qa-chat-shell .panel-tabs,
[data-theme='dark'] .qa-chat-shell .panel-tabs button.active {
  border-color: rgba(148, 163, 184, 0.20) !important;
  background: rgba(15, 23, 42, 0.76) !important;
  color: var(--text-secondary) !important;
}

[data-theme='dark'] .qa-chat-shell .conversation-item:hover,
[data-theme='dark'] .qa-chat-shell .conversation-item.active {
  border-color: rgba(45, 212, 191, 0.24) !important;
  background: rgba(30, 41, 59, 0.82) !important;
  box-shadow: none !important;
}

[data-theme='dark'] .qa-chat-shell .chat-workspace,
[data-theme='dark'] .qa-chat-shell .insight-panel {
  background:
    linear-gradient(180deg, rgba(16, 24, 39, 0.98), rgba(15, 23, 42, 0.98)),
    radial-gradient(circle at 96% 2%, rgba(37, 99, 235, 0.14), transparent 28%) !important;
}

[data-theme='dark'] .qa-chat-shell .context-strip,
[data-theme='dark'] .qa-chat-shell .message-stream,
[data-theme='dark'] .qa-chat-shell .message-tools {
  background: rgba(15, 23, 42, 0.58) !important;
}

[data-theme='dark'] .qa-chat-shell .context-card,
[data-theme='dark'] .qa-chat-shell .message-card,
[data-theme='dark'] .qa-chat-shell .citation-card {
  border-color: rgba(148, 163, 184, 0.20) !important;
  background: rgba(15, 23, 42, 0.78) !important;
  box-shadow: none !important;
}

[data-theme='dark'] .qa-chat-shell .message-row.user .message-card {
  border-color: rgba(45, 212, 191, 0.24) !important;
  background: linear-gradient(135deg, rgba(15, 118, 110, 0.24), rgba(37, 99, 235, 0.18)) !important;
}

[data-theme='dark'] .qa-chat-shell .inline-thinking {
  border-bottom-color: rgba(148, 163, 184, 0.16) !important;
  background: linear-gradient(90deg, rgba(45, 212, 191, 0.13), rgba(37, 99, 235, 0.11)) !important;
}

[data-theme='dark'] .qa-chat-shell .composer {
  background: rgba(16, 24, 39, 0.96) !important;
}

[data-theme='dark'] .qa-chat-shell .composer .el-textarea__inner,
[data-theme='dark'] .qa-chat-shell .follow-up-bar,
[data-theme='dark'] .qa-chat-shell .quick-prompts button {
  border-color: rgba(148, 163, 184, 0.20) !important;
  background: rgba(15, 23, 42, 0.82) !important;
  color: var(--text-primary) !important;
}

[data-theme='dark'] .qa-chat-shell .quick-prompts button:hover {
  border-color: rgba(45, 212, 191, 0.30) !important;
  background: rgba(15, 118, 110, 0.20) !important;
  color: #7dd3fc !important;
}
</style>
