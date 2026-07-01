// apps/km-web/src/api/knowledge.ts
import { apiGet, apiPost, apiPut, apiDelete, apiPost as apiDeleteBatch, API_KM } from '@platform/core'

export interface CreateKnowledgeBaseRequest {
  name: string
  description?: string
  docType: string
  chunkStrategy: {
    type: 'heading' | 'fixed_size'
    separator?: string
    recursiveMerge?: boolean
    chunkSize?: number
    overlap?: number
  }
  searchStrategy: string
}

export function getKnowledgeBaseList(params: any) {
  return apiGet(API_KM.KB.LIST, params)
}

// 获取知识库详情
export function getKnowledgeBaseDetail(id: number) {
  return apiGet(`/api/knowledge-bases/${id}`)
}

// 更新知识库
export function updateKnowledgeBase(id: number, data: Partial<CreateKnowledgeBaseRequest>) {
  return apiPut(API_KM.KB.UPDATE, data, { params: { id } })
}

// 批量删除知识库
export function batchDeleteKnowledgeBase(ids: number[]) {
  return apiDeleteBatch(API_KM.KB.BATCH_DELETE, { ids })
}

export function createKnowledgeBase(data: CreateKnowledgeBaseRequest) {
  return apiPost(API_KM.KB.CREATE, data)
}

export function deleteKnowledgeBase(id: number) {
  return apiDelete(API_KM.KB.DELETE, { params: { id } })
}

export interface SearchParams {
  keyword: string
  kbId?: number
  page?: number
  pageSize?: number
}

export function searchKnowledge(params: SearchParams) {
  return apiGet(API_KM.SEARCH.FRONTEND, params as Record<string, unknown>)
}