// apps/km-web/src/api/knowledge.ts
import { apiGet, apiPost, apiDelete, apiPost as apiDeleteBatch, API_KM } from '@platform/core'

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
