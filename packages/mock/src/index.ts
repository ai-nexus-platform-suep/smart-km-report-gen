import type { SetupWorker } from 'msw/browser'

let worker: SetupWorker | null = null

export async function startWorker() {
  if (worker) return

  const { worker: w } = await import('./browser')
  worker = w

  await worker.start({
    onUnhandledRequest: 'bypass',
    quiet: true,
  })
}
