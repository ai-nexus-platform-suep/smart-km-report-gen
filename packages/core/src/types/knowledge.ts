export type KnowledgeBaseType = 'REGULATION' | 'REPORT' | 'TERM' | 'GENERAL'

export type DocumentStatus = 'UPLOADED' | 'PARSING' | 'CHUNKING' | 'EMBEDDING' | 'READY' | 'FAILED'

export type SearchMode = 'VECTOR' | 'VECTOR_RERANK'

export interface KnowledgeBase {
  id: number
  name: string
  description: string
  type: KnowledgeBaseType
  searchMode: SearchMode
  documentCount: number
  creator: string
  createdAt: string
}

export interface Document {
  id: number
  kbId: number
  fileName: string
  fileType: string
  fileSize: number
  status: DocumentStatus
  tags: string[]
  errorMessage?: string
  createdAt: string
}

export interface Chunk {
  id: number
  docId: number
  content: string
  index: number
}

export interface ChunkStrategy {
  type: 'SEMANTIC' | 'FIXED_SIZE'
  chunkSize?: number
  overlap?: number
}

export interface SearchResultItem {
  documentId: number
  documentName: string
  content: string
  score: number
}

export interface SearchResponse {
  results: SearchResultItem[]
  total: number
}
