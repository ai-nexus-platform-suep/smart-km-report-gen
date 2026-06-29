import client from './client'

export interface EmbeddingConfig {
  modelName: string
  apiUrl: string
  apiKey: string
  dimension: number
  updatedAt?: string
}

export interface RerankConfig {
  modelName: string
  apiUrl: string
  apiKey: string
  topN: number
  updatedAt?: string
}

export interface ParserConfig {
  backend: string
  maxConcurrency: number
  updatedAt?: string
}

export async function getEmbeddingConfig(): Promise<EmbeddingConfig> {
  const { data } = await client.get('/admin/config/embedding')
  return data.data
}

export async function updateEmbeddingConfig(payload: Partial<EmbeddingConfig>): Promise<EmbeddingConfig> {
  const { data } = await client.put('/admin/config/embedding', payload)
  return data.data
}

export async function getRerankConfig(): Promise<RerankConfig> {
  const { data } = await client.get('/admin/config/rerank')
  return data.data
}

export async function updateRerankConfig(payload: Partial<RerankConfig>): Promise<RerankConfig> {
  const { data } = await client.put('/admin/config/rerank', payload)
  return data.data
}

export async function getParserConfig(): Promise<ParserConfig> {
  const { data } = await client.get('/admin/config/parser')
  return data.data
}

export async function updateParserConfig(payload: Partial<ParserConfig>): Promise<ParserConfig> {
  const { data } = await client.put('/admin/config/parser', payload)
  return data.data
}
