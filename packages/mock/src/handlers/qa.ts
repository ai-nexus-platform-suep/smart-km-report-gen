import { http, HttpResponse, delay } from 'msw'
import { API_QA } from '@platform/core'
import type { Conversation, Message } from '@platform/core/types'

const now = new Date().toISOString()

const mockConversations: Conversation[] = [
  { id: 1, title: '关于汽轮机检修周期的咨询', createdAt: now, updatedAt: now },
  { id: 2, title: '锅炉安全规范相关问题', createdAt: now, updatedAt: now },
  { id: 3, title: '电力行业术语解释', createdAt: now, updatedAt: now },
]

const mockMessages: Record<number, Message[]> = {
  1: [
    { id: 1, conversationId: 1, role: 'user', content: '汽轮机的大修周期一般是多久？', createdAt: now },
    {
      id: 2,
      conversationId: 1,
      role: 'assistant',
      content: '根据《汽轮机检修规程》，汽轮机大修周期一般为4-6年，具体周期需根据运行小时数、启停次数和设备状况综合确定。小修周期为1-2年。',
      citations: [
        {
          documentId: 1,
          documentName: '汽轮机检修规程_v3.0.pdf',
          content: '汽轮机大修周期一般为4-6年...',
          score: 0.92,
        },
      ],
      thinkingSteps: [
        { label: '意图识别', content: '识别为知识问答类问题，主题：汽轮机检修周期', status: 'done' },
        { label: '知识检索', content: '检索到2条相关文档片段', status: 'done' },
        { label: '生成回答', content: '正在基于检索结果生成回答...', status: 'done' },
      ],
      createdAt: now,
    },
  ],
}

export const qaHandlers = [
  // 会话列表
  http.get(API_QA.CHAT.LIST, async () => {
    await delay(400)
    return HttpResponse.json({
      code: 200, message: 'ok',
      data: { records: mockConversations, total: mockConversations.length, page: 1, pageSize: 20 },
    })
  }),

  // 创建会话
  http.post(API_QA.CHAT.CREATE, async () => {
    await delay(400)
    return HttpResponse.json({
      code: 200, message: '创建成功',
      data: { id: Date.now(), title: '新对话', createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
    })
  }),

  // 删除会话
  http.delete(API_QA.CHAT.DELETE, async () => {
    await delay(300)
    return HttpResponse.json({ code: 200, message: '删除成功', data: null })
  }),

  // 对话历史
  http.get(API_QA.CHAT.HISTORY, async ({ params }) => {
    await delay(400)
    const id = Number(params.id)
    return HttpResponse.json({ code: 200, message: 'ok', data: mockMessages[id] || [] })
  }),

  // 对话检索
  http.post(API_QA.SEARCH.RETRIEVE, async () => {
    await delay(500)
    return HttpResponse.json({
      code: 200, message: 'ok',
      data: {
        results: [
          { documentId: 1, documentName: '汽轮机检修规程_v3.0.pdf', content: '汽轮机大修周期一般为4-6年...', score: 0.92 },
          { documentId: 2, documentName: '锅炉检修安全规范.docx', content: '锅炉检修前必须办理工作票...', score: 0.85 },
        ],
        total: 2,
      },
    })
  }),

  // Admin: 问答配置
  http.get(API_QA.ADMIN.QA_CONFIG, async () => {
    await delay(300)
    return HttpResponse.json({
      code: 200, message: 'ok',
      data: { topK: 5, similarityThreshold: 0.7, rerankThreshold: 0.5, selectedKbIds: [1, 2] },
    })
  }),

  http.put(API_QA.ADMIN.QA_CONFIG, async () => {
    await delay(500)
    return HttpResponse.json({ code: 200, message: '保存成功', data: null })
  }),

  // Admin: LLM 配置
  http.get(API_QA.ADMIN.LLM_CONFIG, async () => {
    await delay(300)
    return HttpResponse.json({
      code: 200, message: 'ok',
      data: { apiBase: 'https://api.openai.com/v1', apiKey: 'sk-****', modelName: 'gpt-4o', timeout: 60000 },
    })
  }),

  http.put(API_QA.ADMIN.LLM_CONFIG, async () => {
    await delay(500)
    return HttpResponse.json({ code: 200, message: '保存成功', data: null })
  }),

  // Admin: 检索测试
  http.post(API_QA.ADMIN.RETRIEVAL_TEST, async () => {
    await delay(600)
    return HttpResponse.json({
      code: 200, message: 'ok',
      data: {
        results: [
          { documentId: 1, documentName: '汽轮机检修规程_v3.0.pdf', content: '汽轮机大修周期一般为4-6年...', score: 0.92 },
          { documentId: 2, documentName: '锅炉检修安全规范.docx', content: '锅炉检修前必须办理工作票...', score: 0.85 },
          { documentId: 1, documentName: '汽轮机检修规程_v3.0.pdf', content: '汽轮机润滑油系统应定期取样化验...', score: 0.78 },
        ],
        total: 3,
      },
    })
  }),

  // Admin: 统计数据
  http.get(API_QA.ADMIN.STATS, async () => {
    await delay(300)
    return HttpResponse.json({ code: 200, message: 'ok', data: { totalConversations: 128, totalMessages: 1024 } })
  }),

  // Admin: 趋势数据
  http.get(API_QA.ADMIN.QA_TREND, async () => {
    await delay(300)
    return HttpResponse.json({
      code: 200, message: 'ok',
      data: Array.from({ length: 30 }, (_, i) => ({
        date: `2025-06-${String(i + 1).padStart(2, '0')}`,
        count: Math.floor(Math.random() * 20),
      })),
    })
  }),
]
