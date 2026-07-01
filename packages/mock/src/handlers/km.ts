import { http, HttpResponse, delay } from 'msw'
import { API_KM } from '@platform/core'
import type { KnowledgeBase, Document, SearchResultItem } from '@platform/core/types'

const now = new Date().toISOString()

let nextKbId = 5
let nextDocId = 6
let mockKnowledgeBases: KnowledgeBase[] = [
  { id: 1, name: '\u6c7d\u8f6e\u673a\u68c0\u4fee\u89c4\u7a0b', description: '\u706b\u529b\u53d1\u7535\u5382\u6c7d\u8f6e\u673a\u8bbe\u5907\u68c0\u4fee\u6807\u51c6\u89c4\u8303',
    type: 'REGULATION', searchMode: 'VECTOR_RERANK', documentCount: 12, creator: '\u7ba1\u7406\u5458', createdAt: now },
  { id: 2, name: '\u7535\u6c14\u8bbe\u5907\u6280\u672f\u62a5\u544a', description: '\u53bb\u5e74\u7535\u6c14\u8bbe\u5907\u8fd0\u884c\u4e0e\u7ef4\u62a4\u6280\u672f\u62a5\u544a\u6c47\u603b',
    type: 'REPORT', searchMode: 'VECTOR', documentCount: 8, creator: '\u7ba1\u7406\u5458', createdAt: now },
  { id: 3, name: '\u7535\u529b\u884c\u4e1a\u672f\u8bed\u5e93', description: '\u7535\u529b\u884c\u4e1a\u6807\u51c6\u672f\u8bed\u4e0e\u5b9a\u4e49',
    type: 'TERM', searchMode: 'VECTOR', documentCount: 156, creator: '\u7ba1\u7406\u5458', createdAt: now },
  { id: 4, name: '\u901a\u7528\u6280\u672f\u6587\u6863', description: '\u5404\u7c7b\u6280\u672f\u53c2\u8003\u8d44\u6599\u6c47\u603b',
    type: 'GENERAL', searchMode: 'VECTOR_RERANK', documentCount: 45, creator: '\u7528\u6237', createdAt: now },
]

let mockDocuments: Document[] = [
  { id: 1, kbId: 1, fileName: '\u6c7d\u8f6e\u673a\u68c0\u4fee\u89c4\u7a0bv3.0.pdf', fileType: 'pdf', fileSize: 2450000, status: 'READY', tags: ['\u6c7d\u8f6e\u673a', '\u68c0\u4fee', '\u89c4\u7a0b'], errorMessage: '', createdAt: now },
  { id: 2, kbId: 1, fileName: '\u9505\u7089\u68c0\u4fee\u5b89\u5168\u89c4\u8303.docx', fileType: 'docx', fileSize: 1800000, status: 'READY', tags: ['\u9505\u7089', '\u5b89\u5168'], errorMessage: '', createdAt: now },
  { id: 3, kbId: 2, fileName: '2024\u5e74\u5ea6\u7535\u6c14\u8bbe\u5907\u8fd0\u884c\u62a5\u544a.xlsx', fileType: 'xlsx', fileSize: 3200000, status: 'EMBEDDING', tags: ['\u7535\u6c14', '\u5e74\u5ea6\u62a5\u544a'], errorMessage: '', createdAt: now },
  { id: 4, kbId: 1, fileName: '\u6c7d\u673a\u68c0\u4fee\u8bb0\u5f55\u672c2025Q1.pdf', fileType: 'pdf', fileSize: 5200000, status: 'PARSING', tags: ['\u6c7d\u8f6e\u673a', '\u68c0\u4fee\u8bb0\u5f55'], errorMessage: '', createdAt: now },
  { id: 5, kbId: 3, fileName: '\u7535\u529b\u672f\u8bed\u8bcd\u5178.xlsx', fileType: 'xlsx', fileSize: 890000, status: 'READY', tags: ['\u672f\u8bed', '\u8bcd\u5178'], errorMessage: '', createdAt: now },
]

const mockChunkData: any[] = [
  { id: 1, docId: 1, chunkIndex: 0, content: '\u6c7d\u8f6e\u673a\u5927\u4fee\u5468\u671f\u4e00\u822c\u4e3a4-6\u5e74\uff0c\u5c0f\u4fee\u5468\u671f\u4e3a1-2\u5e74\u3002\u5927\u4fee\u5185\u5bb9\u5305\u62ec\uff1a\u6c7d\u7f38\u63ed\u7f38\u68c0\u67e5\u3001\u8f6c\u5b50\u68c0\u67e5\u3001\u53f6\u7247\u65e0\u635f\u68c0\u6d4b\u3001\u8f74\u627f\u7ffb\u4fee\u7b49\u3002', chunkType: 'heading', chapterPath: '\u7b2c\u4e00\u7ae0 > \u68c0\u4fee\u5468\u671f > \u5927\u4fee\u5468\u671f', charCount: 72 },
  { id: 2, docId: 1, chunkIndex: 1, content: '\u6c7d\u8f6e\u673a\u6da6\u6ed1\u6cb9\u7cfb\u7edf\u5e94\u5b9a\u671f\u53d6\u6837\u5316\u9a8c\uff0c\u6cb9\u8d28\u4e0d\u5408\u683c\u65f6\u5e94\u53ca\u65f6\u66f4\u6362\u3002\u8f74\u627f\u6e29\u5ea6\u62a5\u8b66\u503c\u4e3a85\u2103\uff0c\u8df3\u673a\u503c\u4e3a95\u2103\u3002', chunkType: 'heading', chapterPath: '\u7b2c\u4e8c\u7ae0 > \u6da6\u6ed1\u7cfb\u7edf > \u6da6\u6ed1\u6cb9\u7ba1\u7406', charCount: 68 },
  { id: 3, docId: 1, chunkIndex: 2, content: '\u6c7d\u8f6e\u673a\u8f6c\u5b50\u5728\u68c0\u4fee\u524d\u9700\u8fdb\u884c\u51b7\u5374\uff0c\u51b7\u5374\u65f6\u95f4\u4e0d\u5c11\u4e8e24\u5c0f\u65f6\u3002\u8f6c\u5b50\u540a\u51fa\u540e\u653e\u7f6e\u4e8e\u4e13\u7528\u652f\u67b6\u4e0a\uff0c\u907f\u514d\u53d8\u5f62\u3002', chunkType: 'text', chapterPath: '\u7b2c\u4e09\u7ae0 > \u8f6c\u5b50\u68c0\u4fee > \u62c6\u5378\u4e0e\u653e\u7f6e', charCount: 65 },
  { id: 4, docId: 2, chunkIndex: 0, content: '\u9505\u7089\u68c0\u4fee\u524d\u5fc5\u987b\u529e\u7406\u5de5\u4f5c\u7968\uff0c\u505a\u597d\u5b89\u5168\u63aa\u65bd\u3002\u8fdb\u5165\u7089\u819b\u524d\u9700\u8fdb\u884c\u6c14\u4f53\u68c0\u6d4b\uff0c\u786e\u4fdd\u6c27\u6c14\u542b\u91cf\u572819.5%-23.5%\u4e4b\u95f4\u3002', chunkType: 'heading', chapterPath: '\u7b2c\u4e00\u7ae0 > \u5b89\u5168\u63aa\u65bd > \u5de5\u4f5c\u7968\u5236\u5ea6', charCount: 71 },
  { id: 5, docId: 2, chunkIndex: 1, content: '\u9505\u7089\u53d7\u70ed\u9762\u68c0\u4fee\u5305\u62ec\u6c34\u51b7\u58c1\u3001\u8fc7\u70ed\u5668\u3001\u518d\u70ed\u5668\u3001\u7701\u7164\u5668\u7b49\u90e8\u4f4d\u7684\u68c0\u67e5\u4e0e\u4fee\u7406\u3002\u91cd\u70b9\u68c0\u67e5\u7ba1\u5b50\u78e8\u635f\u3001\u8100\u7c97\u3001\u88c2\u7eb9\u7b49\u7f3a\u9677\u3002', chunkType: 'heading', chapterPath: '\u7b2c\u4e8c\u7ae0 > \u53d7\u70ed\u9762\u68c0\u4fee > \u68c0\u67e5\u5185\u5bb9', charCount: 73 },
]

const mockSearchResults: SearchResultItem[] = [
  { documentId: 1, documentName: '\u6c7d\u8f6e\u673a\u68c0\u4fee\u89c4\u7a0bv3.0.pdf', content: '\u6c7d\u8f6e\u673a\u5927\u4fee\u5468\u671f\u4e00\u822c\u4e3a4-6\u5e74\uff0c\u5c0f\u4fee\u5468\u671f\u4e3a1-2\u5e74\u3002\u5927\u4fee\u5185\u5bb9\u5305\u62ec\uff1a\u6c7d\u7f38\u63ed\u7f38\u68c0\u67e5\u3001\u8f6c\u5b50\u68c0\u67e5\u3001\u53f6\u7247\u65e0\u635f\u68c0\u6d4b\u3001\u8f74\u627f\u7ffb\u4fee\u7b49\u3002', score: 0.92 },
  { documentId: 2, documentName: '\u9505\u7089\u68c0\u4fee\u5b89\u5168\u89c4\u8303.docx', content: '\u9505\u7089\u68c0\u4fee\u524d\u5fc5\u987b\u529e\u7406\u5de5\u4f5c\u7968\uff0c\u505a\u597d\u5b89\u5168\u63aa\u65bd\u3002\u8fdb\u5165\u7089\u819b\u524d\u9700\u8fdb\u884c\u6c14\u4f53\u68c0\u6d4b\uff0c\u786e\u4fdd\u6c27\u6c14\u542b\u91cf\u572819.5%-23.5%\u4e4b\u95f4\u3002', score: 0.85 },
  { documentId: 1, documentName: '\u6c7d\u8f6e\u673a\u68c0\u4fee\u89c4\u7a0bv3.0.pdf', content: '\u6c7d\u8f6e\u673a\u6da6\u6ed1\u6cb9\u7cfb\u7edf\u5e94\u5b9a\u671f\u53d6\u6837\u5316\u9a8c\uff0c\u6cb9\u8d28\u4e0d\u5408\u683c\u65f6\u5e94\u53ca\u65f6\u66f4\u6362\u3002\u8f74\u627f\u6e29\u5ea6\u62a5\u8b66\u503c\u4e3a85\u2103\uff0c\u8df3\u673a\u503c\u4e3a95\u2103\u3002', score: 0.78 },
]

export const kmHandlers = [
  http.get('/api/knowledge-bases/:id', async ({ params }) => {
    await delay(200)
    const id = Number(params.id)
    const kb = mockKnowledgeBases.find(k => k.id === id)
    if (!kb) return HttpResponse.json({ code: 404, message: 'Not found', data: null }, { status: 404 })
    return HttpResponse.json({ code: 200, message: 'ok', data: kb })
  }),

  http.get(API_KM.KB.LIST, async ({ request }) => {
    await delay(400)
    const url = new URL(request.url)
    const docType = url.searchParams.get('docType') || ''
    const keyword = url.searchParams.get('keyword') || ''
    let filtered = [...mockKnowledgeBases]
    if (docType) filtered = filtered.filter(kb => kb.type === docType)
    if (keyword) filtered = filtered.filter(kb => kb.name.includes(keyword))
    return HttpResponse.json({
      code: 200, message: 'ok',
      data: { records: filtered, total: filtered.length, page: 1, pageSize: 10 },
    })
  }),

  http.post(API_KM.KB.BATCH_DELETE, async ({ request }) => {
    await delay(400)
    const body = await request.json() as any
    const ids = body?.ids || []
    mockKnowledgeBases = mockKnowledgeBases.filter(kb => !ids.includes(kb.id))
    return HttpResponse.json({ code: 200, message: 'ok', data: null })
  }),

  http.post(API_KM.KB.CREATE, async ({ request }) => {
    await delay(500)
    const body = await request.json() as any
    const newKb = {
      id: nextKbId++, name: body.name || 'New KB', description: body.description || '',
      type: body.type || 'GENERAL', searchMode: body.searchMode || 'VECTOR',
      documentCount: 0, creator: 'Admin', createdAt: new Date().toISOString(),
    }
    mockKnowledgeBases.unshift(newKb)
    return HttpResponse.json({ code: 200, message: 'ok', data: newKb })
  }),

  http.put(API_KM.KB.UPDATE, async ({ request }) => {
    await delay(500)
    const url = new URL(request.url)
    const id = Number(url.searchParams.get('id'))
    const body = await request.json() as any
    const idx = mockKnowledgeBases.findIndex(k => k.id === id)
    if (idx > -1) { mockKnowledgeBases[idx] = { ...mockKnowledgeBases[idx], ...body } }
    return HttpResponse.json({ code: 200, message: 'ok', data: null })
  }),

  http.delete(API_KM.KB.DELETE, async ({ request }) => {
    await delay(400)
    const url = new URL(request.url)
    const id = Number(url.searchParams.get('id'))
    mockKnowledgeBases = mockKnowledgeBases.filter(kb => kb.id !== id)
    return HttpResponse.json({ code: 200, message: 'ok', data: null })
  }),

  http.get('/api/knowledge-bases/:kbId/documents', async ({ params, request }) => {
    await delay(400)
    const kbId = Number(params.kbId)
    const url = new URL(request.url)
    const statusFilter = url.searchParams.get('status') || ''
    const keyword = url.searchParams.get('keyword') || ''
    let filtered = mockDocuments.filter(d => d.kbId === kbId)
    if (statusFilter) filtered = filtered.filter(d => d.status === statusFilter)
    if (keyword) filtered = filtered.filter(d => d.fileName.includes(keyword))
    return HttpResponse.json({
      code: 200, message: 'ok',
      data: { records: filtered, total: filtered.length, page: 1, pageSize: 10 },
    })
  }),

  http.post('/api/knowledge-bases/:kbId/documents/upload', async ({ params }) => {
    await delay(1000)
    const kbId = Number(params.kbId)
    const newDoc = {
      id: nextDocId++, kbId: kbId || 1, fileName: 'doc_' + Date.now() + '.pdf',
      fileType: 'pdf', fileSize: Math.floor(Math.random() * 5000000) + 100000,
      status: 'UPLOADED', tags: [], errorMessage: '', createdAt: new Date().toISOString(),
    }
    mockDocuments.unshift(newDoc)
    return HttpResponse.json({ code: 200, message: 'ok', data: { id: newDoc.id, status: 'UPLOADED' } })
  }),

  http.delete('/api/knowledge-bases/:kbId/documents/batch-delete', async ({ request }) => {
    await delay(400)
    let ids: number[] = []
    try {
      const body = await request.clone().json()
      ids = (body?.ids || []).map((id: any) => Number(id))
    } catch {
      try {
        const text = await request.clone().text()
        if (text) {
          const parsed = JSON.parse(text)
          ids = (parsed?.ids || []).map((id: any) => Number(id))
        }
      } catch {}
    }
    mockDocuments = mockDocuments.filter(d => !ids.includes(d.id))
    return HttpResponse.json({ code: 200, message: 'ok', data: { deletedIds: ids } })
  }),

http.delete('/api/knowledge-bases/:kbId/documents/:docId', async ({ params }) => {
    await delay(400)
    const docId = Number(params.docId)
    mockDocuments = mockDocuments.filter(d => d.id !== docId)
    return HttpResponse.json({ code: 200, message: 'ok', data: null })
  }),

  http.put('/api/knowledge-bases/:kbId/documents/:docId/tags', async ({ params, request }) => {
    await delay(300)
    const docId = Number(params.docId)
    const body = await request.json() as any
    const newTags = body?.tags || []
    const doc = mockDocuments.find(d => d.id === docId)
    if (doc) { doc.tags = newTags }
    return HttpResponse.json({ code: 200, message: 'ok', data: null })
  }),

  http.post('/api/knowledge-bases/:kbId/documents/:docId/retry', async () => {
    await delay(300)
    return HttpResponse.json({ code: 200, message: 'ok', data: null })
  }),

  http.get('/api/knowledge-bases/:kbId/documents/:docId/chunks', async ({ params, request }) => {
    await delay(300)
    const docId = Number(params.docId)
    const url = new URL(request.url)
    const pg = Number(url.searchParams.get('page')) || 1
    const ps = Number(url.searchParams.get('pageSize')) || 10
    const dc = mockChunkData.filter(c => c.docId === docId)
    const start = (pg - 1) * ps
    return HttpResponse.json({ code: 200, message: 'ok', data: { list: dc.slice(start, start + ps), total: dc.length } })
  }),

  

  http.post(API_KM.SEARCH.FRONTEND, async () => {
    await delay(500)
    return HttpResponse.json({ code: 200, message: 'ok', data: { results: mockSearchResults, total: mockSearchResults.length } })
  }),

  http.get(API_KM.ADMIN.EMBED_CONFIG, async () => {
    await delay(300)
    return HttpResponse.json({ code: 200, message: 'ok', data: { modelName: 'BAAI/bge-large-zh-v1.5', apiBase: 'https://api.siliconflow.cn/v1', apiKey: 'sk-****', dimension: 1024 } })
  }),
  http.put(API_KM.ADMIN.EMBED_CONFIG, async () => {
    await delay(500)
    return HttpResponse.json({ code: 200, message: 'ok', data: null })
  }),

  http.get(API_KM.ADMIN.RERANK_CONFIG, async () => {
    await delay(300)
    return HttpResponse.json({ code: 200, message: 'ok', data: { modelName: 'BAAI/bge-reranker-v2-m3', apiBase: 'https://api.siliconflow.cn/v1', apiKey: 'sk-****', topN: 5 } })
  }),
  http.put(API_KM.ADMIN.RERANK_CONFIG, async () => {
    await delay(500)
    return HttpResponse.json({ code: 200, message: 'ok', data: null })
  }),

  http.get(API_KM.ADMIN.PARSER_CONFIG, async () => {
    await delay(300)
    return HttpResponse.json({ code: 200, message: 'ok', data: { backend: 'default', maxConcurrent: 3, chunkSize: 512, chunkOverlap: 64 } })
  }),
  http.put(API_KM.ADMIN.PARSER_CONFIG, async () => {
    await delay(500)
    return HttpResponse.json({ code: 200, message: 'ok', data: null })
  }),

  http.get(API_KM.ADMIN.STATS, async () => {
    await delay(300)
    return HttpResponse.json({ code: 200, message: 'ok', data: { kbCount: 4, documentCount: 221, chunkCount: 15820, todayProcessed: 12 } })
  }),

  http.get(API_KM.ADMIN.KM_TREND, async () => {
    await delay(300)
    const arr = []
    for (let i = 0; i < 30; i++) {
      arr.push({ date: '2025-06-' + String(i + 1).padStart(2, '0'), count: Math.floor(Math.random() * 10) })
    }
    return HttpResponse.json({ code: 200, message: 'ok', data: arr })
  }),
]
