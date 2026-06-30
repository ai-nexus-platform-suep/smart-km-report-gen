// === B组（智能问答）===
export const API_QA = {
  AUTH: {
    LOGIN: '/api/auth/login',
    REGISTER: '/api/auth/register',
    REFRESH: '/api/auth/refresh',
    ME: '/api/auth/me',
    LOGOUT: '/api/auth/logout',
    PROFILE: '/api/auth/me',
  },
  CHAT: {
    LIST: '/api/conversations',
    CREATE: '/api/conversations',
    UPDATE_TITLE: '/api/conversations/:id',
    DELETE: '/api/conversations/:id',
    TEST: '/api/chat/test',
    STREAM: '/api/chat',
    HISTORY: '/api/conversations/:id/messages',
  },
  SEARCH: {
    RETRIEVE: '/api/search',
  },
  ADMIN: {
    QA_CONFIG: '/api/admin/qa-config',
    RETRIEVAL_TEST: '/api/admin/retrieval-test',
    STATS: '/api/stats/qa/overview',
    QA_TREND: '/api/admin/stats/qa-trend',
  },
  MODEL_CONFIG: {
    LIST: '/api/model-configs',
    CREATE: '/api/model-configs',
    UPDATE: '/api/model-configs/:id',
    DELETE: '/api/model-configs/:id',
    SET_DEFAULT: '/api/model-configs/:id/default',
    INTERNAL_DEFAULT: '/internal/model-configs/default',
  },
} as const

// === A组（知识管理）===
export const API_KM = {
  KB: {
    LIST: '/api/knowledge-bases',
    CREATE: '/api/knowledge-bases',
    DETAIL: '/api/knowledge-bases/:id',
    UPDATE: '/api/knowledge-bases/:id',
    DELETE: '/api/knowledge-bases/:id',
    BATCH_DELETE: '/api/knowledge-bases/batch-delete',
  },
  DOC: {
    LIST: '/api/knowledge-bases/:kbId/documents',
    UPLOAD: '/api/knowledge-bases/:kbId/documents/upload',
    DETAIL: '/api/knowledge-bases/:kbId/documents/:docId',
    DELETE: '/api/knowledge-bases/:kbId/documents/:docId',
    BATCH_DELETE: '/api/knowledge-bases/:kbId/documents/batch-delete',
    CHUNKS: '/api/knowledge-bases/:kbId/documents/:docId/chunks',
    RETRY: '/api/knowledge-bases/:kbId/documents/:docId/retry',
    TAGS: '/api/knowledge-bases/:kbId/documents/:docId/tags',
  },
  SEARCH: {
    FRONTEND: '/api/search',
  },
  ADMIN: {
    EMBED_CONFIG: '/api/admin/config/embedding',
    RERANK_CONFIG: '/api/admin/config/rerank',
    PARSER_CONFIG: '/api/admin/config/parser',
    STATS: '/api/admin/stats',
    KM_TREND: '/api/admin/stats/km-trend',
  },
} as const

// === C组（报告生成）===
export const API_REPORT = {
  REPORT: {
    LIST: '/api/reports',
    CREATE: '/api/reports',
    DETAIL: '/api/reports/:id',
    DELETE: '/api/reports/:id',
    OUTLINE: '/api/reports/:id/outline',
    GENERATE: '/api/reports/:id/generate',
    EXPORT: '/api/reports/:id/export/docx',
    DOWNLOAD: '/api/reports/:id/download',
  },
  TEMPLATE: {
    LIST: '/api/report-templates',
    UPLOAD: '/api/report-templates/upload',
    DELETE: '/api/report-templates/:id',
    DETAIL: '/api/report-templates/:id',
  },
  MATERIAL: {
    LIST: '/api/materials',
    UPLOAD: '/api/materials/upload',
    DELETE: '/api/materials/:id',
    TAGS: '/api/materials/:id/tags',
  },
  ADMIN: {
    LLM_CONFIG: '/api/admin/llm-config',
    STATS: '/api/admin/stats',
    TREND: '/api/admin/stats/report-trend',
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
