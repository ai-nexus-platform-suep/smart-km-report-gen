import {
  API_QA,
  apiDelete,
  apiGet,
  apiPatch,
  apiPost,
  apiPut,
  replacePathParams,
  type ApiResponse,
  type ChatTestRequest,
  type ChatTestResponse,
  type ChatStreamRequest,
  type Citation,
  type Conversation,
  type ConversationListResult,
  type ConversationMessagesResult,
  type ConversationSchema,
  type Message,
  type MessageSchema,
  type ModelConfigPayload,
  type ModelConfigVO,
  type ThinkingStep,
} from '@platform/core'

type ListConversationParams = {
  page?: number
  size?: number
  user_id?: number
}

export type ConversationView = Conversation & {
  description: string
  knowledgeBases: string[]
  citationCount: number
  owner: string
  status: 'active' | 'archived'
  updatedAt: string
}

export type ChatMessageView = Message & {
  time: string
  thinkingCollapsed?: boolean
  streaming?: boolean
  interrupted?: boolean
}

export type QaStats = {
  totalConversations: number
  totalMessages: number
  totalCitations: number
}

function safeJsonParse<T>(value: string | null | undefined, fallback: T): T {
  if (!value) return fallback
  try {
    return JSON.parse(value) as T
  } catch {
    return fallback
  }
}

function formatDisplayTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function inferSummary(title: string) {
  if (title.includes('锅炉')) return '工作票、隔离措施、危险点预控等安全规程问答'
  if (title.includes('电气') || title.includes('试验')) return '试验数据、报告结论、监督口径解释'
  if (title.includes('煤') || title.includes('库存')) return '经营监督、盘点口径和佐证材料分析'
  return '技术监督知识问答与依据追溯'
}

function inferKnowledgeBases(title: string) {
  if (title.includes('锅炉')) return ['安全规程']
  if (title.includes('电气') || title.includes('试验')) return ['电气设备', '报告规范']
  if (title.includes('煤') || title.includes('库存')) return ['经营监督']
  return ['设备检修', '技术监督']
}

function toConversation(item: ConversationSchema): ConversationView {
  const knowledgeBases = inferKnowledgeBases(item.title)
  return {
    id: item.session_id,
    title: item.title,
    summary: inferSummary(item.title),
    description: inferSummary(item.title),
    messageCount: item.message_count,
    citationCount: Math.max(1, Math.round(item.message_count / 3)),
    lastMessageAt: item.last_message_at,
    updatedAt: formatDisplayTime(item.last_message_at),
    createdAt: item.created_at,
    knowledgeBases,
    owner: '当前用户',
    status: item.message_count > 0 ? 'active' : 'archived',
    tag: knowledgeBases[0],
  }
}

function toMessage(item: MessageSchema, conversationId: number): ChatMessageView | null {
  if (item.role === 'system') return null
  return {
    id: item.message_id,
    conversationId,
    seq: item.seq,
    role: item.role,
    content: item.content,
    intentType: item.intent_type,
    generateStatus: item.generate_status,
    citations: safeJsonParse<Citation[]>(item.citations, []),
    thinkingSteps: safeJsonParse<ThinkingStep[]>(item.thinking_steps, []),
    createdAt: item.created_at,
    time: formatDisplayTime(item.created_at),
    thinkingCollapsed: true,
  }
}

export async function listConversations(params: ListConversationParams = {}) {
  const res = await apiGet<ApiResponse<ConversationListResult>>(API_QA.CHAT.LIST, {
    page: params.page ?? 1,
    size: params.size ?? 20,
    user_id: params.user_id,
  })
  return {
    ...res.data.data,
    items: res.data.data.items.map(toConversation),
  }
}

export async function createConversation(title = '新对话', userId = 1) {
  const res = await apiPost<ApiResponse<ConversationSchema>>(API_QA.CHAT.CREATE, {
    title,
  }, {
    params: { user_id: userId },
  })
  return toConversation(res.data.data)
}

export async function updateConversationTitle(id: number, title: string) {
  const url = replacePathParams(API_QA.CHAT.UPDATE_TITLE, { id })
  const res = await apiPatch<ApiResponse<ConversationSchema>>(url, { title })
  return toConversation(res.data.data)
}

export async function deleteConversation(id: number) {
  const url = replacePathParams(API_QA.CHAT.DELETE, { id })
  await apiDelete<ApiResponse<null>>(url)
}

export async function listMessages(conversationId: number, page = 1, size = 50) {
  const url = replacePathParams(API_QA.CHAT.HISTORY, { id: conversationId })
  const res = await apiGet<ApiResponse<ConversationMessagesResult>>(url, { page, size })
  return {
    ...res.data.data,
    messages: res.data.data.messages
      .map((message) => toMessage(message, conversationId))
      .filter((message): message is ChatMessageView => Boolean(message)),
  }
}

export async function askChatOnce(payload: ChatTestRequest) {
  const res = await apiPost<ChatTestResponse>(API_QA.CHAT.TEST, payload)
  return res.data
}

export async function sendChatMessage(payload: ChatStreamRequest) {
  const res = await apiPost<ChatTestResponse>(API_QA.CHAT.STREAM, payload)
  return res.data
}

export async function listModelConfigs() {
  const res = await apiGet<ApiResponse<ModelConfigVO[]>>(API_QA.MODEL_CONFIG.LIST)
  return res.data.data
}

export async function createModelConfig(payload: ModelConfigPayload) {
  const res = await apiPost<ApiResponse<ModelConfigVO>>(API_QA.MODEL_CONFIG.CREATE, payload)
  return res.data.data
}

export async function updateModelConfig(id: number, payload: ModelConfigPayload) {
  const url = replacePathParams(API_QA.MODEL_CONFIG.UPDATE, { id })
  const res = await apiPut<ApiResponse<ModelConfigVO>>(url, payload)
  return res.data.data
}

export async function deleteModelConfig(id: number) {
  const url = replacePathParams(API_QA.MODEL_CONFIG.DELETE, { id })
  await apiDelete<ApiResponse<null>>(url)
}

export async function setDefaultModelConfig(id: number) {
  const url = replacePathParams(API_QA.MODEL_CONFIG.SET_DEFAULT, { id })
  await apiPost<ApiResponse<null>>(url)
}

export async function runRetrievalTest(question: string) {
  const res = await apiPost<ApiResponse<{ results: Citation[]; total: number }>>(API_QA.SEARCH.RETRIEVE, {
    question,
    selected_kb_ids: [1, 2],
    top_k: 5,
  })
  return res.data.data
}

export async function getQaStats(): Promise<QaStats> {
  const res = await apiGet<ApiResponse<Partial<QaStats>>>(API_QA.ADMIN.STATS)
  return {
    totalConversations: res.data.data.totalConversations ?? 0,
    totalMessages: res.data.data.totalMessages ?? 0,
    totalCitations: res.data.data.totalCitations ?? 0,
  }
}
