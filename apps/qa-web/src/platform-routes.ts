import type { RouteRecordRaw } from 'vue-router'

// Routes exposed to apps/platform-web. qa-web owns its internal page imports;
// platform-web only aggregates this public route list.
export const qaPlatformRoutes: RouteRecordRaw[] = [
  {
    path: '/qa/chat',
    name: 'QaChat',
    component: () => import('./pages/ChatView.vue'),
    meta: { title: '智能对话' },
  },
  {
    path: '/chat',
    name: 'QaChatCompat',
    component: () => import('./pages/ChatView.vue'),
    meta: { title: '智能对话' },
  },
  {
    path: '/qa/conversations',
    name: 'QaConversations',
    component: () => import('./pages/Conversations.vue'),
    meta: { title: '会话记录' },
  },
  {
    path: '/conversations',
    name: 'QaConversationsCompat',
    component: () => import('./pages/Conversations.vue'),
    meta: { title: '会话记录' },
  },
  {
    path: '/qa/retrieval-test',
    name: 'QaRetrievalTest',
    component: () => import('./pages/admin/RetrievalTest.vue'),
    meta: { title: '检索测试', admin: true },
  },
  {
    path: '/admin/qa/retrieval-test',
    name: 'QaRetrievalTestCompat',
    component: () => import('./pages/admin/RetrievalTest.vue'),
    meta: { title: '检索测试', admin: true },
  },
  {
    path: '/qa/settings',
    name: 'QaSettings',
    component: () => import('./pages/admin/QaConfig.vue'),
    meta: { title: '问答配置', admin: true },
  },
  {
    path: '/admin/qa/config',
    name: 'QaSettingsCompat',
    component: () => import('./pages/admin/QaConfig.vue'),
    meta: { title: '问答配置', admin: true },
  },
  {
    path: '/qa/llm',
    name: 'QaLlmSettings',
    component: () => import('./pages/admin/LlmConfig.vue'),
    meta: { title: 'LLM 配置', admin: true },
  },
  {
    path: '/admin/qa/llm',
    name: 'QaLlmSettingsCompat',
    component: () => import('./pages/admin/LlmConfig.vue'),
    meta: { title: 'LLM 配置', admin: true },
  },
  {
    path: '/admin/qa/dashboard',
    name: 'QaAdminDashboard',
    component: () => import('./pages/admin/QaDashboard.vue'),
    meta: { title: '问答统计', admin: true },
  },
]
