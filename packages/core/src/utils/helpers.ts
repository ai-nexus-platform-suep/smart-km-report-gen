export function replacePathParams(path: string, params: Record<string, string | number>): string {
  let result = path
  for (const [key, value] of Object.entries(params)) {
    result = result.replace(`:${key}`, String(value))
  }
  return result
}

export function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}
