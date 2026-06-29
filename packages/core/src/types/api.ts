/** 统一后端响应体 */
export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

/** 分页结果 */
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  pageSize: number
}
