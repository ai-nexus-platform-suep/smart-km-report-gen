import client from './client'

export interface HealthData {
  service: string
  status: string
  version: string
}

export async function checkHealth(): Promise<HealthData> {
  const { data } = await client.get('/health')
  return data.data
}
