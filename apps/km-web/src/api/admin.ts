// apps/km-web/src/api/admin.ts
import { apiGet, apiPut, API_KM } from '@platform/core'

// ============ 概览统计 ============

export interface StatsOverview {
  totalKb: number
  totalDocs: number
  totalChunks: number
  processedToday: number
}

export interface TrendItem {
  date: string
  count: number
}

export function getStatsOverview() {
  return apiGet<{ code: number; data: StatsOverview; message: string }>(API_KM.ADMIN.STATS)
}

export function getKmTrend() {
  return apiGet<{ code: number; data: TrendItem[]; message: string }>(API_KM.ADMIN.KM_TREND)
}

// ============ 嵌入模型配置 ============

export interface EmbedConfig {
  provider: string
  modelName: string
  apiKey: string
  apiBase?: string
  dimension: number
  maxBatchSize: number
}

export function getEmbedConfig() {
  return apiGet<{ code: number; data: EmbedConfig; message: string }>(API_KM.ADMIN.EMBED_CONFIG)
}

export function saveEmbedConfig(data: Partial<EmbedConfig>) {
  return apiPut<{ code: number; data: EmbedConfig; message: string }>(API_KM.ADMIN.EMBED_CONFIG, data)
}

// ============ 重排序模型配置 ============

export interface RerankConfig {
  provider: string
  modelName: string
  apiKey: string
  apiBase?: string
  topK: number
  minScore: number
}

export function getRerankConfig() {
  return apiGet<{ code: number; data: RerankConfig; message: string }>(API_KM.ADMIN.RERANK_CONFIG)
}

export function saveRerankConfig(data: Partial<RerankConfig>) {
  return apiPut<{ code: number; data: RerankConfig; message: string }>(API_KM.ADMIN.RERANK_CONFIG, data)
}

// ============ 解析器配置 ============

export interface ParserConfig {
  concurrency: number
  maxFileSize: number
  supportedTypes: string[]
  timeoutSeconds: number
}

export function getParserConfig() {
  return apiGet<{ code: number; data: ParserConfig; message: string }>(API_KM.ADMIN.PARSER_CONFIG)
}

export function saveParserConfig(data: Partial<ParserConfig>) {
  return apiPut<{ code: number; data: ParserConfig; message: string }>(API_KM.ADMIN.PARSER_CONFIG, data)
}
