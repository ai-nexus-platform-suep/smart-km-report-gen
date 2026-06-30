<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import DOMPurify from 'dompurify'
import MarkdownIt from 'markdown-it'
import {
  ArrowDown,
  ChatDotRound,
  CopyDocument,
  Delete,
  Finished,
  Plus,
  Promotion,
  Search,
  Setting,
  VideoPause,
} from '@element-plus/icons-vue'
import {
  createConversation,
  deleteConversation,
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
const deletingConversationId = ref<string | null>(null)
const messageStreamRef = ref<HTMLElement | null>(null)
const stickToBottom = ref(true)
const route = useRoute()
const router = useRouter()
const markdown = new MarkdownIt({
  breaks: true,
  html: false,
  linkify: true,
  typographer: true,
})

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

function waitForNextPaint() {
  return new Promise<void>((resolve) => {
    window.requestAnimationFrame(() => {
      window.requestAnimationFrame(() => resolve())
    })
  })
}

function updateStickToBottomState() {
  const el = messageStreamRef.value
  if (!el) return
  stickToBottom.value = el.scrollHeight - el.scrollTop - el.clientHeight <= 140
}

async function scrollMessagesToBottom(force = false, behavior: ScrollBehavior = force ? 'auto' : 'smooth') {
  if (!force && !stickToBottom.value) return
  await nextTick()
  await waitForNextPaint()
  const el = messageStreamRef.value
  if (!el) return
  el.scrollTo({ top: Math.max(0, el.scrollHeight - el.clientHeight), behavior })
  stickToBottom.value = true
}

function handleComposerKeydown(event: KeyboardEvent) {
  if (event.key !== 'Enter' || event.isComposing) return
  if (event.ctrlKey || event.metaKey) return

  event.preventDefault()
  sendMessage()
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

function renderMarkdown(content = '') {
  return DOMPurify.sanitize(markdown.render(content), {
    ADD_ATTR: ['target', 'rel'],
  })
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
    await scrollMessagesToBottom(true)
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
    await scrollMessagesToBottom(true)
  } catch (error) {
    console.error(error)
    ElMessage.error('新建会话失败。')
  }
}

async function handleDeleteConversation(item: ConversationView) {
  if (deletingConversationId.value) return

  try {
    await ElMessageBox.confirm(`确定删除「${item.title}」吗？删除后该会话的历史消息也会被移除。`, '删除会话', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
      confirmButtonClass: 'el-button--danger',
    })

    deletingConversationId.value = item.id
    if (item.id === activeConversationId.value && isGenerating.value) {
      stopGeneration(false)
    }

    await deleteConversation(item.id)

    const wasActive = item.id === activeConversationId.value
    const nextConversations = conversations.value.filter((conversation) => conversation.id !== item.id)
    conversations.value = nextConversations

    if (wasActive) {
      const nextConversation = nextConversations[0]
      if (nextConversation) {
        activeConversationId.value = nextConversation.id
        await router.replace({ path: '/chat', query: { conversationId: nextConversation.id } })
        await loadMessages(nextConversation.id)
      } else {
        activeConversationId.value = null
        activeAssistantId.value = null
        messages.value = []
        await router.replace({ path: '/chat' })
      }
    }

    ElMessage.success('会话已删除')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      console.error(error)
      ElMessage.error('删除会话失败。')
    }
  } finally {
    deletingConversationId.value = null
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
    thinkingSteps: [],
    thinkingCollapsed: false,
    streaming: true,
  }

  messages.value.push(userMessage, assistantMessage)
  const streamingAssistant = messages.value[messages.value.length - 1]
  activeAssistantId.value = streamingAssistant.id
  isGenerating.value = true
  prompt.value = ''
  stickToBottom.value = true
  scrollMessagesToBottom(true)
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
          const existingIndex = streamingAssistant.thinkingSteps?.findIndex((item) => item.step_type === step.step_type)
          if (existingIndex != null && existingIndex >= 0 && streamingAssistant.thinkingSteps) {
            streamingAssistant.thinkingSteps[existingIndex] = step
          } else {
            streamingAssistant.thinkingSteps = [...(streamingAssistant.thinkingSteps ?? []), step]
          }
        },
        onMessage(data) {
          if (data.message_id) {
            streamingAssistant.id = data.message_id
            activeAssistantId.value = data.message_id
          }
          if (typeof data.content === 'string') {
            streamingAssistant.content = data.content
          } else if (data.delta) {
            streamingAssistant.content += data.delta
          }
          scrollMessagesToBottom()
          if (data.intent) {
            streamingAssistant.intentType = data.intent as ChatMessageView['intentType']
          }
          if (data.finished) {
            finishGeneration(
              streamingAssistant,
              streamingAssistant.citations?.length ? streamingAssistant.citations : [],
              streamingAssistant.thinkingSteps?.length ? streamingAssistant.thinkingSteps : [],
            )
          }
        },
        onCitation(nextCitations) {
          streamingAssistant.citations = nextCitations
          scrollMessagesToBottom()
        },
        onError(message) {
          streamingAssistant.content = message
          streamingAssistant.generateStatus = 2
          streamingAssistant.thinkingSteps = [{ label: '请求失败', content: message, status: 'done' }]
          finishGeneration(streamingAssistant, [], streamingAssistant.thinkingSteps)
          ElMessage.error(message)
        },
        onDone() {
          finishGeneration(
            streamingAssistant,
            streamingAssistant.citations?.length ? streamingAssistant.citations : [],
            streamingAssistant.thinkingSteps?.length ? streamingAssistant.thinkingSteps : [],
          )
        },
      },
    )
    if (!streamingAssistant.content) {
      streamingAssistant.content = '本次请求没有返回回答内容。'
    }
    if (!abortController.signal.aborted) {
      const nextConversationId = doneData?.conversation_id ?? conversationId
      await loadMessages(nextConversationId)
      await loadConversations(nextConversationId)
    }
  } catch (error) {
    if (abortController.signal.aborted) return
    console.error(error)
    streamingAssistant.streaming = false
    streamingAssistant.content = '问答服务暂时不可用，请稍后重试。'
    streamingAssistant.thinkingSteps = [{ label: '请求失败', content: '未能从 mock 或后端服务取得回答。', status: 'done' }]
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
    <!-- 左侧会话栏只保留会话入口，让问答区成为页面主角。 -->
    <aside class="conversation-panel">
      <div class="panel-top">
        <div class="side-brand">
          <p class="eyebrow">智能问答</p>
          <h2>技术监督助手</h2>
        </div>
        <el-button class="new-chat-btn" type="primary" :icon="Plus" :loading="loadingConversations" @click="createNewConversation">
          新建
        </el-button>
      </div>

      <div class="side-status">
        <div class="status-row">
          <span>SSE 服务</span>
          <el-tag size="small" effect="plain" type="success">已连接</el-tag>
        </div>
        <button class="model-config-btn" @click="openModelConfig">
          <span class="config-icon"><Setting /></span>
          <span>
            <strong>模型配置</strong>
            <small>DeepSeek Chat / 流式生成</small>
          </span>
        </button>
        <div class="side-metrics">
          <span><b>{{ knowledgeBaseCount }}</b> 知识库</span>
          <span><b>{{ insightThinkingSteps.length }}</b> 思考步骤</span>
          <span><b>{{ citations.length }}</b> 引用</span>
        </div>
      </div>

      <el-input v-model="query" class="conversation-search" :prefix-icon="Search" placeholder="搜索会话、设备、规程" />

      <div v-loading="loadingConversations" class="conversation-list">
        <div
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
          <el-tooltip content="删除会话" placement="top">
            <el-button
              class="conversation-delete"
              text
              circle
              :icon="Delete"
              :loading="deletingConversationId === item.id"
              @click.stop="handleDeleteConversation(item)"
            />
          </el-tooltip>
        </div>
      </div>
    </aside>

    <!-- 主工作区只保留问答流和输入区，顶部配置全部收纳到左侧。 -->
    <main class="chat-workspace">
      <!-- 消息流区域：用户消息靠右，助手消息靠左。 -->
      <section
        ref="messageStreamRef"
        v-loading="loadingMessages"
        class="message-stream"
        @scroll.passive="updateStickToBottomState"
      >
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
              <div
                v-if="message.content && message.role === 'assistant'"
                class="message-content markdown"
                v-html="renderMarkdown(message.content)"
              />
              <p v-else-if="message.content" class="message-content plain">{{ message.content }}</p>
              <p v-else class="streaming-placeholder">正在组织回答...</p>
              <div v-if="message.citations?.length || message.streaming" class="message-tools">
                <el-button text :icon="CopyDocument">复制</el-button>
                <el-button text :icon="ArrowDown">引用 {{ message.citations?.length ?? 0 }}</el-button>
              </div>
            </div>
          </div>
        </article>
      </section>

      <!-- 输入区固定在中间工作区底部，不随消息列表滚动。 -->
      <footer class="composer">
        <button v-if="!stickToBottom" class="jump-bottom-btn" @click="scrollMessagesToBottom(true, 'smooth')">
          回到底部继续提问
        </button>
        <div class="composer-inner">
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
            @keydown="handleComposerKeydown"
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
        </div>
      </footer>
    </main>

  </div>
</template>

<style scoped>
/* 页面整体采用两栏工作台布局：会话列表收窄，主问答区突出。 */
.qa-chat-shell {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 0;
  height: calc(100vh - var(--header-height) - 48px);
  min-height: 640px;
  overflow: hidden;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 24px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.98)),
    radial-gradient(circle at 8% 4%, rgba(15, 118, 110, 0.08), transparent 26%),
    radial-gradient(circle at 96% 0%, rgba(37, 99, 235, 0.08), transparent 28%);
  box-shadow: 0 22px 60px rgba(15, 23, 42, 0.08);
  color: var(--text-primary);
}

.conversation-panel,
.chat-workspace {
  min-height: 0;
  height: 100%;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
}

.conversation-panel {
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
  padding: 14px 12px;
  border-right: 1px solid rgba(15, 23, 42, 0.07);
  background: rgba(248, 250, 252, 0.62);
}

.conversation-panel::before {
  display: none;
}

.panel-top,
.composer-top,
.composer-actions,
.quick-prompts,
.model-config-btn,
.status-row,
.side-metrics {
  display: flex;
  align-items: center;
}

.panel-top,
.composer-top,
.composer-actions,
.status-row {
  justify-content: space-between;
}

.panel-top {
  position: relative;
  z-index: 1;
  gap: 12px;
  margin-bottom: 12px;
}

.side-brand {
  min-width: 0;
}

.eyebrow,
.status-row {
  color: var(--qa-brand, #0f766e);
  font-size: 12px;
  font-weight: 700;
}

.panel-top h2 {
  margin: 0;
  color: var(--text-primary);
  font-weight: 700;
  letter-spacing: 0;
}

.panel-top h2 {
  overflow: hidden;
  font-size: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.new-chat-btn {
  border: 0;
  border-radius: 999px;
  padding: 8px 12px;
  background: linear-gradient(135deg, #0f766e, #2563eb);
  box-shadow: 0 12px 22px rgba(15, 118, 110, 0.22);
}

.side-status {
  display: grid;
  gap: 8px;
  margin-bottom: 10px;
  padding: 10px;
  border: 1px solid rgba(15, 118, 110, 0.12);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.62);
}

.status-row {
  gap: 8px;
  color: var(--text-tertiary);
  font-size: 12px;
  font-weight: 700;
}

.model-config-btn {
  width: 100%;
  gap: 10px;
  padding: 9px;
  border: 1px solid rgba(15, 118, 110, 0.14);
  border-radius: 12px;
  background: rgba(236, 253, 245, 0.74);
  color: var(--text-primary);
  text-align: left;
  transition:
    border-color var(--transition-fast),
    background var(--transition-fast),
    transform var(--transition-fast);
}

.model-config-btn:hover {
  border-color: rgba(15, 118, 110, 0.32);
  background: rgba(236, 253, 245, 0.94);
  transform: translateY(-1px);
}

.config-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  flex: 0 0 auto;
  border-radius: 10px;
  background: #fff;
  color: var(--qa-brand, #0f766e);
}

.config-icon :deep(svg) {
  width: 17px;
  height: 17px;
}

.model-config-btn strong,
.model-config-btn small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.model-config-btn strong {
  font-size: 13px;
}

.model-config-btn small {
  color: var(--text-tertiary);
  font-size: 12px;
}

.side-metrics {
  gap: 6px;
  flex-wrap: wrap;
}

.side-metrics span {
  padding: 4px 7px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  color: #475467;
  font-size: 12px;
}

.side-metrics b {
  color: #0f766e;
}

.conversation-search {
  position: relative;
  z-index: 1;
  margin-bottom: 10px;
}

.conversation-search :deep(.el-input__wrapper) {
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: 0 0 0 1px rgba(15, 23, 42, 0.06);
}

.conversation-list {
  position: relative;
  z-index: 1;
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 6px;
  min-height: 0;
  overflow-y: auto;
  padding-right: 4px;
  scrollbar-gutter: stable;
}

.conversation-item {
  position: relative;
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr) 26px;
  gap: 8px;
  align-items: start;
  width: 100%;
  padding: 9px;
  border: 1px solid transparent;
  border-radius: 12px;
  color: var(--text-secondary);
  cursor: pointer;
  text-align: left;
  transition: all var(--transition-fast);
}

.conversation-item:hover {
  border-color: rgba(15, 23, 42, 0.06);
  background: rgba(255, 255, 255, 0.68);
}

.conversation-item.active {
  border-color: rgba(15, 118, 110, 0.28);
  background: rgba(236, 253, 245, 0.86);
  color: var(--text-primary);
  box-shadow: inset 3px 0 0 rgba(15, 118, 110, 0.72);
}

.conversation-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.conversation-icon {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  background: rgba(15, 118, 110, 0.10);
  color: var(--qa-brand, #0f766e);
}

.conversation-item.active .conversation-icon {
  background: #0f766e;
  color: #fff;
}

.conversation-icon :deep(svg) {
  width: 17px;
  height: 17px;
}

.conversation-content {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.conversation-title {
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
  font-size: 13px;
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
  -webkit-line-clamp: 1;
}

.conversation-meta {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  color: var(--text-tertiary);
  font-size: 12px;
}

.conversation-delete {
  align-self: start;
  opacity: 0;
  color: var(--text-tertiary);
  transition:
    opacity var(--transition-fast),
    background var(--transition-fast),
    color var(--transition-fast);
}

.conversation-item:hover .conversation-delete,
.conversation-item.active .conversation-delete,
.conversation-delete.is-loading {
  opacity: 1;
}

.conversation-delete:hover {
  background: rgba(217, 45, 32, 0.1);
  color: #d92d20;
}

.chat-workspace {
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.64);
}

.message-stream {
  flex: 1;
  min-height: 0;
  max-height: 100%;
  overflow-y: auto;
  padding: 24px clamp(32px, 6vw, 96px);
  scrollbar-gutter: stable;
  background:
    radial-gradient(circle at top left, rgba(15, 118, 110, 0.06), transparent 30%),
    linear-gradient(180deg, rgba(248, 250, 252, 0.72), rgba(255, 255, 255, 0.86));
}

.message-row {
  display: grid;
  grid-template-columns: 38px minmax(0, 860px);
  gap: 12px;
  margin-bottom: 22px;
}

.message-row.user {
  justify-content: end;
  grid-template-columns: minmax(0, 760px) 38px;
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
  border: 1px solid rgba(15, 23, 42, 0.07);
  border-radius: 14px;
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

.message-content {
  margin: 0;
  padding: 15px 16px;
  color: var(--text-primary);
  line-height: 1.75;
}

.message-content.plain {
  white-space: pre-line;
}

.message-card .streaming-placeholder {
  margin: 0;
  padding: 15px 16px;
  color: var(--text-tertiary);
}

.message-content.markdown {
  overflow-wrap: anywhere;
}

.message-content.markdown :deep(h1),
.message-content.markdown :deep(h2),
.message-content.markdown :deep(h3),
.message-content.markdown :deep(h4) {
  margin: 16px 0 8px;
  color: var(--text-primary);
  font-weight: 700;
  line-height: 1.35;
}

.message-content.markdown :deep(h1:first-child),
.message-content.markdown :deep(h2:first-child),
.message-content.markdown :deep(h3:first-child),
.message-content.markdown :deep(h4:first-child),
.message-content.markdown :deep(p:first-child) {
  margin-top: 0;
}

.message-content.markdown :deep(h1) {
  font-size: 22px;
}

.message-content.markdown :deep(h2) {
  font-size: 19px;
}

.message-content.markdown :deep(h3) {
  font-size: 16px;
}

.message-content.markdown :deep(p) {
  margin: 10px 0;
}

.message-content.markdown :deep(ul),
.message-content.markdown :deep(ol) {
  margin: 10px 0;
  padding-left: 22px;
}

.message-content.markdown :deep(li + li) {
  margin-top: 6px;
}

.message-content.markdown :deep(strong) {
  color: var(--text-primary);
  font-weight: 700;
}

.message-content.markdown :deep(a) {
  color: var(--qa-blue, #2563eb);
  text-decoration: none;
}

.message-content.markdown :deep(a:hover) {
  text-decoration: underline;
}

.message-content.markdown :deep(blockquote) {
  margin: 12px 0;
  padding: 8px 12px;
  border-left: 3px solid rgba(15, 118, 110, 0.34);
  border-radius: 0 10px 10px 0;
  background: rgba(15, 118, 110, 0.06);
  color: var(--text-secondary);
}

.message-content.markdown :deep(code) {
  padding: 2px 5px;
  border-radius: 6px;
  background: rgba(15, 23, 42, 0.06);
  color: #b42318;
  font-family: Consolas, 'SFMono-Regular', Menlo, monospace;
  font-size: 0.92em;
}

.message-content.markdown :deep(pre) {
  overflow-x: auto;
  margin: 12px 0;
  padding: 12px;
  border-radius: 12px;
  background: #0f172a;
}

.message-content.markdown :deep(pre code) {
  padding: 0;
  background: transparent;
  color: #e2e8f0;
  white-space: pre;
}

.message-content.markdown :deep(table) {
  display: block;
  overflow-x: auto;
  width: 100%;
  margin: 12px 0;
  border-collapse: collapse;
}

.message-content.markdown :deep(th),
.message-content.markdown :deep(td) {
  padding: 8px 10px;
  border: 1px solid rgba(15, 23, 42, 0.1);
  text-align: left;
}

.message-content.markdown :deep(th) {
  background: rgba(248, 250, 252, 0.92);
  font-weight: 700;
}

.message-tools {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
  padding: 8px 12px;
  border-top: 1px solid var(--border-color-light);
  background: rgba(248, 250, 252, 0.74);
}

.jump-bottom-btn {
  position: absolute;
  z-index: 5;
  top: -42px;
  left: 50%;
  display: block;
  width: fit-content;
  padding: 8px 14px;
  border: 1px solid rgba(15, 118, 110, 0.18);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.92);
  color: var(--qa-brand, #0f766e);
  font-size: 12px;
  font-weight: 700;
  box-shadow: 0 14px 32px rgba(15, 23, 42, 0.12);
  transform: translateX(-50%);
  backdrop-filter: blur(12px);
}

.composer {
  position: relative;
  flex: 0 0 auto;
  padding: 12px clamp(32px, 6vw, 96px) 18px;
  border-top: 1px solid rgba(15, 23, 42, 0.06);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.78), rgba(248, 250, 252, 0.92));
}

.composer-inner {
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.88);
  padding: 12px;
  box-shadow: 0 14px 34px rgba(15, 23, 42, 0.07);
}

.composer-top {
  gap: 12px;
  margin-bottom: 8px;
  color: var(--text-tertiary);
  font-size: 12px;
}

.composer :deep(.el-textarea__inner) {
  border-radius: 14px;
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

.conversation-list,
.message-stream {
  scrollbar-width: thin;
  scrollbar-color: rgba(15, 118, 110, 0.32) transparent;
}

.conversation-list::-webkit-scrollbar,
.message-stream::-webkit-scrollbar {
  width: 8px;
}

.conversation-list::-webkit-scrollbar-track,
.message-stream::-webkit-scrollbar-track {
  background: transparent;
}

.conversation-list::-webkit-scrollbar-thumb,
.message-stream::-webkit-scrollbar-thumb {
  border: 2px solid transparent;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.26);
  background-clip: padding-box;
}

.conversation-list::-webkit-scrollbar-thumb:hover,
.message-stream::-webkit-scrollbar-thumb:hover {
  background: rgba(37, 99, 235, 0.34);
  background-clip: padding-box;
}

/* 暗色模式单独适配，避免白底卡片和弱对比文字在夜间主题下看不清。 */
:global([data-theme='dark']) .qa-chat-shell {
  border-color: rgba(148, 163, 184, 0.16);
  background: rgba(15, 23, 42, 0.98);
  box-shadow: none;
}

:global([data-theme='dark']) .conversation-panel {
  border-right-color: rgba(148, 163, 184, 0.14);
  background: rgba(16, 24, 39, 0.96);
}

:global([data-theme='dark']) .side-status,
:global([data-theme='dark']) .side-metrics span,
:global([data-theme='dark']) .conversation-search :deep(.el-input__wrapper),
:global([data-theme='dark']) .composer-inner {
  border-color: rgba(148, 163, 184, 0.18);
  background: rgba(15, 23, 42, 0.72);
  color: var(--text-secondary);
}

:global([data-theme='dark']) .model-config-btn {
  border-color: rgba(45, 212, 191, 0.22);
  background: rgba(15, 118, 110, 0.14);
}

:global([data-theme='dark']) .config-icon {
  background: rgba(15, 23, 42, 0.72);
}

:global([data-theme='dark']) .conversation-item:hover,
:global([data-theme='dark']) .conversation-item.active {
  border-color: rgba(45, 212, 191, 0.22);
  background: rgba(30, 41, 59, 0.78);
  box-shadow: none;
}

:global([data-theme='dark']) .chat-workspace {
  background:
    linear-gradient(180deg, rgba(16, 24, 39, 0.98), rgba(15, 23, 42, 0.98)),
    radial-gradient(circle at 96% 2%, rgba(37, 99, 235, 0.12), transparent 28%);
}

:global([data-theme='dark']) .message-tools {
  background: rgba(15, 23, 42, 0.58);
}

:global([data-theme='dark']) .message-card {
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

:global([data-theme='dark']) .message-content.markdown :deep(code) {
  background: rgba(148, 163, 184, 0.14);
  color: #fca5a5;
}

:global([data-theme='dark']) .message-content.markdown :deep(blockquote) {
  background: rgba(45, 212, 191, 0.08);
}

:global([data-theme='dark']) .message-content.markdown :deep(th),
:global([data-theme='dark']) .message-content.markdown :deep(td) {
  border-color: rgba(148, 163, 184, 0.18);
}

:global([data-theme='dark']) .message-content.markdown :deep(th) {
  background: rgba(30, 41, 59, 0.82);
}

:global([data-theme='dark']) .composer {
  background: rgba(16, 24, 39, 0.96);
}

:global([data-theme='dark']) .jump-bottom-btn {
  border-color: rgba(45, 212, 191, 0.24);
  background: rgba(15, 23, 42, 0.88);
  color: #7dd3fc;
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

/* 窄屏时改为单栏，优先保证聊天主流程可用。 */
@media (max-width: 1320px) {
  .qa-chat-shell {
    grid-template-columns: 236px minmax(0, 1fr);
  }
}

@media (max-width: 900px) {
  .qa-chat-shell {
    grid-template-columns: 1fr;
  }

  .conversation-panel {
    max-height: 320px;
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
