import { http, HttpResponse, delay } from 'msw'
import { API_QA } from '@platform/core'
import type {
  ChatTestRequest,
  ChatTestResponse,
  ConversationSchema,
  MessageSchema,
  ModelConfigPayload,
  ModelConfigVO,
  ThinkingStep,
} from '@platform/core/types'

const now = () => new Date().toISOString()

let nextConversationId = 5
let nextMessageId = 9
let nextModelId = 3

const mockThinkingSteps: ThinkingStep[] = [
  { label: '意图识别', content: '识别为技术监督知识问答，聚焦设备检修周期和规程依据。', status: 'done' },
  { label: '知识检索', content: '从设备检修、技术监督知识库召回 2 条高相关片段。', status: 'done' },
  { label: '回答组织', content: '按依据、判断条件、处置建议组织结论。', status: 'done' },
]

const mockCitations = [
  {
    documentId: 1,
    documentName: '汽轮机检修规程_v3.0.pdf',
    content: '汽轮机大修周期一般为 4-6 年，需结合运行小时数、启停次数和设备健康状态综合确定。',
    score: 0.92,
    source: '知识库 / 设备检修',
  },
  {
    documentId: 2,
    documentName: '发电设备技术监督导则.docx',
    content: '关键指标持续异常时，应组织状态评估、专项诊断和检修论证。',
    score: 0.86,
    source: '知识库 / 技术监督',
  },
]

const conversations: ConversationSchema[] = [
  {
    session_id: 1,
    title: '汽轮机检修周期判断',
    message_count: 2,
    last_message_at: now(),
    created_at: now(),
  },
  {
    session_id: 2,
    title: '锅炉安全规范相关问题',
    message_count: 0,
    last_message_at: now(),
    created_at: now(),
  },
  {
    session_id: 3,
    title: '电气设备试验报告解释',
    message_count: 0,
    last_message_at: now(),
    created_at: now(),
  },
  {
    session_id: 4,
    title: '煤库存审计口径确认',
    message_count: 0,
    last_message_at: now(),
    created_at: now(),
  },
]

const messagesByConversation = new Map<number, MessageSchema[]>([
  [
    1,
    [
      {
        message_id: 1,
        seq: 1,
        role: 'user',
        content: '汽轮机的大修周期一般是多久？如果运行状态良好，可以延期吗？',
        intent_type: 'KNOWLEDGE_QA',
        generate_status: 1,
        created_at: now(),
        updated_at: now(),
      },
      {
        message_id: 2,
        seq: 2,
        role: 'assistant',
        content:
          '汽轮机大修周期通常不是机械固定值。规程可按 4-6 年作为参考，但最终应结合运行小时数、启停次数、振动趋势、油质指标、效率变化和缺陷闭环情况综合判断。若状态稳定、试验结论正常且缺陷闭环充分，可以组织状态评估并形成延期论证；若关键指标持续异常，应优先安排专项诊断或提前检修。',
        intent_type: 'KNOWLEDGE_QA',
        thinking_steps: JSON.stringify(mockThinkingSteps),
        citations: JSON.stringify(mockCitations),
        generate_status: 1,
        token_usage: { prompt_tokens: 328, completion_tokens: 196 },
        created_at: now(),
        updated_at: now(),
      },
    ],
  ],
])

let modelConfigs: ModelConfigVO[] = [
  {
    id: 1,
    userId: 1,
    provider: 'deepseek',
    baseUrl: 'https://api.deepseek.com',
    modelName: 'deepseek-chat',
    apiKeyMasked: 'sk-****demo',
    scenario: 'chat',
    enabled: 1,
    isDefault: 1,
    createdAt: now(),
    updatedAt: now(),
  },
  {
    id: 2,
    userId: 1,
    provider: 'qwen',
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    modelName: 'qwen-plus',
    apiKeyMasked: 'sk-****qwen',
    scenario: 'chat',
    enabled: 1,
    isDefault: 0,
    createdAt: now(),
    updatedAt: now(),
  },
]

function ok<T>(data: T, message = 'ok') {
  return HttpResponse.json({ code: 200, message, data })
}

function findConversation(id: number) {
  return conversations.find((item) => item.session_id === id)
}

function buildAnswer(question: string) {
  return `针对“${question}”，建议不要把技术监督结论建立在单一固定阈值上。应先确认适用规程和设备对象，再结合运行小时数、启停次数、缺陷记录、试验数据和检修历史进行综合判断。若监督指标稳定且缺陷闭环充分，可以形成状态评估意见；若关键指标持续异常，应优先安排专项诊断、风险评估或提前检修。`
}

function appendChatMessages(conversationId: number, question: string, answer: string) {
  const list = messagesByConversation.get(conversationId) ?? []
  const timestamp = now()
  const userMessage: MessageSchema = {
    message_id: nextMessageId++,
    seq: list.length + 1,
    role: 'user',
    content: question,
    intent_type: 'KNOWLEDGE_QA',
    generate_status: 1,
    created_at: timestamp,
    updated_at: timestamp,
  }
  const assistantMessage: MessageSchema = {
    message_id: nextMessageId++,
    seq: list.length + 2,
    role: 'assistant',
    content: answer,
    intent_type: 'KNOWLEDGE_QA',
    thinking_steps: JSON.stringify(mockThinkingSteps),
    citations: JSON.stringify(mockCitations),
    generate_status: 1,
    token_usage: { prompt_tokens: 320, completion_tokens: 180 },
    created_at: timestamp,
    updated_at: timestamp,
  }

  messagesByConversation.set(conversationId, [...list, userMessage, assistantMessage])

  const conversation = findConversation(conversationId)
  if (conversation) {
    conversation.message_count += 2
    conversation.last_message_at = timestamp
  }
}

export const qaHandlers = [
  http.get(API_QA.CHAT.LIST, async ({ request }) => {
    await delay(260)
    const url = new URL(request.url)
    const page = Number(url.searchParams.get('page') ?? 1)
    const size = Number(url.searchParams.get('size') ?? 20)
    const start = (page - 1) * size
    const items = conversations.slice(start, start + size)

    return ok({
      items,
      total: conversations.length,
      page,
      size,
    })
  }),

  http.post(API_QA.CHAT.CREATE, async ({ request }) => {
    await delay(260)
    const body = (await request.json().catch(() => ({}))) as { title?: string }
    const title = body.title?.trim() || '新对话'
    const conversation: ConversationSchema = {
      session_id: nextConversationId++,
      title,
      message_count: 0,
      last_message_at: now(),
      created_at: now(),
    }

    conversations.unshift(conversation)
    messagesByConversation.set(conversation.session_id, [])
    return ok(conversation, '创建成功')
  }),

  http.patch(API_QA.CHAT.UPDATE_TITLE, async ({ params, request }) => {
    await delay(220)
    const id = Number(params.id)
    const conversation = findConversation(id)
    if (!conversation) {
      return HttpResponse.json({ code: 404, message: '会话不存在', data: null }, { status: 404 })
    }

    const body = (await request.json()) as { title: string }
    conversation.title = body.title?.trim() || conversation.title
    conversation.last_message_at = now()
    return ok(conversation, '修改成功')
  }),

  http.delete(API_QA.CHAT.DELETE, async ({ params }) => {
    await delay(220)
    const id = Number(params.id)
    const index = conversations.findIndex((item) => item.session_id === id)
    if (index < 0) {
      return HttpResponse.json({ code: 404, message: '会话不存在', data: null }, { status: 404 })
    }

    conversations.splice(index, 1)
    messagesByConversation.delete(id)
    return ok(null, '删除成功')
  }),

  http.get(API_QA.CHAT.HISTORY, async ({ params, request }) => {
    await delay(260)
    const id = Number(params.id)
    const conversation = findConversation(id)
    if (!conversation) {
      return HttpResponse.json({ code: 404, message: '会话不存在', data: null }, { status: 404 })
    }

    const url = new URL(request.url)
    const page = Number(url.searchParams.get('page') ?? 1)
    const size = Number(url.searchParams.get('size') ?? 50)
    const allMessages = messagesByConversation.get(id) ?? []
    const start = (page - 1) * size

    return ok({
      session_id: id,
      title: conversation.title,
      total: allMessages.length,
      messages: allMessages.slice(start, start + size),
    })
  }),

  http.post(API_QA.CHAT.TEST, async ({ request }) => {
    await delay(520)
    const body = (await request.json()) as ChatTestRequest
    const question = body.question?.trim() || '请说明技术监督判断依据'
    const response: ChatTestResponse = {
      intent: 'KNOWLEDGE_QA',
      mode: 'mock',
      needs_clarification: false,
      retrieved_docs_count: mockCitations.length,
      thinking_steps: mockThinkingSteps,
      citations: mockCitations,
      final_response: buildAnswer(question),
    }

    return HttpResponse.json(response)
  }),

  http.post(API_QA.CHAT.STREAM, async ({ request }) => {
    await delay(520)
    const body = (await request.json()) as {
      conversation_id: number
      question: string
      selected_kb_ids?: number[]
      user_id?: number | null
    }
    const answer = buildAnswer(body.question)
    appendChatMessages(body.conversation_id, body.question, answer)

    return HttpResponse.json({
      intent: 'KNOWLEDGE_QA',
      type: 'done',
      thinking_steps: mockThinkingSteps,
      citations: mockCitations,
      final_response: answer,
    })
  }),

  http.post(API_QA.SEARCH.RETRIEVE, async () => {
    await delay(360)
    return ok({
      results: mockCitations,
      total: mockCitations.length,
    })
  }),

  http.get(API_QA.ADMIN.STATS, async () => {
    await delay(220)
    const totalMessages = conversations.reduce((sum, item) => sum + item.message_count, 0)
    return ok({
      totalConversations: conversations.length,
      totalMessages,
      totalCitations: totalMessages === 0 ? 0 : Math.max(2, Math.round(totalMessages / 2)),
    })
  }),

  http.get(API_QA.ADMIN.QA_TREND, async () => {
    await delay(220)
    return ok(
      Array.from({ length: 14 }, (_, i) => ({
        date: `2026-06-${String(16 + i).padStart(2, '0')}`,
        count: Math.floor(8 + Math.random() * 22),
      })),
    )
  }),

  http.get(API_QA.MODEL_CONFIG.LIST, async () => {
    await delay(260)
    return ok(modelConfigs)
  }),

  http.post(API_QA.MODEL_CONFIG.CREATE, async ({ request }) => {
    await delay(320)
    const body = (await request.json()) as ModelConfigPayload
    const scenario = body.scenario ?? 'chat'
    const isFirstInScenario = !modelConfigs.some((item) => item.scenario === scenario)
    const config: ModelConfigVO = {
      id: nextModelId++,
      userId: 1,
      provider: body.provider,
      baseUrl: body.baseUrl,
      modelName: body.modelName,
      apiKeyMasked: `${body.apiKey.slice(0, 3)}****${body.apiKey.slice(-4)}`,
      scenario,
      enabled: 1,
      isDefault: isFirstInScenario ? 1 : 0,
      createdAt: now(),
      updatedAt: now(),
    }

    modelConfigs = [config, ...modelConfigs]
    return ok(config, '创建成功')
  }),

  http.put(API_QA.MODEL_CONFIG.UPDATE, async ({ params, request }) => {
    await delay(320)
    const id = Number(params.id)
    const body = (await request.json()) as ModelConfigPayload
    const index = modelConfigs.findIndex((item) => item.id === id)
    if (index < 0) {
      return HttpResponse.json({ code: 404, message: '模型配置不存在', data: null }, { status: 404 })
    }

    modelConfigs[index] = {
      ...modelConfigs[index],
      provider: body.provider,
      baseUrl: body.baseUrl,
      modelName: body.modelName,
      apiKeyMasked: body.apiKey ? `${body.apiKey.slice(0, 3)}****${body.apiKey.slice(-4)}` : modelConfigs[index].apiKeyMasked,
      scenario: body.scenario ?? modelConfigs[index].scenario,
      updatedAt: now(),
    }

    return ok(modelConfigs[index], '修改成功')
  }),

  http.delete(API_QA.MODEL_CONFIG.DELETE, async ({ params }) => {
    await delay(220)
    const id = Number(params.id)
    modelConfigs = modelConfigs.filter((item) => item.id !== id)
    return ok(null, '删除成功')
  }),

  http.post(API_QA.MODEL_CONFIG.SET_DEFAULT, async ({ params }) => {
    await delay(220)
    const id = Number(params.id)
    const target = modelConfigs.find((item) => item.id === id)
    if (!target) {
      return HttpResponse.json({ code: 404, message: '模型配置不存在', data: null }, { status: 404 })
    }

    modelConfigs = modelConfigs.map((item) => ({
      ...item,
      isDefault: item.scenario === target.scenario ? (item.id === id ? 1 : 0) : item.isDefault,
      updatedAt: item.id === id ? now() : item.updatedAt,
    }))

    return ok(null, '设置成功')
  }),
]
