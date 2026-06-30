import type { SetupWorker } from 'msw/browser'
import type { MockModule } from './handlers'

let worker: SetupWorker | null = null

export async function startWorker(modules?: MockModule[]) {
  if (worker) return

  const { createWorker } = await import('./browser')
  worker = createWorker(modules)

  await worker.start({
    onUnhandledRequest: 'bypass',
    quiet: true,
  })
}

export type { MockModule }
