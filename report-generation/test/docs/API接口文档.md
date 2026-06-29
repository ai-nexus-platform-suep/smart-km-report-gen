# API 接口文档

> 项目：技术监督辅助平台 · 报告生成模块（feat-c）  
> 依据：`项目接口清单.md`、`report-outline-docx` 源码  
> 日期：2026-06-29

---

## 1. 通用说明

### 1.1 基础地址

| 环境 | 地址 |
|---|---|
| 本地后端 1 服务 | `http://127.0.0.1:8080` |
| 网关地址 | `http://127.0.0.1:8088` |

说明：

- `report-outline-docx` 后端 1 服务默认建议从 `8080` 访问。
- 如通过 Gateway 统一转发，则以前端或网关配置的 `8088` 为准。

### 1.2 通用响应格式

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

### 1.3 通用请求头

```http
Content-Type: application/json; charset=UTF-8
Authorization: Bearer <accessToken>
```

备注：认证接口中 `GET /api/auth/me` 在接口清单中写明使用 `X-Username` Header；若后续网关统一接入 JWT，以实际实现为准。

### 1.4 错误码

| 错误码 | 含义 | 场景 |
|---:|---|---|
| 200 | 操作成功 | 请求正常 |
| 400 | 请求参数错误 | 参数缺失、枚举非法、校验失败 |
| 401 | 未认证 | Token 缺失或无效 |
| 403 | 无权限 | 非管理员访问管理接口 |
| 404 | 资源不存在 | 报告、章节、文件不存在 |
| 1001 | 用户不存在 | 登录用户名不存在 |
| 1002 | 用户已存在 | 注册用户名重复 |
| 1003 | 密码错误 | 登录密码不匹配 |
| 1004 | Token 类型错误 | Token 类型不符合要求 |
| 1005 | Refresh Token 无效或过期 | 刷新 Token 失败 |
| 2001 | 大纲生成失败 | AI 调用超时或返回异常 |
| 2002 | 章节生成失败 | SSE 生成过程异常 |
| 2003 | DOCX 导出失败 | 文件生成或写入异常 |

---

## 2. 接口总览

| 模块 | 接口数 | 当前状态 | 责任人 |
|---|---:|---|---|
| 认证 Gateway | 4 | 已完成 | 组长 |
| 报告核心：健康检查、大纲、DOCX | 6 | 已完成 | 后端 1 |
| SSE 流式生成 | 2 | 待开发 | 后端 2 |
| 章节内容管理 | 4 | 待开发 | 后端 2 |
| 历史记录管理 | 3 | 待开发 | 后端 2 |
| 管理后台统计概览 | 2 | 待开发 | 后端 2 |
| 管理后台模型配置 | 2 | 待开发 | 后端 2 |
| 管理后台模板管理 | 3 | 待开发 | 后端 2 |

---

## 3. 认证接口

### 3.1 用户注册

```http
POST /api/auth/register
```

请求体：

```json
{
  "username": "test_user_01",
  "password": "Test@123456"
}
```

成功响应：

```json
{
  "code": 200,
  "message": "注册成功"
}
```

异常场景：

| 场景 | 预期 |
|---|---|
| 用户名为空 | 返回 400 |
| 密码为空 | 返回 400 |
| 用户名重复 | 返回 1002 或用户已存在提示 |

### 3.2 用户登录

```http
POST /api/auth/login
```

请求体：

```json
{
  "username": "test_user_01",
  "password": "Test@123456"
}
```

成功响应：

```json
{
  "code": 200,
  "data": {
    "accessToken": "xxx",
    "refreshToken": "xxx",
    "tokenType": "Bearer",
    "expiresIn": 7200,
    "username": "test_user_01",
    "roles": ["USER"]
  }
}
```

异常场景：

| 场景 | 预期 |
|---|---|
| 用户不存在 | 返回 1001 |
| 密码错误 | 返回 1003 |

### 3.3 Token 刷新

```http
POST /api/auth/refresh
```

请求体：

```json
{
  "refreshToken": "xxx"
}
```

成功响应：

```json
{
  "code": 200,
  "data": {
    "accessToken": "new-access-token",
    "refreshToken": "new-refresh-token",
    "tokenType": "Bearer",
    "expiresIn": 7200
  }
}
```

### 3.4 获取当前用户

```http
GET /api/auth/me
```

请求头：

```http
X-Username: test_user_01
```

成功响应：

```json
{
  "code": 200,
  "data": {
    "username": "test_user_01",
    "roles": ["USER"]
  }
}
```

---

## 4. 报告核心接口

### 4.1 健康检查

```http
GET /api/health
```

成功响应：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "status": "ok",
    "scope": "outline-and-docx"
  }
}
```

### 4.2 获取固定大纲

```http
POST /api/reports/outline
```

请求体：

```json
{
  "reportType": "SUMMER_PEAK_CHECK"
}
```

支持的 `reportType`：

| 枚举值 | 含义 |
|---|---|
| `SUMMER_PEAK_CHECK` | 迎峰度夏检查报告 |
| `COAL_INVENTORY_AUDIT` | 煤库存审计报告 |

成功响应：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": "node-id",
      "number": "1",
      "title": "检查概况",
      "level": 1,
      "promptHint": null,
      "children": []
    }
  ]
}
```

异常场景：

| 场景 | 预期 |
|---|---|
| `reportType` 缺失 | HTTP 400，提示 `reportType` |
| `reportType` 非法 | HTTP 400 |

### 4.3 AI 生成大纲

```http
POST /api/reports/outline/generate
```

请求体：

```json
{
  "reportType": "SUMMER_PEAK_CHECK",
  "subject": "2026 年迎峰度夏专项检查",
  "name": "2026 年迎峰度夏专项检查报告",
  "specialty": "电气",
  "powerPlant": "示例电厂",
  "reportYear": 2026,
  "context": {
    "unit": "1号机组"
  }
}
```

成功响应：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "tempId": "temp-id",
    "source": "AI",
    "expireSeconds": 1800,
    "outline": []
  }
}
```

字段说明：

| 字段 | 说明 |
|---|---|
| `tempId` | 临时大纲 ID，用于后续确认保存 |
| `source` | `AI` 表示 AI 返回，`LOCAL_TEMPLATE` 表示本地模板兜底 |
| `expireSeconds` | 临时大纲缓存过期秒数 |
| `outline` | 生成的大纲树 |

校验规则：

| 字段 | 规则 |
|---|---|
| `reportType` | 必填 |
| `subject` | 必填，不能为空白 |
| `reportYear` | 可选；传入时必须大于等于 2000 |

### 4.4 确认保存大纲

```http
POST /api/reports/outline/confirm
```

请求体：

```json
{
  "tempId": "temp-id",
  "reportType": "SUMMER_PEAK_CHECK",
  "subject": "2026 年迎峰度夏专项检查",
  "name": "2026 年迎峰度夏专项检查报告",
  "specialty": "电气",
  "powerPlant": "示例电厂",
  "reportYear": 2026,
  "outline": [
    {
      "number": "1",
      "title": "检查概况",
      "level": 1,
      "children": [
        {
          "number": "1.1",
          "title": "检查背景",
          "level": 2,
          "children": []
        }
      ]
    }
  ]
}
```

成功响应：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "reportId": "report-id",
    "status": "DRAFT",
    "outlineCount": 2,
    "outline": []
  }
}
```

校验规则：

| 字段 | 规则 |
|---|---|
| `reportType` | 必填 |
| `subject` | 必填 |
| `specialty` | 必填 |
| `powerPlant` | 必填 |
| `reportYear` | 必填，且大于等于 2000 |
| `outline` | 为空时必须传有效 `tempId` |

### 4.5 导出 DOCX

```http
POST /api/reports/{reportId}/export/docx
```

请求体：

```json
{
  "figureNumberingMode": "GLOBAL",
  "tableNumberingMode": "SECTION",
  "includeEmptySections": true
}
```

字段说明：

| 字段 | 说明 |
|---|---|
| `figureNumberingMode` | 图编号模式，支持 `GLOBAL`、`SECTION` |
| `tableNumberingMode` | 表编号模式，支持 `GLOBAL`、`SECTION` |
| `includeEmptySections` | 是否导出空章节标题 |

成功响应：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "fileId": "file-id",
    "reportId": "report-id",
    "fileName": "2026_年迎峰度夏专项检查报告_20260629120000.docx",
    "fileSize": 18342,
    "sha256": "64位sha256",
    "downloadUrl": "/api/reports/files/file-id/download"
  }
}
```

异常场景：

| 场景 | 预期 |
|---|---|
| `reportId` 不存在 | HTTP 400，提示报告不存在或已删除 |
| 编号模式非法 | HTTP 400 |

### 4.6 下载 DOCX

```http
GET /api/reports/files/{fileId}/download
```

成功响应：

```http
HTTP/1.1 200 OK
Content-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document
Content-Disposition: attachment; filename*=UTF-8''xxx.docx
```

响应体为 DOCX 二进制文件流。

异常场景：

| 场景 | 预期 |
|---|---|
| `fileId` 不存在 | HTTP 400，提示导出文件不存在 |
| 文件记录存在但本地文件丢失 | HTTP 400，提示导出文件已丢失 |

---

## 5. 待开发接口

以下接口已在接口清单中定义，但当前状态为后端 2 待开发。

### 5.1 SSE 流式生成

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/reports/{reportId}/sections/generate` | 启动章节流式生成 |
| GET | `/api/reports/{reportId}/sections/stream` | 建立 SSE 连接接收内容 |

SSE 事件：

| 事件 | 数据 |
|---|---|
| `progress` | `{"current":2,"total":5,"sectionTitle":"现场自查情况"}` |
| `content` | 文本片段 |
| `section_done` | `[SECTION_DONE]` |
| `done` | `[DONE]` |

### 5.2 章节内容管理

| 方法 | 路径 | 说明 |
|---|---|---|
| PUT | `/api/reports/{reportId}/sections/{sectionId}` | 保存章节内容 |
| GET | `/api/reports/{reportId}/sections/{sectionId}` | 获取单个章节 |
| GET | `/api/reports/{reportId}/sections` | 获取全部章节 |
| POST | `/api/reports/{reportId}/sections/{sectionId}/regenerate` | 单章节重新生成 |

### 5.3 历史记录管理

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/reports/history?page=&size=` | 分页查询历史 |
| GET | `/api/reports/history/{reportId}` | 获取历史详情 |
| DELETE | `/api/reports/history/{reportId}` | 删除历史记录 |

### 5.4 管理后台

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/admin/stats/overview` | 统计概览 |
| GET | `/api/admin/stats/trend` | 近 30 天趋势 |
| GET | `/api/admin/config/llm` | 获取 LLM 配置 |
| PUT | `/api/admin/config/llm` | 更新 LLM 配置 |
| GET | `/api/admin/templates` | 获取模板列表 |
| POST | `/api/admin/templates` | 上传新模板 |
| DELETE | `/api/admin/templates/{templateId}` | 删除模板 |

