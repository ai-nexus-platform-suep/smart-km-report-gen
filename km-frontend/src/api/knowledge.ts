// apps/km-web/src/api/knowledge.ts
import { apiGet, apiDelete, API_KM } from '@platform/core'

// 获取知识库分页列表
export function getKnowledgeBaseList(params: any) {
  // 架构同学封装的 apiGet 第二个参数直接就是 params
  return apiGet(API_KM.KB.LIST, params)
}

// 删除知识库
export function deleteKnowledgeBase(id: number) {
  // apiDelete 的第二个参数是 config 对象
  return apiDelete(API_KM.KB.DELETE, { params: { id } })
}