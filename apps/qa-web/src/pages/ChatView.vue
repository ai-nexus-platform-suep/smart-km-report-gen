<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowDown,
  ChatDotRound,
  Collection,
  Connection,
  CopyDocument,
  DocumentChecked,
  Finished,
  MagicStick,
  Plus,
  Promotion,
  Search,
  Setting,
  VideoPause,
} from '@element-plus/icons-vue'
import {
  createConversation,
  listConversations,
  listMessages,
  streamChatMessage,
  type ChatMessageView,
  type ConversationView,
} from '../api'
import type { ThinkingStep } from '@platform/core'

const query = ref('')
const prompt = ref('汽轮机大修周期是否必须固定为 4 年？请结合规程说明判断依据。')
const followUp = ref('')
const activeConversationId = ref<string | null>(null)
const isGenerating = ref(false)
const activeAssistantId = ref<string | null>(null)
const streamTimer = ref<number | null>(null)
const streamController = ref<AbortController | null>(null)
const loadingConversations = ref(false)
const loadingMessages = ref(false)
const conversations = ref<ConversationView[]>([])
const messages = ref<ChatMessageView[]>([])
const route = useRoute()
const router = useRouter()

const filteredConversations = computed(() => {
  const keyword = query.value.trim()
  if (!keyword) return conversations.value
  return conversations.value.filter((item) => `${item.title}${item.summary}${item.tag ?? ''}`.includes(keyword))
})

const activeConversation = computed(() => {
  return (
    conversations.value.find((item) => item.id === activeConversationId.value) ?? {
      id: '',
      title: '新对话',
      summary: '开始一次技术监督问答',
      description: '开始一次技术监督问答',
      messageCount: 0,
      citationCount: 0,
      lastMessageAt: '',
      updatedAt: '--',
      createdAt: '',
      knowledgeBases: [],
      owner: '当前用户',
      status: 'empty' as const,
      tag: '待提问',
    }
  )
})

const activeAssistant = computed(() => messages.value.find((item) => item.id === activeAssistantId.value))
const latestAssistant = computed(() => [...messages.value].reverse().find((item) => item.role === 'assistant'))
const insightThinkingSteps = computed(() => activeAssistant.value?.thinkingSteps ?? latestAssistant.value?.thinkingSteps ?? [])
const citations = computed(() => activeAssistant.value?.citations ?? latestAssistant.value?.citations ?? [])
const composerHint = computed(() =>
  isGenerating.value ? '正在生成中，可停止；补充问题会在本轮结束后作为下一轮发送。' : '当前将通过真实 SSE 接口发送到 B 组问答服务',
)
const conversationCount = computed(() => conversations.value.length)
const knowledgeBaseCount = computed(() => new Set(conversations.value.flatMap((item) => item.knowledgeBases)).size)

async function selectConversation(id: string) {
  if (isGenerating.value) {
    stopGeneration(false)
  }
  if (activeConversationId.value === id && messages.value.length) return
  activeConversationId.value = id
  await router.replace({ path: '/chat', query: { conversationId: id } })
  await loadMessages(id)
}

function formatTime() {
  return new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

function formatCreatedAt() {
  return new Date().toISOString()
}

function toggleThinking(message: ChatMessageView) {
  message.thinkingCollapsed = !message.thinkingCollapsed
}

function normalizeFinalThinkingSteps(steps: ThinkingStep[] = []) {
  return steps.map((step) => ({ ...step, status: 'done' as const }))
}

function finishGeneration(message: ChatMessageView, finalCitations = message.citations ?? [], finalThinkingSteps = message.thinkingSteps ?? []) {
  if (streamTimer.value) {
    window.clearInterval(streamTimer.value)
    streamTimer.value = null
  }
  streamController.value = null
  message.streaming = false
  message.citations = finalCitations
  message.thinkingSteps = normalizeFinalThinkingSteps(finalThinkingSteps)
  isGenerating.value = false
}

async function loadConversations(targetConversationId = activeConversationId.value) {
  loadingConversations.value = true
  try {
    const result = await listConversations({ page: 1, size: 20, user_id: 1 })
    conversations.value = result.items

    if (result.items.length) {
      const routeConversationId = String(route.query.conversationId ?? '')
      const hasRouteConversation = result.items.some((item) => item.id === routeConversationId)
      const hasTargetConversation = result.items.some((item) => item.id === targetConversationId)
      const targetId = hasTargetConversation
        ? targetConversationId
        : hasRouteConversation
          ? routeConversationId
          : result.items[0].id
      await selectConversation(targetId)
    } else {
      activeConversationId.value = null
      messages.value = []
      activeAssistantId.value = null
    }
  } catch (error) {
    console.error(error)
    ElMessage.error('会话列表加载失败，请检查 mock 或后端服务。')
  } finally {
    loadingConversations.value = false
  }
}

async function loadMessages(conversationId: string) {
  loadingMessages.value = true
  try {
    const result = await listMessages(conversationId)
    messages.value = result.messages
    activeAssistantId.value = [...result.messages].reverse().find((item) => item.role === 'assistant')?.id ?? null
  } catch (error) {
    console.error(error)
    ElMessage.error('历史消息加载失败。')
    messages.value = []
  } finally {
    loadingMessages.value = false
  }
}

async function createNewConversation() {
  if (isGenerating.value) return
  try {
    const conversation = await createConversation('新对话')
    conversations.value = [conversation, ...conversations.value.filter((item) => item.id !== conversation.id)]
    activeConversationId.value = conversation.id
    messages.value = []
    activeAssistantId.value = null
    await router.replace({ path: '/chat', query: { conversationId: conversation.id } })
    prompt.value = '请说明技术监督问答可以如何帮助我定位规程依据。'
  } catch (error) {
    console.error(error)
    ElMessage.error('新建会话失败。')
  }
}

async function sendMessage(extraText = '') {
  const text = (extraText || prompt.value).trim()
  if (!text || isGenerating.value) return
  if (!activeConversationId.value) {
    await createNewConversation()
  }
  if (!activeConversationId.value) return
  const conversationId = activeConversationId.value

  const createdAt = formatCreatedAt()
  const userMessage: ChatMessageView = {
    id: `local-user-${Date.now()}`,
    conversationId,
    role: 'user',
    content: text,
    createdAt,
    time: formatTime(),
  }
  const assistantMessage: ChatMessageView = {
    id: `local-assistant-${Date.now()}`,
    conversationId,
    role: 'assistant',
    content: '',
    createdAt,
    time: formatTime(),
    thinkingSteps: [
      { label: '意图识别', content: '正在判断问题类型、设备对象和监督场景。', status: 'done' },
      { label: '知识检索', content: '正在由 qa-agent 根据 selected_kb_ids 和工作流召回相关片段。', status: 'running' },
      { label: '回答组织', content: '等待检索完成后按依据、判断、建议输出。', status: 'pending' },
    ],
    thinkingCollapsed: false,
    streaming: true,
  }

  messages.value.push(userMessage, assistantMessage)
  activeAssistantId.value = assistantMessage.id
  isGenerating.value = true
  prompt.value = ''
  const abortController = new AbortController()
  streamController.value = abortController

  try {
    const doneData = await streamChatMessage(
      {
        conversation_id: conversationId,
        question: text,
        user_id: 1,
        selected_kb_ids: [],
      },
      {
        signal: abortController.signal,
        onThinking(step) {
          const existingIndex = assistantMessage.thinkingSteps?.findIndex((item) => item.step_type === step.step_type)
          if (existingIndex != null && existingIndex >= 0 && assistantMessage.thinkingSteps) {
            assistantMessage.thinkingSteps[existingIndex] = step
          } else {
            assistantMessage.thinkingSteps = [...(assistantMessage.thinkingSteps ?? []), step]
          }
        },
        onMessage(data) {
          if (data.message_id) {
            assistantMessage.id = data.message_id
            activeAssistantId.value = data.message_id
          }
          if (typeof data.content === 'string') {
            assistantMessage.content = data.content
          } else if (data.delta) {
            assistantMessage.content += data.delta
          }
          if (data.intent) {
            assistantMessage.intentType = data.intent as ChatMessageView['intentType']
          }
          if (data.finished) {
            finishGeneration(
              assistantMessage,
              assistantMessage.citations?.length ? assistantMessage.citations : [],
              assistantMessage.thinkingSteps?.length ? assistantMessage.thinkingSteps : [],
            )
          }
        },
        onCitation(nextCitations) {
          assistantMessage.citations = nextCitations
        },
        onError(message) {
          assistantMessage.content = message
          assistantMessage.generateStatus = 2
          assistantMessage.thinkingSteps = [{ label: '请求失败', content: message, status: 'done' }]
          finishGeneration(assistantMessage, [], assistantMessage.thinkingSteps)
          ElMessage.error(message)
        },
        onDone() {
          finishGeneration(
            assistantMessage,
            assistantMessage.citations?.length ? assistantMessage.citations : [],
            assistantMessage.thinkingSteps?.length ? assistantMessage.thinkingSteps : [],
          )
        },
      },
    )
    if (!assistantMessage.content) {
      assistantMessage.content = '本次请求没有返回回答内容。'
    }
    if (!abortController.signal.aborted) {
      const nextConversationId = doneData?.conversation_id ?? conversationId
      await loadMessages(nextConversationId)
      await loadConversations(nextConversationId)
    }
  } catch (error) {
    if (abortController.signal.aborted) return
    console.error(error)
    assistantMessage.streaming = false
    assistantMessage.content = '问答服务暂时不可用，请稍后重试。'
    assistantMessage.thinkingSteps = [{ label: '请求失败', content: '未能从 mock 或后端服务取得回答。', status: 'done' }]
    isGenerating.value = false
    streamController.value = null
    ElMessage.error('发送失败，请检查 mock 或后端服务。')
  } finally {
    if (!abortController.signal.aborted) {
      isGenerating.value = false
      streamController.value = null
    }
  }
}

function stopGeneration(showMessage = true) {
  if (!isGenerating.value || !activeAssistant.value) return
  streamController.value?.abort()
  streamController.value = null
  if (streamTimer.value) {
    window.clearInterval(streamTimer.value)
    streamTimer.value = null
  }
  activeAssistant.value.streaming = false
  activeAssistant.value.interrupted = true
  if (showMessage) {
    activeAssistant.value.content += activeAssistant.value.content ? '\n\n已停止生成。' : '已停止生成。'
  }
  activeAssistant.value.thinkingSteps = activeAssistant.value.thinkingSteps?.map((step) => ({
    ...step,
    status: step.status === 'running' ? 'done' : step.status,
  }))
  isGenerating.value = false
}

function appendFollowUp() {
  const text = followUp.value.trim()
  if (!text) return
  followUp.value = ''
  if (activeAssistant.value?.streaming) {
    ElMessage.info('当前回答仍在生成，请停止或等待完成后再发送补充问题。')
    followUp.value = text
    return
  }
  sendMessage(text)
}

function openModelConfig() {
  router.push('/admin/qa/llm')
}

onMounted(() => {
  loadConversations()
})

watch(
  () => route.query.conversationId,
  async (conversationId) => {
    const nextId = String(conversationId ?? '')
    if (!nextId || nextId === activeConversationId.value) return
    await selectConversation(nextId)
  },
)

onBeforeUnmount(() => {
  streamController.value?.abort()
  if (streamTimer.value) {
    window.clearInterval(streamTimer.value)
  }
})
</script>

<template>
  <div class="qa-chat-shell">
    <!-- 左栏：会话搜索与切换，风格参考 FastGPT 的工作台侧栏。 -->
    <aside class="conversation-panel">
      <div class="panel-top">
        <div>
          <p class="eyebrow">智能问答</p>
          <h2>技术监督助手</h2>
        </div>
        <el-button class="new-chat-btn" type="primary" :icon="Plus" :loading="loadingConversations" @click="createNewConversation">
          新建
        </el-button>
      </div>

      <!-- 侧栏概览用于制造“产品工作台”感，也方便后续接入真实统计数据。 -->
      <div class="panel-hero">
        <span class="hero-glow" />
        <strong>企业知识问答空间</strong>
        <p>基于规程、报告和检修记录生成可追溯回答。</p>
        <div class="hero-stats">
          <span><b>{{ knowledgeBaseCount }}</b> 已选知识库</span>
          <span><b>{{ conversationCount }}</b> 最近会话</span>
        </div>
      </div>

      <el-input v-model="query" class="conversation-search" :prefix-icon="Search" placeholder="搜索会话、设备、规程" />

      <div class="panel-tabs">
        <button class="active">全部</button>
        <button>收藏</button>
        <button>最近</button>
      </div>

      <div v-loading="loadingConversations" class="conversation-list">
        <button
          v-for="item in filteredConversations"
          :key="item.id"
          class="conversation-item"
          :class="{ active: item.id === activeConversationId }"
          @click="selectConversation(item.id)"
        >
          <span class="conversation-icon"><ChatDotRound /></span>
          <span class="conversation-content">
            <span class="conversation-title">
              {{ item.title }}
              <i v-if="item.id === activeConversationId" />
            </span>
            <span class="conversation-summary">{{ item.summary }}</span>
            <span class="conversation-meta">
              <span>{{ item.tag }}</span>
              <span>{{ item.updatedAt }}</span>
            </span>
          </span>
        </button>
      </div>
    </aside>

    <!-- 中栏：智能对话主工作区，后续在这里接入真实消息、发送、停止生成和 SSE。 -->
    <main class="chat-workspace">
      <header class="chat-header">
        <div>
          <div class="header-kicker">
            <el-icon><Connection /></el-icon>
            已连接问答服务
          </div>
          <h1>{{ activeConversation.title }}</h1>
        </div>
        <div class="header-actions">
          <el-tag effect="plain" type="success">自动意图识别</el-tag>
          <el-button :icon="Setting" @click="openModelConfig">模型配置</el-button>
        </div>
      </header>

      <!-- 当前问答上下文摘要，用来让用户知道本轮回答会基于哪些知识库和参数。 -->
      <section class="context-strip">
        <div class="context-card">
          <span class="context-icon blue"><Collection /></span>
          <span><strong>知识库</strong><small>由 selected_kb_ids 交给后端处理</small></span>
        </div>
        <div class="context-card">
          <span class="context-icon green"><DocumentChecked /></span>
          <span><strong>检索流程</strong><small>qa-agent 根据工作流执行召回与生成</small></span>
        </div>
        <div class="context-card">
          <span class="context-icon amber"><MagicStick /></span>
          <span><strong>DeepSeek Chat</strong><small>SSE 流式生成</small></span>
        </div>
      </section>

      <!-- 消息流区域：用户消息靠右，助手消息靠左。 -->
      <section v-loading="loadingMessages" class="message-stream">
        <article v-for="message in messages" :key="message.id" class="message-row" :class="message.role">
          <div class="avatar">{{ message.role === 'user' ? '我' : 'AI' }}</div>
          <div class="message-body">
            <div class="message-meta">
              <strong>{{ message.role === 'user' ? '用户' : '智能问答助手' }}</strong>
              <span>{{ message.time }}</span>
              <el-tag v-if="message.streaming" size="small" type="warning">生成中</el-tag>
              <el-tag v-if="message.interrupted" size="small" type="info">已中断</el-tag>
            </div>
            <div class="message-card">
              <div v-if="message.role === 'assistant' && message.thinkingSteps?.length" class="inline-thinking">
                <button class="thinking-toggle" @click="toggleThinking(message)">
                  <span>思考过程</span>
                  <small>{{ message.thinkingCollapsed ? '展开' : '收起' }}</small>
                </button>
                <div v-show="!message.thinkingCollapsed" class="inline-thinking-list">
                  <div
                    v-for="step in message.thinkingSteps"
                    :key="`${step.step_type ?? step.label}-${step.phase ?? ''}`"
                    class="inline-thinking-step"
                    :class="step.status"
                  >
                    <span class="inline-dot" />
                    <div>
                      <strong>{{ step.label }}</strong>
                      <p>{{ step.content || step.message }}</p>
                    </div>
                  </div>
                </div>
              </div>
              <p v-if="message.content">{{ message.content }}</p>
              <p v-else class="streaming-placeholder">正在组织回答...</p>
              <div v-if="message.citations?.length || message.streaming" class="message-tools">
                <el-button text :icon="CopyDocument">复制</el-button>
                <el-button text :icon="ArrowDown">引用 {{ message.citations?.length ?? 0 }}</el-button>
              </div>
            </div>
          </div>
        </article>
      </section>

      <!-- 输入区：后续发送时调用聊天接口，SSE 接入后这里也负责停止生成状态。 -->
      <footer class="composer">
        <div class="composer-top">
          <span>{{ composerHint }}</span>
          <el-button text :icon="Finished">检索预览</el-button>
        </div>
        <el-input
          v-model="prompt"
          type="textarea"
          resize="none"
          :autosize="{ minRows: 3, maxRows: 5 }"
          placeholder="输入技术监督问题，支持规程依据、检索引用、报告口径判断"
        />
        <div v-if="isGenerating" class="follow-up-bar">
          <el-input
            v-model="followUp"
            placeholder="本轮完成后发送补充问题，例如：请重点说明延期条件"
            @keyup.enter="appendFollowUp"
          />
          <el-button @click="appendFollowUp">补充</el-button>
        </div>
        <div class="composer-actions">
          <div class="quick-prompts">
            <button @click="prompt = '请解释汽轮机大修周期的判断依据。'">检修周期</button>
            <button @click="prompt = '锅炉检修前需要满足哪些安全规程？'">安全规程</button>
            <button @click="prompt = '设备振动指标异常时应该如何监督处置？'">异常指标</button>
          </div>
          <div class="send-actions">
            <el-button v-if="isGenerating" type="danger" plain :icon="VideoPause" @click="stopGeneration">
              停止生成
            </el-button>
            <el-button v-else type="primary" :icon="Promotion" @click="sendMessage()">发送</el-button>
          </div>
        </div>
      </footer>
    </main>

    <!-- 右栏：解释性信息，承担 Dify 风格的思考过程和引用溯源展示。 -->
    <aside class="insight-panel">
      <section class="insight-section">
        <div class="section-title">
          <span>思考过程</span>
          <el-tag size="small" type="success">运行中</el-tag>
        </div>
        <div class="timeline">
          <el-empty v-if="!insightThinkingSteps.length" description="暂无思考过程" />
          <div
            v-for="step in insightThinkingSteps"
            :key="`${step.step_type ?? step.label}-${step.phase ?? ''}`"
            class="timeline-item"
            :class="step.status"
          >
            <span class="timeline-dot" />
            <div>
              <strong>{{ step.label }}</strong>
              <p>{{ step.content || step.message }}</p>
            </div>
          </div>
        </div>
      </section>

      <section class="insight-section">
        <div class="section-title">
          <span>引用来源</span>
          <span class="section-count">{{ citations.length }}</span>
        </div>
        <div class="citation-list">
          <el-empty v-if="!citations.length" description="暂无引用来源" />
          <article v-for="item in citations" :key="`${item.documentName}-${item.index ?? item.score}`" class="citation-card">
            <div class="citation-head">
              <strong>{{ item.documentName }}</strong>
              <span>{{ Math.round(item.score * 100) }}%</span>
            </div>
            <p>{{ item.content }}</p>
            <small>{{ item.chapterPath || item.source }}</small>
          </article>
        </div>
      </section>
    </aside>
  </div>
</template>

<style scoped>
/* 页面整体采用三栏工作台布局：会话、对话、溯源信息。 */
.qa-chat-shell {
  display: grid;
  grid-template-columns: 304px minmax(0, 1fr) 340px;
  gap: 18px;
  min-height: calc(100vh - var(--header-height) - 48px);
  color: var(--text-primary);
}

.conversation-panel,
.chat-workspace,
.insight-panel {
  min-height: 0;
  border: 1px solid var(--border-color);
  border-radius: 22px;
  background: var(--bg-container);
  box-shadow: var(--shadow-sm);
}

.conversation-panel {
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
  padding: 18px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(248, 250, 252, 0.98)),
    radial-gradient(circle at 12% 4%, rgba(15, 118, 110, 0.14), transparent 30%);
}

.conversation-panel::before {
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background:
    linear-gradient(90deg, rgba(15, 118, 110, 0.08) 1px, transparent 1px),
    linear-gradient(180deg, rgba(15, 118, 110, 0.06) 1px, transparent 1px);
  background-size: 26px 26px;
  content: '';
  mask-image: linear-gradient(180deg, #000, transparent 50%);
  pointer-events: none;
}

.panel-top,
.chat-header,
.header-actions,
.context-card,
.composer-top,
.composer-actions,
.quick-prompts,
.section-title,
.citation-head {
  display: flex;
  align-items: center;
}

.panel-top,
.chat-header,
.composer-top,
.composer-actions,
.section-title,
.citation-head {
  justify-content: space-between;
}

.panel-top {
  position: relative;
  z-index: 1;
  gap: 12px;
  margin-bottom: 14px;
}

.eyebrow,
.header-kicker {
  color: var(--qa-brand, #0f766e);
  font-size: 12px;
  font-weight: 700;
}

.panel-top h2,
.chat-header h1 {
  margin: 0;
  color: var(--text-primary);
  font-weight: 700;
  letter-spacing: 0;
}

.panel-top h2 {
  font-size: 21px;
  letter-spacing: -0.3px;
}

.new-chat-btn {
  border: 0;
  border-radius: 999px;
  background: linear-gradient(135deg, #0f766e, #2563eb);
  box-shadow: 0 12px 22px rgba(15, 118, 110, 0.22);
}

.panel-hero {
  position: relative;
  z-index: 1;
  overflow: hidden;
  margin-bottom: 14px;
  padding: 16px;
  border: 1px solid rgba(15, 118, 110, 0.16);
  border-radius: 18px;
  background:
    radial-gradient(circle at 86% 12%, rgba(37, 99, 235, 0.16), transparent 28%),
    linear-gradient(135deg, rgba(236, 253, 245, 0.96), rgba(239, 246, 255, 0.88));
}

.hero-glow {
  position: absolute;
  right: -28px;
  bottom: -34px;
  width: 96px;
  height: 96px;
  border-radius: 50%;
  background: rgba(20, 184, 166, 0.22);
  filter: blur(4px);
}

.panel-hero strong {
  position: relative;
  z-index: 1;
  display: block;
  color: #0f172a;
  font-size: 15px;
}

.panel-hero p {
  position: relative;
  z-index: 1;
  margin: 8px 0 14px;
  color: #475467;
  font-size: 12px;
  line-height: 1.6;
}

.hero-stats {
  position: relative;
  z-index: 1;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.hero-stats span {
  padding: 6px 9px;
  border: 1px solid rgba(15, 118, 110, 0.12);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.62);
  color: #475467;
  font-size: 12px;
}

.hero-stats b {
  color: #0f766e;
}

.conversation-search {
  position: relative;
  z-index: 1;
  margin-bottom: 14px;
}

.conversation-search :deep(.el-input__wrapper) {
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: 0 0 0 1px rgba(15, 23, 42, 0.06);
}

.panel-tabs {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
  margin-bottom: 12px;
  padding: 4px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  border-radius: 14px;
  background: rgba(241, 245, 249, 0.82);
}

.panel-tabs button {
  height: 30px;
  border-radius: 10px;
  color: var(--text-tertiary);
  font-size: 12px;
  font-weight: 700;
}

.panel-tabs button.active {
  background: #fff;
  color: var(--qa-brand, #0f766e);
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.08);
}

.conversation-list {
  position: relative;
  z-index: 1;
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
  overflow-y: auto;
  padding-right: 2px;
}

.conversation-item {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr);
  gap: 11px;
  width: 100%;
  padding: 12px;
  border: 1px solid transparent;
  border-radius: 18px;
  color: var(--text-secondary);
  text-align: left;
  transition: all var(--transition-fast);
}

.conversation-item:hover {
  border-color: rgba(15, 23, 42, 0.06);
  background: rgba(255, 255, 255, 0.72);
  transform: translateY(-1px);
}

.conversation-item.active {
  border-color: rgba(15, 118, 110, 0.28);
  background:
    linear-gradient(135deg, rgba(236, 253, 245, 0.92), rgba(239, 246, 255, 0.92));
  color: var(--text-primary);
  box-shadow: 0 14px 26px rgba(15, 23, 42, 0.08);
}

.conversation-icon,
.context-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.conversation-icon {
  width: 40px;
  height: 40px;
  border-radius: 14px;
  background: rgba(15, 118, 110, 0.10);
  color: var(--qa-brand, #0f766e);
}

.conversation-item.active .conversation-icon {
  background: linear-gradient(135deg, #0f766e, #2563eb);
  color: #fff;
}

.conversation-icon :deep(svg),
.context-icon :deep(svg) {
  width: 18px;
  height: 18px;
}

.conversation-content {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.conversation-title,
.context-card strong,
.context-card small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: inherit;
  font-weight: 650;
}

.conversation-title i {
  width: 7px;
  height: 7px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: #17b26a;
  box-shadow: 0 0 0 4px rgba(23, 178, 106, 0.12);
}

.conversation-summary {
  display: -webkit-box;
  overflow: hidden;
  color: var(--text-tertiary);
  font-size: 12px;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.conversation-meta {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  color: var(--text-tertiary);
  font-size: 12px;
}

.chat-workspace {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.96)),
    radial-gradient(circle at 96% 2%, rgba(37, 99, 235, 0.08), transparent 28%);
}

.chat-header {
  gap: 16px;
  padding: 22px 26px 16px;
  border-bottom: 1px solid var(--border-color-light);
}

.header-kicker {
  display: flex;
  gap: 6px;
  margin-bottom: 4px;
  align-items: center;
}

.chat-header h1 {
  font-size: 23px;
  letter-spacing: -0.4px;
}

.header-actions {
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.context-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  padding: 14px 26px;
  border-bottom: 1px solid var(--border-color-light);
  background: rgba(248, 250, 252, 0.72);
}

.context-card {
  gap: 10px;
  min-width: 0;
  padding: 12px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.78);
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.04);
}

.context-card strong,
.context-card small {
  display: block;
}

.context-card small {
  color: var(--text-tertiary);
  font-size: 12px;
}

.context-icon {
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  border-radius: 12px;
}

.context-icon.blue {
  background: #eef3ff;
  color: #155eef;
}

.context-icon.green {
  background: #eafaf3;
  color: #17b26a;
}

.context-icon.amber {
  background: #fff5e6;
  color: #b54708;
}

.message-stream {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 28px 26px;
  background:
    radial-gradient(circle at top left, rgba(15, 118, 110, 0.08), transparent 30%),
    linear-gradient(180deg, rgba(248, 250, 252, 0.86), rgba(255, 255, 255, 0.94));
}

.message-row {
  display: grid;
  grid-template-columns: 38px minmax(0, 760px);
  gap: 12px;
  margin-bottom: 22px;
}

.message-row.user {
  justify-content: end;
  grid-template-columns: minmax(0, 700px) 38px;
}

.message-row.user .avatar {
  grid-column: 2;
  grid-row: 1;
  background: #1d2939;
}

.message-row.user .message-body {
  grid-column: 1;
  grid-row: 1;
}

.message-row.user .message-meta {
  justify-content: flex-end;
}

.avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 14px;
  background: linear-gradient(135deg, #0f766e, #2563eb);
  color: #fff;
  font-weight: 700;
  box-shadow: 0 12px 20px rgba(15, 118, 110, 0.16);
}

.message-body {
  min-width: 0;
}

.message-meta {
  display: flex;
  gap: 8px;
  margin-bottom: 6px;
  color: var(--text-tertiary);
  font-size: 12px;
}

.message-meta strong {
  color: var(--text-secondary);
}

.message-card {
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}

.message-row.user .message-card {
  border-color: rgba(15, 118, 110, 0.14);
  background: linear-gradient(135deg, rgba(236, 253, 245, 0.96), rgba(239, 246, 255, 0.92));
}

.inline-thinking {
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
  background:
    linear-gradient(90deg, rgba(15, 118, 110, 0.08), rgba(37, 99, 235, 0.05)),
    rgba(248, 250, 252, 0.9);
}

.thinking-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 10px 14px;
  color: var(--text-secondary);
  font-size: 13px;
}

.thinking-toggle span {
  font-weight: 700;
}

.thinking-toggle small {
  color: var(--qa-blue, #2563eb);
  font-weight: 700;
}

.inline-thinking-list {
  display: grid;
  gap: 10px;
  padding: 0 14px 12px;
}

.inline-thinking-step {
  display: grid;
  grid-template-columns: 12px minmax(0, 1fr);
  gap: 8px;
}

.inline-thinking-step strong {
  display: block;
  margin-bottom: 2px;
  color: var(--text-primary);
  font-size: 13px;
}

.inline-thinking-step p {
  padding: 0;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.inline-dot {
  width: 8px;
  height: 8px;
  margin-top: 6px;
  border-radius: 50%;
  background: #98a2b3;
}

.inline-thinking-step.done .inline-dot {
  background: #17b26a;
}

.inline-thinking-step.running .inline-dot {
  background: #f79009;
  box-shadow: 0 0 0 4px rgba(247, 144, 9, 0.14);
}

.message-card p {
  margin: 0;
  padding: 15px 16px;
  color: var(--text-primary);
  line-height: 1.75;
  white-space: pre-line;
}

.message-card .streaming-placeholder {
  color: var(--text-tertiary);
}

.message-tools {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
  padding: 8px 12px;
  border-top: 1px solid var(--border-color-light);
  background: rgba(248, 250, 252, 0.74);
}

.composer {
  padding: 16px 26px 22px;
  border-top: 1px solid var(--border-color);
  background: rgba(255, 255, 255, 0.92);
}

.composer-top {
  gap: 12px;
  margin-bottom: 8px;
  color: var(--text-tertiary);
  font-size: 12px;
}

.composer :deep(.el-textarea__inner) {
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 0 0 1px rgba(15, 23, 42, 0.08);
  line-height: 1.6;
}

.composer :deep(.el-textarea__inner:focus) {
  box-shadow: 0 0 0 1px rgba(15, 118, 110, 0.36), 0 16px 32px rgba(15, 23, 42, 0.08);
}

.follow-up-bar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  margin-top: 10px;
  padding: 10px;
  border: 1px solid rgba(217, 119, 6, 0.26);
  border-radius: 16px;
  background: rgba(255, 251, 235, 0.92);
}

.composer-actions {
  gap: 12px;
  margin-top: 10px;
}

.quick-prompts {
  gap: 8px;
  min-width: 0;
  flex-wrap: wrap;
}

.quick-prompts button {
  padding: 6px 11px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.62);
  color: var(--text-secondary);
  font-size: 12px;
  transition: all var(--transition-fast);
}

.quick-prompts button:hover {
  border-color: rgba(15, 118, 110, 0.24);
  background: var(--qa-brand-soft, #e9fbf6);
  color: var(--qa-brand, #0f766e);
  transform: translateY(-1px);
}

.send-actions {
  display: flex;
  align-items: center;
  flex: 0 0 auto;
}

.insight-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px;
  overflow-y: auto;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.96)),
    radial-gradient(circle at 30% 0%, rgba(15, 118, 110, 0.10), transparent 28%);
}

.insight-section {
  padding-bottom: 14px;
  border-bottom: 1px solid var(--border-color-light);
}

.insight-section:last-child {
  border-bottom: 0;
}

.section-title {
  gap: 12px;
  margin-bottom: 12px;
  color: var(--text-primary);
  font-weight: 700;
}

.section-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  height: 24px;
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.10);
  color: var(--qa-blue, #2563eb);
  font-size: 12px;
}

.timeline {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.timeline-item {
  display: grid;
  grid-template-columns: 14px minmax(0, 1fr);
  gap: 10px;
}

.timeline-dot {
  width: 10px;
  height: 10px;
  margin-top: 5px;
  border-radius: 50%;
  background: #17b26a;
  box-shadow: 0 0 0 4px rgba(23, 178, 106, 0.14);
}

.timeline-item.running .timeline-dot {
  background: #f79009;
  box-shadow: 0 0 0 4px rgba(247, 144, 9, 0.14);
}

.timeline-item strong {
  display: block;
  margin-bottom: 4px;
}

.timeline-item p,
.citation-card p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.65;
}

.citation-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.citation-card {
  padding: 12px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.04);
}

.citation-head {
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 8px;
}

.citation-head strong {
  min-width: 0;
  color: var(--text-primary);
  font-size: 13px;
}

.citation-head span {
  color: var(--qa-brand, #0f766e);
  font-size: 12px;
  font-weight: 700;
}

.citation-card small {
  display: block;
  margin-top: 8px;
  color: var(--text-tertiary);
}

/* 暗色模式单独适配，避免白底卡片和弱对比文字在夜间主题下看不清。 */
:global([data-theme='dark']) .conversation-panel {
  background:
    linear-gradient(180deg, rgba(16, 24, 39, 0.96), rgba(15, 23, 42, 0.98)),
    radial-gradient(circle at 12% 4%, rgba(45, 212, 191, 0.12), transparent 30%);
}

:global([data-theme='dark']) .panel-hero {
  border-color: rgba(45, 212, 191, 0.18);
  background:
    radial-gradient(circle at 86% 12%, rgba(59, 130, 246, 0.16), transparent 28%),
    linear-gradient(135deg, rgba(15, 118, 110, 0.16), rgba(30, 41, 59, 0.88));
}

:global([data-theme='dark']) .panel-hero strong,
:global([data-theme='dark']) .panel-hero p {
  color: var(--text-primary);
}

:global([data-theme='dark']) .hero-stats span,
:global([data-theme='dark']) .conversation-search :deep(.el-input__wrapper),
:global([data-theme='dark']) .panel-tabs,
:global([data-theme='dark']) .panel-tabs button.active {
  border-color: rgba(148, 163, 184, 0.18);
  background: rgba(15, 23, 42, 0.72);
  color: var(--text-secondary);
}

:global([data-theme='dark']) .conversation-item:hover,
:global([data-theme='dark']) .conversation-item.active {
  border-color: rgba(45, 212, 191, 0.22);
  background: rgba(30, 41, 59, 0.78);
  box-shadow: none;
}

:global([data-theme='dark']) .chat-workspace,
:global([data-theme='dark']) .insight-panel {
  background:
    linear-gradient(180deg, rgba(16, 24, 39, 0.98), rgba(15, 23, 42, 0.98)),
    radial-gradient(circle at 96% 2%, rgba(37, 99, 235, 0.12), transparent 28%);
}

:global([data-theme='dark']) .context-strip,
:global([data-theme='dark']) .message-tools {
  background: rgba(15, 23, 42, 0.58);
}

:global([data-theme='dark']) .context-card,
:global([data-theme='dark']) .message-card,
:global([data-theme='dark']) .citation-card {
  border-color: rgba(148, 163, 184, 0.18);
  background: rgba(15, 23, 42, 0.76);
  box-shadow: none;
}

:global([data-theme='dark']) .message-row.user .message-card {
  border-color: rgba(45, 212, 191, 0.22);
  background: linear-gradient(135deg, rgba(15, 118, 110, 0.22), rgba(37, 99, 235, 0.16));
}

:global([data-theme='dark']) .inline-thinking {
  border-bottom-color: rgba(148, 163, 184, 0.16);
  background: linear-gradient(90deg, rgba(45, 212, 191, 0.12), rgba(37, 99, 235, 0.10));
}

:global([data-theme='dark']) .composer {
  background: rgba(16, 24, 39, 0.96);
}

:global([data-theme='dark']) .composer :deep(.el-textarea__inner),
:global([data-theme='dark']) .follow-up-bar,
:global([data-theme='dark']) .quick-prompts button {
  border-color: rgba(148, 163, 184, 0.18);
  background: rgba(15, 23, 42, 0.78);
  color: var(--text-primary);
}

:global([data-theme='dark']) .quick-prompts button:hover {
  border-color: rgba(45, 212, 191, 0.28);
  background: rgba(15, 118, 110, 0.18);
  color: #7dd3fc;
}

/* 小屏时隐藏右侧溯源栏，优先保证聊天主流程可用。 */
@media (max-width: 1320px) {
  .qa-chat-shell {
    grid-template-columns: 260px minmax(0, 1fr);
  }

  .insight-panel {
    display: none;
  }
}

@media (max-width: 900px) {
  .qa-chat-shell {
    grid-template-columns: 1fr;
  }

  .conversation-panel {
    max-height: 320px;
  }

  .chat-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .context-strip {
    display: flex;
    overflow-x: auto;
  }

  .context-card {
    min-width: 220px;
  }

  .message-row,
  .message-row.user {
    grid-template-columns: 34px minmax(0, 1fr);
    justify-content: stretch;
  }

  .message-row.user .avatar,
  .message-row.user .message-body {
    grid-column: auto;
  }
}
</style>
