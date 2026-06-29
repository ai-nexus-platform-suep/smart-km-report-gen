import { authHandlers } from './auth'
import { qaHandlers } from './qa'
import { kmHandlers } from './km'
import { reportHandlers } from './report'

export const handlers = [
  ...authHandlers,
  ...qaHandlers,
  ...kmHandlers,
  ...reportHandlers,
]
