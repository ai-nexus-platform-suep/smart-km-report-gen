import { setupWorker } from 'msw/browser'
import { createHandlers } from './handlers'
import type { MockModule } from './handlers'

export function createWorker(modules?: MockModule[]) {
  return setupWorker(...createHandlers(modules))
}

export const worker = createWorker()
