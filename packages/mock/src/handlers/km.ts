import { http, HttpResponse, delay } from 'msw'
import { API_KM } from '@platform/core'
import type { KnowledgeBase, Document, SearchResultItem } from '@platform/core/types'

const now = new Date().toISOString()

const mockKnowledgeBases: KnowledgeBase[] = [
  {
    id: 1, name: '汽轮机检修规程', description: '火力发电厂汽轮机设备检修标准规范',
    type: 'REGULATION', searchMode: 'VECTOR_RERANK', documentCount: 12, creator: '管理员', createdAt: now,
  },
  {
    id: 2, name: '电气设备技术报告', description: '历年电气设备运行与维护技术报告汇总',
    type: 'REPORT', searchMode: 'VECTOR', documentCount: 8, creator: '管理员', createdAt: now,
  },
  {
    id: 3, name: '电力行业术语库', description: '电力行业标准术语与定义',
    type: 'TERM', searchMode: 'VECTOR', documentCount: 156, creator: '管理员', createdAt: now,
  },
  {
    id: 4, name: '通用技术文档', description: '各类技术参考资料汇总',
    type: 'GENERAL', searchMode: 'VECTOR_RERANK', documentCount: 45, creator: '用户', createdAt: now,
  },
]

const mockDocuments: Document[] = [
  { id: 1, kbId: 1, fileName: '汽轮机检修规程_v3.0.pdf', fileType: 'pdf', fileSize: 2450000, status: 'READY', tags: ['汽轮机', '检修', '规程'], createdAt: now },
  { id: 2, kbId: 1, fileName: '锅炉检修安全规范.docx', fileType: 'docx', fileSize: 1800000, status: 'READY', tags: ['锅炉', '安全'], createdAt: now },
  { id: 3, kbId: 2, fileName: '2024年度电气设备运行报告.xlsx', fileType: 'xlsx', fileSize: 3200000, status: 'EMBEDDING', tags: ['电气', '年度报告'], createdAt: now },
  { id: 4, kbId: 1, fileName: '汽机检修记录_2025Q1.pdf', fileType: 'pdf', fileSize: 5200000, status: 'PARSING', tags: ['汽轮机', '检修记录'], createdAt: now },
  { id: 5, kbId: 3, fileName: '电力术语词典.xlsx', fileType: 'xlsx', fileSize: 890000, status: 'READY', tags: ['术语', '词典'], createdAt: now },
]

const mockSearchResults: SearchResultItem[] = [
  { documentId: 1, documentName: '汽轮机检修规程_v3.0.pdf', content: '汽轮机大修周期一般为4-6年，小修周期为1-2年。大修内容包括：汽缸揭缸检查、转子检查、叶片无损检测、轴承翻修等。', score: 0.92 },
  { documentId: 2, documentName: '锅炉检修安全规范.docx', content: '锅炉检修前必须办理工作票，做好安全措施。进入炉膛前需进行气体检测，确保氧气含量在19.5%-23.5%之间。', score: 0.85 },
  { documentId: 1, documentName: '汽轮机检修规程_v3.0.pdf', content: '汽轮机润滑油系统应定期取样化验，油质不合格时应及时更换。轴承温度报警值为85℃，跳机值为95℃。', score: 0.78 },
]

export const kmHandlers = [
  // === 知识库 CRUD ===
  http.get(API_KM.KB.LIST, async () => {
    await delay(400)
    return HttpResponse.json({
      code: 200, message: 'ok',
      data: { records: mockKnowledgeBases, total: mockKnowledgeBases.length, page: 1, pageSize: 10 },
    })
  }),

  http.post(API_KM.KB.CREATE, async () => {
    await delay(500)
    return HttpResponse.json({ code: 200, message: '创建成功', data: { id: Date.now() } })
  }),

  http.put(API_KM.KB.UPDATE, async () => {
    await delay(500)
    return HttpResponse.json({ code: 200, message: '更新成功', data: null })
  }),

  http.delete(API_KM.KB.DELETE, async () => {
    await delay(400)
    return HttpResponse.json({ code: 200, message: '删除成功', data: null })
  }),

  // === 文档管理 ===
  http.get(API_KM.DOC.LIST, async () => {
    await delay(400)
    return HttpResponse.json({
      code: 200, message: 'ok',
      data: { records: mockDocuments, total: mockDocuments.length, page: 1, pageSize: 10 },
    })
  }),

  http.post(API_KM.DOC.UPLOAD, async () => {
    await delay(1000)
    return HttpResponse.json({ code: 200, message: '上传成功', data: { id: Date.now(), status: 'UPLOADED' } })
  }),

  http.delete(API_KM.DOC.DELETE, async () => {
    await delay(400)
    return HttpResponse.json({ code: 200, message: '删除成功', data: null })
  }),

  // === 知识检索 ===
  http.post(API_KM.SEARCH.FRONTEND, async () => {
    await delay(500)
    return HttpResponse.json({
      code: 200, message: 'ok',
      data: { results: mockSearchResults, total: mockSearchResults.length },
    })
  }),

  // === Admin: 嵌入模型配置 ===
  http.get(API_KM.ADMIN.EMBED_CONFIG, async () => {
    await delay(300)
    return HttpResponse.json({
      code: 200, message: 'ok',
      data: { modelName: 'BAAI/bge-large-zh-v1.5', apiBase: 'https://api.siliconflow.cn/v1', apiKey: 'sk-****', dimension: 1024 },
    })
  }),

  http.put(API_KM.ADMIN.EMBED_CONFIG, async () => {
    await delay(500)
    return HttpResponse.json({ code: 200, message: '保存成功', data: null })
  }),

  // === Admin: 重排序模型配置 ===
  http.get(API_KM.ADMIN.RERANK_CONFIG, async () => {
    await delay(300)
    return HttpResponse.json({
      code: 200, message: 'ok',
      data: { modelName: 'BAAI/bge-reranker-v2-m3', apiBase: 'https://api.siliconflow.cn/v1', apiKey: 'sk-****', topN: 5 },
    })
  }),

  http.put(API_KM.ADMIN.RERANK_CONFIG, async () => {
    await delay(500)
    return HttpResponse.json({ code: 200, message: '保存成功', data: null })
  }),

  // === Admin: 解析器配置 ===
  http.get(API_KM.ADMIN.PARSER_CONFIG, async () => {
    await delay(300)
    return HttpResponse.json({
      code: 200, message: 'ok',
      data: { backend: 'default', maxConcurrent: 3, chunkSize: 512, chunkOverlap: 64 },
    })
  }),

  http.put(API_KM.ADMIN.PARSER_CONFIG, async () => {
    await delay(500)
    return HttpResponse.json({ code: 200, message: '保存成功', data: null })
  }),

  // === Admin: 统计数据 ===
  http.get(API_KM.ADMIN.STATS, async () => {
    await delay(300)
    return HttpResponse.json({
      code: 200, message: 'ok',
      data: { kbCount: 4, documentCount: 221, chunkCount: 15820 },
    })
  }),

  // === Admin: 趋势数据 ===
  http.get(API_KM.ADMIN.KM_TREND, async () => {
    await delay(300)
    return HttpResponse.json({
      code: 200, message: 'ok',
      data: Array.from({ length: 30 }, (_, i) => ({
        date: `2025-06-${String(i + 1).padStart(2, '0')}`,
        count: Math.floor(Math.random() * 10),
      })),
    })
  }),
]
