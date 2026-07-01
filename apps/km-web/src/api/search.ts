import { apiPost, apiGet, API_KM, replacePathParams } from '@platform/core'
import type { ApiResponse, SearchResultItem, SearchResponse } from '@platform/core/types'

/** 搜索参数 */
export interface SearchParams {
  query: string
  kbIds?: number[]
  mode?: 'VECTOR' | 'VECTOR_RERANK'
  topN?: number
  page?: number
  pageSize?: number
}

/** 知识库选项 */
export interface KbOption {
  id: number
  name: string
}

/** 执行搜索 */
export function searchDocuments(params: SearchParams) {
  return apiPost<ApiResponse<SearchResponse>>(API_KM.SEARCH.FRONTEND, params)
}

/** 获取知识库列表 */
export function fetchKbList(params?: Record<string, unknown>) {
  return apiGet<ApiResponse<{ records: KbOption[]; total: number }>>(API_KM.KB.LIST, params)
}
