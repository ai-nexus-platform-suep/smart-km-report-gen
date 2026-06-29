<script setup lang="ts">
import { computed, ref } from 'vue'
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

type Conversation = {
  id: number
  title: string
  summary: string
  updatedAt: string
  tag: string
}

type Citation = {
  id: number
  documentName: string
  content: string
  score: number
  source: string
}

type ThinkingStep = {
  label: string
  content: string
  status: 'done' | 'running' | 'pending'
}

type Message = {
  id: number
  role: 'user' | 'assistant'
  content: string
  time: string
  citations?: Citation[]
  thinkingSteps?: ThinkingStep[]
  thinkingCollapsed?: boolean
  streaming?: boolean
  interrupted?: boolean
}

// 当前页面先用静态演示数据撑起交互形态，后续替换为 API_QA.CHAT / API_QA.SEARCH 接口返回值。
const query = ref('')
const prompt = ref('汽轮机大修周期是否必须固定为 4 年？请结合规程说明判断依据。')
const followUp = ref('')
const selectedMode = ref('knowledge')
const activeConversationId = ref(1)
const isGenerating = ref(false)
const activeAssistantId = ref<number | null>(null)
const streamTimer = ref<number | null>(null)

// 左侧会话列表，对应后续的 API_QA.CHAT.LIST。
const conversations: Conversation[] = [
  { id: 1, title: '汽轮机检修周期判断', summary: '大修周期、运行小时数、启停次数综合判断', updatedAt: '09:42', tag: '设备检修' },
  { id: 2, title: '锅炉安全规范问答', summary: '工作票、隔离措施、危险点预控', updatedAt: '昨天', tag: '安全规程' },
  { id: 3, title: '电气试验报告解释', summary: '绝缘电阻、介损、试验结论生成', updatedAt: '周二', tag: '试验监督' },
  { id: 4, title: '煤库存审计口径', summary: '盘点口径、异常波动、佐证材料', updatedAt: '6月28日', tag: '经营监督' },
]

// 右侧引用来源，对应后续问答接口中的 citations 字段。
const citations: Citation[] = [
  {
    id: 1,
    documentName: '汽轮机检修规程_v3.0.pdf',
    content: '汽轮机大修周期一般为 4-6 年，需结合运行小时数、启停次数和设备健康状态综合确定。',
    score: 0.92,
    source: '知识库 / 设备检修',
  },
  {
    id: 2,
    documentName: '发电设备技术监督导则.docx',
    content: '当振动、油质、效率等关键指标出现持续异常时，应提前组织状态评估和检修论证。',
    score: 0.86,
    source: '知识库 / 技术监督',
  },
]

// 右侧思考过程，对应后续问答接口中的 thinkingSteps 字段。
const defaultThinkingSteps: ThinkingStep[] = [
  { label: '意图识别', content: '识别为知识问答，主题为汽轮机检修周期判断。', status: 'done' },
  { label: '知识检索', content: '命中 2 份规程文档，最高相关度 0.92。', status: 'done' },
  { label: '生成回答', content: '正在按规程依据、适用条件、建议动作组织回答。', status: 'running' },
]

// 中间消息流，对应后续的 API_QA.CHAT.HISTORY 和 SSE 流式消息。
const messages = ref<Message[]>([
  { id: 1, role: 'user', content: '汽轮机的大修周期一般是多久？如果运行状态良好，可以延期吗？', time: '09:40' },
  {
    id: 2,
    role: 'assistant',
    content:
      '汽轮机大修周期通常不是一个机械固定值。按照现有检修规程，一般可按 4-6 年作为参考范围，但最终应结合运行小时数、启停次数、振动趋势、油质指标、效率变化和缺陷记录综合判断。若运行状态稳定，且监督数据、试验结果、缺陷闭环均满足要求，可以组织状态评估后提出延期建议；如果关键指标持续异常，则应提前安排检修论证。',
    time: '09:41',
    citations,
    thinkingSteps: defaultThinkingSteps.map((step) => ({ ...step, status: 'done' })),
    thinkingCollapsed: true,
  },
])

const filteredConversations = computed(() => {
  const keyword = query.value.trim()
  if (!keyword) return conversations
  return conversations.filter((item) => `${item.title}${item.summary}${item.tag}`.includes(keyword))
})

const activeConversation = computed(
  () => conversations.find((item) => item.id === activeConversationId.value) ?? conversations[0],
)

const activeAssistant = computed(() => messages.value.find((item) => item.id === activeAssistantId.value))
const insightThinkingSteps = computed(() => activeAssistant.value?.thinkingSteps ?? defaultThinkingSteps)
const composerHint = computed(() =>
  isGenerating.value ? '正在生成中，可停止，也可以补充一句让回答更聚焦。' : '当前将基于「设备检修」「技术监督」知识库回答',
)

function selectConversation(id: number) {
  activeConversationId.value = id
}

function formatTime() {
  return new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

function toggleThinking(message: Message) {
  message.thinkingCollapsed = !message.thinkingCollapsed
}

function buildAnswer(question: string) {
  return `针对“${question}”，建议不要把大修周期理解成单一固定年限。规程通常给出 4-6 年的参考区间，但真正落地时要结合运行小时数、启停次数、振动趋势、油质指标、效率变化和缺陷闭环情况综合判断。若状态数据稳定，试验结论正常，且缺陷整改闭环充分，可以发起状态评估并形成延期论证；若关键指标持续异常，应优先安排专项诊断或提前检修。`
}

function runMockStream(message: Message, answer: string) {
  let index = 0
  const chars = Array.from(answer)
  streamTimer.value = window.setInterval(() => {
    if (index >= chars.length) {
      finishGeneration(message)
      return
    }

    message.content += chars[index]
    index += 1

    if (index > chars.length * 0.35 && message.thinkingSteps?.[1]) {
      message.thinkingSteps[1].status = 'done'
    }
    if (index > chars.length * 0.7 && message.thinkingSteps?.[2]) {
      message.thinkingSteps[2].status = 'running'
    }
  }, 28)
}

function finishGeneration(message: Message) {
  if (streamTimer.value) {
    window.clearInterval(streamTimer.value)
    streamTimer.value = null
  }
  message.streaming = false
  message.citations = citations
  message.thinkingSteps = message.thinkingSteps?.map((step) => ({ ...step, status: 'done' }))
  isGenerating.value = false
}

function sendMessage(extraText = '') {
  const text = (extraText || prompt.value).trim()
  if (!text || isGenerating.value) return

  const userMessage: Message = {
    id: Date.now(),
    role: 'user',
    content: text,
    time: formatTime(),
  }
  const assistantMessage: Message = {
    id: Date.now() + 1,
    role: 'assistant',
    content: '',
    time: formatTime(),
    thinkingSteps: [
      { label: '意图识别', content: '正在判断问题类型、设备对象和监督场景。', status: 'done' },
      { label: '知识检索', content: '正在从设备检修、技术监督知识库召回相关片段。', status: 'running' },
      { label: '回答组织', content: '等待检索完成后按依据、判断、建议输出。', status: 'pending' },
    ],
    thinkingCollapsed: false,
    streaming: true,
  }

  messages.value.push(userMessage, assistantMessage)
  activeAssistantId.value = assistantMessage.id
  isGenerating.value = true
  prompt.value = ''
  runMockStream(assistantMessage, buildAnswer(text))
}

function stopGeneration() {
  if (!isGenerating.value || !activeAssistant.value) return
  if (streamTimer.value) {
    window.clearInterval(streamTimer.value)
    streamTimer.value = null
  }
  activeAssistant.value.streaming = false
  activeAssistant.value.interrupted = true
  activeAssistant.value.content += activeAssistant.value.content ? '\n\n已停止生成。' : '已停止生成。'
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
    activeAssistant.value.content += `\n\n补充要求：${text}`
    return
  }
  sendMessage(text)
}
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
        <el-button type="primary" :icon="Plus" circle />
      </div>

      <el-input v-model="query" class="conversation-search" :prefix-icon="Search" placeholder="搜索会话、设备、规程" />

      <div class="conversation-list">
        <button
          v-for="item in filteredConversations"
          :key="item.id"
          class="conversation-item"
          :class="{ active: item.id === activeConversationId }"
          @click="selectConversation(item.id)"
        >
          <span class="conversation-icon"><ChatDotRound /></span>
          <span class="conversation-content">
            <span class="conversation-title">{{ item.title }}</span>
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
            已连接知识库
          </div>
          <h1>{{ activeConversation.title }}</h1>
        </div>
        <div class="header-actions">
          <el-segmented
            v-model="selectedMode"
            :options="[
              { label: '知识问答', value: 'knowledge' },
              { label: '文档检索', value: 'search' },
              { label: '自由对话', value: 'chat' },
            ]"
          />
          <el-button :icon="Setting">参数</el-button>
        </div>
      </header>

      <!-- 当前问答上下文摘要，用来让用户知道本轮回答会基于哪些知识库和参数。 -->
      <section class="context-strip">
        <div class="context-card">
          <span class="context-icon blue"><Collection /></span>
          <span><strong>2 个知识库</strong><small>设备检修、技术监督</small></span>
        </div>
        <div class="context-card">
          <span class="context-icon green"><DocumentChecked /></span>
          <span><strong>Top K 5</strong><small>相似度阈值 0.70</small></span>
        </div>
        <div class="context-card">
          <span class="context-icon amber"><MagicStick /></span>
          <span><strong>DeepSeek Chat</strong><small>流式生成待接入</small></span>
        </div>
      </section>

      <!-- 消息流区域：用户消息靠右，助手消息靠左。 -->
      <section class="message-stream">
        <article v-for="message in messages" :key="message.id" class="message-row" :class="message.role">
          <div class="avatar">{{ message.role === 'user' ? '用' : '问' }}</div>
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
                    :key="step.label"
                    class="inline-thinking-step"
                    :class="step.status"
                  >
                    <span class="inline-dot" />
                    <div>
                      <strong>{{ step.label }}</strong>
                      <p>{{ step.content }}</p>
                    </div>
                  </div>
                </div>
              </div>
              <p v-if="message.content">{{ message.content }}</p>
              <p v-else class="streaming-placeholder">正在组织回答...</p>
              <div v-if="message.citations?.length || message.streaming" class="message-tools">
                <el-button text :icon="CopyDocument">复制</el-button>
                <el-button text :icon="ArrowDown">查看引用</el-button>
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
            placeholder="生成中也可以补充要求，例如：请重点说明延期条件"
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
          <div v-for="step in insightThinkingSteps" :key="step.label" class="timeline-item" :class="step.status">
            <span class="timeline-dot" />
            <div>
              <strong>{{ step.label }}</strong>
              <p>{{ step.content }}</p>
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
          <article v-for="item in citations" :key="item.id" class="citation-card">
            <div class="citation-head">
              <strong>{{ item.documentName }}</strong>
              <span>{{ Math.round(item.score * 100) }}%</span>
            </div>
            <p>{{ item.content }}</p>
            <small>{{ item.source }}</small>
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
  grid-template-columns: 292px minmax(0, 1fr) 340px;
  gap: 16px;
  min-height: calc(100vh - var(--header-height) - 48px);
}

.conversation-panel,
.chat-workspace,
.insight-panel {
  min-height: 0;
  border: 1px solid var(--border-color);
  border-radius: var(--border-radius);
  background: var(--bg-container);
  box-shadow: var(--shadow-xs);
}

.conversation-panel {
  display: flex;
  flex-direction: column;
  padding: 18px;
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
  gap: 12px;
  margin-bottom: 18px;
}

.eyebrow,
.header-kicker {
  color: #0f766e;
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
  font-size: 20px;
}

.conversation-search {
  margin-bottom: 14px;
}

.conversation-list {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 8px;
  min-height: 0;
  overflow-y: auto;
}

.conversation-item {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr);
  gap: 10px;
  width: 100%;
  padding: 12px;
  border: 1px solid transparent;
  border-radius: var(--border-radius);
  color: var(--text-secondary);
  text-align: left;
  transition: all var(--transition-fast);
}

.conversation-item:hover {
  background: var(--bg-hover);
}

.conversation-item.active {
  border-color: #b7d4ff;
  background: #f0f7ff;
  color: var(--text-primary);
}

.conversation-icon,
.context-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.conversation-icon {
  width: 36px;
  height: 36px;
  border-radius: var(--border-radius-sm);
  background: #edf6f5;
  color: #0f766e;
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
  color: inherit;
  font-weight: 650;
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
}

.chat-header {
  gap: 16px;
  padding: 20px 24px 16px;
  border-bottom: 1px solid var(--border-color-light);
}

.header-kicker {
  display: flex;
  gap: 6px;
  margin-bottom: 4px;
}

.chat-header h1 {
  font-size: 22px;
}

.header-actions {
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.context-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  padding: 14px 24px;
  border-bottom: 1px solid var(--border-color-light);
  background: #fbfcfd;
}

.context-card {
  gap: 10px;
  min-width: 0;
  padding: 10px;
  border: 1px solid var(--border-color-light);
  border-radius: var(--border-radius);
  background: #fff;
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
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  border-radius: var(--border-radius-sm);
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
  padding: 24px;
  background:
    linear-gradient(180deg, rgba(247, 250, 252, 0.88), rgba(255, 255, 255, 0.96)),
    radial-gradient(circle at top left, rgba(21, 94, 239, 0.06), transparent 28%);
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
  border-radius: 10px;
  background: #0f766e;
  color: #fff;
  font-weight: 700;
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
  border: 1px solid var(--border-color);
  border-radius: var(--border-radius);
  background: #fff;
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}

.inline-thinking {
  border-bottom: 1px solid var(--border-color-light);
  background: #fbfcfd;
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
  color: #155eef;
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
  box-shadow: 0 0 0 4px #fff5e6;
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
  background: #fbfcfd;
}

.composer {
  padding: 16px 24px 20px;
  border-top: 1px solid var(--border-color);
  background: #fff;
}

.composer-top {
  gap: 12px;
  margin-bottom: 8px;
  color: var(--text-tertiary);
  font-size: 12px;
}

.composer :deep(.el-textarea__inner) {
  border-radius: var(--border-radius);
  box-shadow: none;
  line-height: 1.6;
}

.follow-up-bar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  margin-top: 10px;
  padding: 10px;
  border: 1px solid #fedf89;
  border-radius: var(--border-radius);
  background: #fffbeb;
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
  padding: 5px 10px;
  border: 1px solid var(--border-color);
  border-radius: 999px;
  color: var(--text-secondary);
  font-size: 12px;
  transition: all var(--transition-fast);
}

.quick-prompts button:hover {
  border-color: #b7d4ff;
  background: #f0f7ff;
  color: #155eef;
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
  background: #eef3ff;
  color: #155eef;
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
  box-shadow: 0 0 0 4px #eafaf3;
}

.timeline-item.running .timeline-dot {
  background: #f79009;
  box-shadow: 0 0 0 4px #fff5e6;
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
  border: 1px solid var(--border-color);
  border-radius: var(--border-radius);
  background: #fff;
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
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}

.citation-card small {
  display: block;
  margin-top: 8px;
  color: var(--text-tertiary);
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
