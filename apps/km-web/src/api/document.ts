import { apiGet, apiPost, apiPut, apiDelete } from '@platform/core/utils/request'
import { replacePathParams } from '@platform/core/utils/helpers'
import { API_KM } from '@platform/core/constants'
import type { ApiResponse, Document, Chunk, DocumentUploadResult, DocumentDeleteResult, DocumentBatchDeleteResult } from '@platform/core/types'

export interface DocListParams {
  page?: number
  pageSize?: number
  status?: string
  keyword?: string
}

export function listDocuments(kbId: number | string, params?: DocListParams) {
  return apiGet<ApiResponse<{ list: Document[]; total: number; page: number; pageSize: number }>>(
    replacePathParams(API_KM.DOC.LIST, { kbId }),
    params as Record<string, unknown>,
  )
}

export function uploadDocument(kbId: number | string, file: File, onProgress?: (pct: number) => void) {
  const fd = new FormData()
  fd.append('file', file)
  return apiPost<ApiResponse<DocumentUploadResult>>(
    replacePathParams(API_KM.DOC.UPLOAD, { kbId }),
    fd,
    {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: onProgress
        ? (e: { loaded: number; total?: number }) => onProgress(e.total ? Math.round(e.loaded * 100 / e.total) : 0)
        : undefined,
    },
  )
}

export function deleteDocument(kbId: number | string, docId: string) {
  return apiDelete<ApiResponse<DocumentDeleteResult>>(replacePathParams(API_KM.DOC.DELETE, { kbId, docId }))
}

export function batchDeleteDocuments(kbId: number | string, ids: string[]) {
  return apiDelete<ApiResponse<DocumentBatchDeleteResult>>(replacePathParams(API_KM.DOC.BATCH_DELETE, { kbId }),
    { data: { ids } })
}

export function retryProcessDocument(kbId: number | string, docId: string) {
  return apiPost<ApiResponse<null>>(replacePathParams(API_KM.DOC.RETRY, { kbId, docId }))
}

export function listChunks(kbId: number | string, docId: string, params?: { page?: number; pageSize?: number }) {
  return apiGet<ApiResponse<{ list: Chunk[]; total: number }>>(
    replacePathParams(API_KM.DOC.CHUNKS, { kbId, docId }),
    params as Record<string, unknown>,
  )
}

export function updateDocumentTags(kbId: number | string, docId: string, tags: Record<string, string>) {
  return apiPut<ApiResponse<null>>(replacePathParams(API_KM.DOC.TAGS, { kbId, docId }), { tags })
}
