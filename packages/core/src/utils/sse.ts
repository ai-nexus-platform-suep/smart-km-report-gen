/**
 * 解析 SSE data 行
 * 格式: "data: {...}" → 返回 JSON 对象
 */
export function parseSseChunk(chunk: string): Record<string, unknown> | null {
  const lines = chunk.trim().split('\n')
  for (const line of lines) {
    if (line.startsWith('data: ')) {
      try {
        return JSON.parse(line.slice(6))
      } catch {
        return null
      }
    }
  }
  return null
}
