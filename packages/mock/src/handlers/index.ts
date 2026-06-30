import { authHandlers } from './auth'
import { qaHandlers } from './qa'
import { kmHandlers } from './km'
import { reportHandlers } from './report'

export type MockModule = 'auth' | 'qa' | 'km' | 'report'

export const handlerGroups: Record<MockModule, typeof authHandlers> = {
  auth: authHandlers,
  qa: qaHandlers,
  km: kmHandlers,
  report: reportHandlers,
}

export const handlers = Object.values(handlerGroups).flat()

export function createHandlers(modules: MockModule[] = ['auth', 'qa', 'km', 'report']) {
  return modules.flatMap((moduleName) => handlerGroups[moduleName] ?? [])
}
