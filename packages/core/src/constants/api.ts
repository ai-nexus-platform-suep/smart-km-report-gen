// === B组（智能问答）===
export const API_QA = {
  AUTH: {
    LOGIN: '/api/v1/auth/login',
    REGISTER: '/api/v1/auth/register',
    LOGOUT: '/api/v1/auth/logout',
    PROFILE: '/api/v1/auth/profile',
  },
  CHAT: {
    LIST: '/api/v1/conversations',
    CREATE: '/api/v1/conversations',
    DELETE: '/api/v1/conversations/:id',
    STREAM: '/api/v1/chat/stream',
    HISTORY: '/api/v1/conversations/:id/messages',
  },
  SEARCH: {
    RETRIEVE: '/api/v1/search',
  },
  ADMIN: {
    QA_CONFIG: '/api/v1/admin/qa-config',
    LLM_CONFIG: '/api/v1/admin/llm-config',
    RETRIEVAL_TEST: '/api/v1/admin/retrieval-test',
    STATS: '/api/v1/admin/stats',
    QA_TREND: '/api/v1/admin/stats/qa-trend',
  },
} as const

// === A组（知识管理）===
export const API_KM = {
  KB: {
    LIST: '/api/v1/knowledge-bases',
    CREATE: '/api/v1/knowledge-bases',
    DETAIL: '/api/v1/knowledge-bases/:id',
    UPDATE: '/api/v1/knowledge-bases/:id',
    DELETE: '/api/v1/knowledge-bases/:id',
    BATCH_DELETE: '/api/v1/knowledge-bases/batch-delete',
  },
  DOC: {
    LIST: '/api/v1/knowledge-bases/:kbId/documents',
    UPLOAD: '/api/v1/knowledge-bases/:kbId/documents/upload',
    DETAIL: '/api/v1/knowledge-bases/:kbId/documents/:docId',
    DELETE: '/api/v1/knowledge-bases/:kbId/documents/:docId',
    BATCH_DELETE: '/api/v1/knowledge-bases/:kbId/documents/batch-delete',
    CHUNKS: '/api/v1/knowledge-bases/:kbId/documents/:docId/chunks',
    RETRY: '/api/v1/knowledge-bases/:kbId/documents/:docId/retry',
    TAGS: '/api/v1/knowledge-bases/:kbId/documents/:docId/tags',
  },
  SEARCH: {
    FRONTEND: '/api/v1/search',
  },
  ADMIN: {
    EMBED_CONFIG: '/api/v1/admin/config/embedding',
    RERANK_CONFIG: '/api/v1/admin/config/rerank',
    PARSER_CONFIG: '/api/v1/admin/config/parser',
    STATS: '/api/v1/admin/stats',
    KM_TREND: '/api/v1/admin/stats/km-trend',
  },
} as const

// === C组（报告生成）===
export const API_REPORT = {
  REPORT: {
    LIST: '/api/v1/reports',
    CREATE: '/api/v1/reports',
    DETAIL: '/api/v1/reports/:id',
    DELETE: '/api/v1/reports/:id',
    OUTLINE: '/api/v1/reports/:id/outline',
    GENERATE: '/api/v1/reports/:id/generate',
    EXPORT: '/api/v1/reports/:id/export/docx',
    DOWNLOAD: '/api/v1/reports/:id/download',
  },
  TEMPLATE: {
    LIST: '/api/v1/report-templates',
    UPLOAD: '/api/v1/report-templates/upload',
    DELETE: '/api/v1/report-templates/:id',
    DETAIL: '/api/v1/report-templates/:id',
  },
  MATERIAL: {
    LIST: '/api/v1/materials',
    UPLOAD: '/api/v1/materials/upload',
    DELETE: '/api/v1/materials/:id',
    TAGS: '/api/v1/materials/:id/tags',
  },
  ADMIN: {
    LLM_CONFIG: '/api/v1/admin/llm-config',
    STATS: '/api/v1/admin/stats',
    TREND: '/api/v1/admin/stats/report-trend',
  },
} as const

/** HTTP 状态码 / 业务码 */
export const CODE = {
  SUCCESS: 200,
  BAD_REQUEST: 400,
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  NOT_FOUND: 404,
  INTERNAL_ERROR: 500,
} as const
