export interface Conversation {
  id: number
  title: string
  createdAt: string
  updatedAt: string
}

export interface Message {
  id: number
  conversationId: number
  role: 'user' | 'assistant'
  content: string
  citations?: Citation[]
  thinkingSteps?: ThinkingStep[]
  createdAt: string
}

export interface Citation {
  documentId: number
  documentName: string
  content: string
  score: number
}

export interface ThinkingStep {
  label: string
  content: string
  status: 'pending' | 'running' | 'done'
}

export interface ChatRequest {
  conversationId?: number
  message: string
  knowledgeBaseIds?: number[]
  searchMode?: SearchMode
  topK?: number
}

export interface SseEvent {
  type: 'thinking' | 'content' | 'citation' | 'done' | 'error'
  data: unknown
}

import type { SearchMode } from './knowledge'
