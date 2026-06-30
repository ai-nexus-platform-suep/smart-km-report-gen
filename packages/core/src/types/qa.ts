import type { SearchMode } from './knowledge'

export type MessageRole = 'user' | 'assistant' | 'system'
export type IntentType =
  | 'CHAT'
  | 'KNOWLEDGE_QA'
  | 'DOCUMENT_SEARCH'
  | 'REPORT_GENERATION'
  | 'KB_MANAGEMENT'
  | 'TASK_ACTION'
export type GenerateStatus = 0 | 1 | 2
export type ThinkingStatus = 'pending' | 'running' | 'done'
export type ModelProvider = 'deepseek' | 'openai' | 'qwen' | 'siliconflow'
export type ModelScenario = 'chat' | 'report_generate'

export interface ConversationSchema {
  session_id: number
  title: string
  message_count: number
  last_message_at: string
  created_at: string
}

export interface ConversationListResult {
  items: ConversationSchema[]
  total: number
  page: number
  size: number
}

export interface MessageSchema {
  message_id: number
  seq: number
  role: MessageRole
  content: string
  intent_type?: IntentType
  thinking_steps?: string | null
  citations?: string | null
  generate_status?: GenerateStatus
  token_usage?: Record<string, unknown> | null
  created_at: string
  updated_at?: string
}

export interface ConversationMessagesResult {
  session_id: number
  title: string
  total: number
  messages: MessageSchema[]
}

export interface Citation {
  documentId?: number
  documentName: string
  content: string
  score: number
  source?: string
}

export interface ThinkingStep {
  label: string
  content: string
  status: ThinkingStatus
}

export interface ChatTestRequest {
  question: string
  selected_kb_ids?: number[]
  user_id?: number | null
  messages?: Array<{
    role: MessageRole
    content: string
  }>
}

export interface ChatStreamRequest {
  conversation_id: number
  question: string
  user_id?: number | null
  selected_kb_ids?: number[]
}

export interface ChatTestResponse {
  intent: IntentType
  mode?: string
  needs_clarification?: boolean
  retrieved_docs_count?: number
  thinking_steps?: ThinkingStep[]
  citations?: Citation[]
  final_response: string
}

export interface SseEvent {
  type: 'thinking' | 'message' | 'citation' | 'done' | 'error'
  data: unknown
}

export interface ModelConfigVO {
  id: number
  userId: number
  provider: ModelProvider
  baseUrl: string
  modelName: string
  apiKeyMasked: string
  scenario: ModelScenario
  enabled: 0 | 1
  isDefault: 0 | 1
  createdAt: string
  updatedAt: string
}

export interface ModelConfigPayload {
  provider: ModelProvider
  baseUrl: string
  modelName: string
  apiKey: string
  scenario?: ModelScenario
}

export interface Conversation {
  id: number
  title: string
  summary: string
  messageCount: number
  lastMessageAt: string
  createdAt: string
  tag?: string
}

export interface Message {
  id: number
  conversationId: number
  seq?: number
  role: Exclude<MessageRole, 'system'>
  content: string
  intentType?: IntentType
  generateStatus?: GenerateStatus
  citations?: Citation[]
  thinkingSteps?: ThinkingStep[]
  createdAt: string
}

export interface ChatRequest {
  conversationId?: number
  message: string
  knowledgeBaseIds?: number[]
  searchMode?: SearchMode
  topK?: number
}
