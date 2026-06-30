import {
  API_QA,
  apiDelete,
  apiGet,
  apiPatch,
  apiPost,
  apiPut,
  getToken,
  getTokenType,
  replacePathParams,
  type ApiResponse,
  type ChatSseCitationData,
  type ChatSseDoneData,
  type ChatSseErrorData,
  type ChatSseMessageData,
  type ChatSseThinkingData,
  type ChatStreamRequest,
  type ChatTestRequest,
  type ChatTestResponse,
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

type QaStatsOverview = {
  totalCount?: number
  totalConversations?: number
  totalMessages?: number
  totalCitations?: number
  trend?: Array<{ date: string; count: number }>
}

export type ConversationView = Conversation & {
  description: string
  knowledgeBases: string[]
  citationCount: number
  owner: string
  status: 'active' | 'empty'
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
  trend?: Array<{ date: string; count: number }>
}

export type ChatStreamCallbacks = {
  signal?: AbortSignal
  onThinking?: (step: ThinkingStep) => void
  onMessage?: (data: ChatSseMessageData) => void
  onCitation?: (citations: Citation[]) => void
  onError?: (message: string) => void
  onDone?: (data: ChatSseDoneData) => void
}

type SseEnvelope = {
  event: 'thinking' | 'message' | 'citation' | 'done' | 'error'
  data: unknown
}

function unwrapApiData<T>(payload: ApiResponse<T> | T): T {
  if (
    payload &&
    typeof payload === 'object' &&
    'data' in payload &&
    ('code' in payload || 'message' in payload)
  ) {
    return (payload as ApiResponse<T>).data as T
  }
  return payload as T
}

function quoteUnsafeJsonNumbers(json: string) {
  return json
    .replace(/(:\s*)(-?\d{16,})(\s*[,}])/g, '$1"$2"$3')
    .replace(/([\[,]\s*)(-?\d{16,})(\s*[,\]])/g, '$1"$2"$3')
}

function parseJsonPreserveIds<T = unknown>(json: string): T {
  return JSON.parse(quoteUnsafeJsonNumbers(json)) as T
}

function safeJsonParseArray(value: unknown): unknown[] {
  if (Array.isArray(value)) return value
  if (!value || typeof value !== 'string') return []
  try {
    const parsed = parseJsonPreserveIds(value)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function asNumber(value: unknown, fallback = 0) {
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue : fallback
}

function asId(value: unknown, fallback = '') {
  if (value === null || value === undefined) return fallback
  return String(value)
}

function asOptionalNumber(value: unknown) {
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue : undefined
}

function pickString(record: Record<string, unknown>, keys: string[], fallback = '') {
  for (const key of keys) {
    const value = record[key]
    if (typeof value === 'string' && value.trim()) return value
  }
  return fallback
}

function formatDisplayTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value || '--'
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function buildConversationSummary(conversation: ConversationSchema) {
  if (!conversation.message_count) {
    return '空会话。发送第一条问题后，后端会自动用问题内容更新标题。'
  }
  return `共 ${conversation.message_count} 条消息，最近更新 ${formatDisplayTime(conversation.last_message_at)}`
}

function normalizeConversation(item: ConversationSchema | Record<string, unknown>): ConversationSchema {
  return {
    session_id: asId(item.session_id ?? item.sessionId),
    title: String(item.title ?? '新对话'),
    message_count: asNumber(item.message_count ?? item.messageCount),
    last_message_at: String(item.last_message_at ?? item.lastMessageAt ?? item.created_at ?? item.createdAt ?? ''),
    created_at: String(item.created_at ?? item.createdAt ?? ''),
  }
}

function normalizeMessage(item: MessageSchema | Record<string, unknown>): MessageSchema {
  return {
    message_id: asId(item.message_id ?? item.messageId),
    seq: asNumber(item.seq),
    role: (item.role as MessageSchema['role']) ?? 'assistant',
    content: String(item.content ?? ''),
    intent_type: (item.intent_type ?? item.intentType) as MessageSchema['intent_type'],
    thinking_steps: (item.thinking_steps ?? item.thinkingSteps) as string | null,
    citations: (item.citations ?? item.citation) as string | null,
    generate_status: asNumber(item.generate_status ?? item.generateStatus, 1) as MessageSchema['generate_status'],
    token_usage: (item.token_usage ?? item.tokenUsage) as MessageSchema['token_usage'],
    created_at: String(item.created_at ?? item.createdAt ?? new Date().toISOString()),
    updated_at: String(item.updated_at ?? item.updatedAt ?? item.created_at ?? item.createdAt ?? new Date().toISOString()),
  }
}

export function normalizeThinkingStep(raw: unknown): ThinkingStep {
  const item = typeof raw === 'object' && raw ? raw as Record<string, unknown> : { message: String(raw ?? '') }
  const phase = String(item.phase ?? '')
  const rawStatus = item.status
  const status =
    phase === 'start'
      ? 'running'
      : phase === 'error'
        ? 'done'
        : rawStatus === 'pending' || rawStatus === 'running' || rawStatus === 'done'
          ? rawStatus
          : 'done'
  const stepType = pickString(item, ['step_type', 'type', 'label'], 'thinking')
  const labelMap: Record<string, string> = {
    intent: '意图识别',
    clarify: '问题澄清',
    retrieve: '知识检索',
    rerank: '重排序',
    generate: '回答生成',
    citation: '引用整理',
    thinking: '思考过程',
  }
  const message = pickString(item, ['message', 'content'], '')

  return {
    label: labelMap[stepType] ?? stepType,
    content: message,
    step_type: stepType,
    message,
    elapsed_ms: typeof item.elapsed_ms === 'number' ? item.elapsed_ms : null,
    phase,
    status,
  }
}

export function normalizeCitation(raw: unknown): Citation {
  const item = typeof raw === 'object' && raw ? raw as Record<string, unknown> : {}
  const documentName = pickString(item, ['documentName', 'doc_name', 'docName'], '未知文档')
  const content = pickString(item, ['content', 'snippet', 'full_snippet', 'fullSnippet'], '')
  const score = asNumber(item.score ?? item.similarity, 0)

  return {
    index: asOptionalNumber(item.index),
    indices: Array.isArray(item.indices) ? item.indices.map((value) => asNumber(value)).filter(Boolean) : undefined,
    documentId: asOptionalNumber(item.documentId ?? item.doc_id ?? item.docId),
    docId: (item.doc_id ?? item.docId) as string | number | undefined,
    documentName,
    docName: documentName,
    content,
    snippet: content,
    fullSnippet: pickString(item, ['full_snippet', 'fullSnippet'], content),
    chapterPath: pickString(item, ['chapter_path', 'chapterPath'], ''),
    score,
    source: pickString(item, ['source', 'chapter_path', 'chapterPath', 'chunk_type', 'chunkType'], '知识库'),
    chunkType: pickString(item, ['chunk_type', 'chunkType'], ''),
  }
}

function toConversation(item: ConversationSchema | Record<string, unknown>): ConversationView {
  const conversation = normalizeConversation(item)
  const summary = buildConversationSummary(conversation)
  return {
    id: conversation.session_id,
    title: conversation.title,
    summary,
    description: summary,
    messageCount: conversation.message_count,
    citationCount: 0,
    lastMessageAt: conversation.last_message_at,
    updatedAt: conversation.last_message_at ? formatDisplayTime(conversation.last_message_at) : '--',
    createdAt: conversation.created_at,
    knowledgeBases: [],
    owner: '当前用户',
    status: conversation.message_count > 0 ? 'active' : 'empty',
    tag: conversation.message_count > 0 ? '已有问答' : '待提问',
  }
}

function toMessage(item: MessageSchema | Record<string, unknown>, conversationId: string): ChatMessageView | null {
  const message = normalizeMessage(item)
  if (message.role === 'system') return null
  return {
    id: message.message_id,
    conversationId,
    seq: message.seq,
    role: message.role,
    content: message.content,
    intentType: message.intent_type,
    generateStatus: message.generate_status,
    citations: safeJsonParseArray(message.citations).map(normalizeCitation),
    thinkingSteps: safeJsonParseArray(message.thinking_steps).map(normalizeThinkingStep),
    createdAt: message.created_at,
    time: formatDisplayTime(message.created_at),
    thinkingCollapsed: true,
  }
}

function normalizeConversationList(result: ConversationListResult | Record<string, unknown>) {
  const items = Array.isArray(result.items) ? result.items : []
  return {
    items: items.map((item) => toConversation(item as Record<string, unknown>)),
    total: asNumber(result.total, items.length),
    page: asNumber(result.page, 1),
    size: asNumber(result.size, items.length),
  }
}

export async function listConversations(params: ListConversationParams = {}) {
  const res = await apiGet<ApiResponse<ConversationListResult>>(API_QA.CHAT.LIST, {
    page: params.page ?? 1,
    size: params.size ?? 20,
    user_id: params.user_id,
  })
  return normalizeConversationList(unwrapApiData(res.data))
}

export async function createConversation(title = '新对话', userId = 1) {
  const res = await apiPost<ApiResponse<ConversationSchema>>(
    API_QA.CHAT.CREATE,
    { title },
    { params: { user_id: userId } },
  )
  return toConversation(unwrapApiData(res.data))
}

export async function updateConversationTitle(id: string, title: string) {
  const url = replacePathParams(API_QA.CHAT.UPDATE_TITLE, { id })
  const res = await apiPatch<ApiResponse<ConversationSchema>>(url, { title })
  return toConversation(unwrapApiData(res.data))
}

export async function deleteConversation(id: string) {
  const url = replacePathParams(API_QA.CHAT.DELETE, { id })
  await apiDelete<ApiResponse<null>>(url)
}

export async function listMessages(conversationId: string, page = 1, size = 50) {
  const url = replacePathParams(API_QA.CHAT.HISTORY, { id: conversationId })
  const res = await apiGet<ApiResponse<ConversationMessagesResult>>(url, { page, size })
  const data = unwrapApiData(res.data)
  return {
    ...data,
    messages: data.messages
      .map((message) => toMessage(message, conversationId))
      .filter((message): message is ChatMessageView => Boolean(message)),
  }
}

export async function askChatOnce(payload: ChatTestRequest) {
  const res = await apiPost<ApiResponse<ChatTestResponse> | ChatTestResponse>(API_QA.CHAT.TEST, payload)
  return unwrapApiData(res.data)
}

function getApiBase() {
  return import.meta.env.VITE_API_BASE || ''
}

function buildHeaders() {
  const headers: Record<string, string> = {
    Accept: 'text/event-stream',
    'Content-Type': 'application/json',
  }
  const token = getToken()
  if (token) {
    headers.Authorization = `${getTokenType()} ${token}`
  }
  return headers
}

function parseSseBlock(block: string): SseEnvelope | null {
  const lines = block.split(/\r?\n/)
  let event: SseEnvelope['event'] = 'message'
  const dataLines: string[] = []

  for (const line of lines) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim() as SseEnvelope['event']
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart())
    }
  }

  if (!dataLines.length) return null

  try {
    return {
      event,
      data: parseJsonPreserveIds(dataLines.join('\n')),
    }
  } catch {
    return null
  }
}

async function dispatchSseBlock(block: string, callbacks: ChatStreamCallbacks) {
  const parsed = parseSseBlock(block)
  if (!parsed) return

  if (parsed.event === 'thinking') {
    callbacks.onThinking?.(normalizeThinkingStep(parsed.data as ChatSseThinkingData))
    return
  }

  if (parsed.event === 'message') {
    callbacks.onMessage?.(parsed.data as ChatSseMessageData)
    return
  }

  if (parsed.event === 'citation') {
    const data = parsed.data as ChatSseCitationData
    callbacks.onCitation?.((data.citations ?? []).map(normalizeCitation))
    return
  }

  if (parsed.event === 'error') {
    callbacks.onError?.((parsed.data as ChatSseErrorData).message || '问答服务返回错误')
    return
  }

  if (parsed.event === 'done') {
    callbacks.onDone?.(parsed.data as ChatSseDoneData)
  }
}

export async function streamChatMessage(
  payload: ChatStreamRequest,
  callbacks: ChatStreamCallbacks = {},
): Promise<ChatSseDoneData | null> {
  let doneData: ChatSseDoneData | null = null
  const streamCallbacks: ChatStreamCallbacks = {
    ...callbacks,
    onDone(data) {
      doneData = data
      callbacks.onDone?.(data)
    },
  }

  const response = await fetch(`${getApiBase()}${API_QA.CHAT.STREAM}`, {
    method: 'POST',
    headers: buildHeaders(),
    body: JSON.stringify(payload),
    signal: callbacks.signal,
  })

  if (!response.ok) {
    throw new Error(`问答服务请求失败：${response.status}`)
  }
  if (!response.body) {
    throw new Error('当前浏览器不支持流式响应。')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  try {
    while (true) {
      const { value, done } = await reader.read()
      buffer += decoder.decode(value ?? new Uint8Array(), { stream: !done })

      const blocks = buffer.split(/\r?\n\r?\n/)
      buffer = blocks.pop() ?? ''
      for (const block of blocks) {
        await dispatchSseBlock(block, streamCallbacks)
      }

      if (done) break
    }

    if (buffer.trim()) {
      await dispatchSseBlock(buffer, streamCallbacks)
    }
  } finally {
    reader.releaseLock()
  }

  return doneData
}

export async function sendChatMessage(payload: ChatStreamRequest, callbacks: ChatStreamCallbacks = {}) {
  return streamChatMessage(payload, callbacks)
}

export async function listModelConfigs() {
  const res = await apiGet<ApiResponse<ModelConfigVO[]>>(API_QA.MODEL_CONFIG.LIST)
  return unwrapApiData(res.data)
}

export async function createModelConfig(payload: ModelConfigPayload) {
  const res = await apiPost<ApiResponse<ModelConfigVO>>(API_QA.MODEL_CONFIG.CREATE, payload)
  return unwrapApiData(res.data)
}

export async function updateModelConfig(id: number, payload: ModelConfigPayload) {
  const url = replacePathParams(API_QA.MODEL_CONFIG.UPDATE, { id })
  const res = await apiPut<ApiResponse<ModelConfigVO>>(url, payload)
  return unwrapApiData(res.data)
}

export async function deleteModelConfig(id: number) {
  const url = replacePathParams(API_QA.MODEL_CONFIG.DELETE, { id })
  await apiDelete<ApiResponse<null>>(url)
}

export async function setDefaultModelConfig(id: number) {
  const url = replacePathParams(API_QA.MODEL_CONFIG.SET_DEFAULT, { id })
  await apiPost<ApiResponse<null>>(url)
}

export async function runRetrievalTest(question: string, selectedKbIds: number[] = []) {
  const res = await apiPost<ApiResponse<{ results: Citation[]; total: number }>>(API_QA.SEARCH.RETRIEVE, {
    question,
    selected_kb_ids: selectedKbIds,
    top_k: 5,
  })
  return unwrapApiData(res.data)
}

export async function getQaStats(): Promise<QaStats> {
  const res = await apiGet<ApiResponse<QaStatsOverview>>(API_QA.ADMIN.STATS)
  const data = unwrapApiData(res.data) ?? {}
  return {
    totalConversations: asNumber(data.totalConversations),
    totalMessages: asNumber(data.totalMessages ?? data.totalCount),
    totalCitations: asNumber(data.totalCitations),
    trend: data.trend,
  }
}
