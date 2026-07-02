# 技术监督辅助平台 — 完整 API 接口文档

> **Base URL（对外统一入口）**: `http://localhost:8080`（Gateway）  
> **版本**: RBAC + 用户上下文透传版  
> **最后更新**: 2026-07-01

---

## 目录

1. [通用约定](#1-通用约定)
2. [认证与用户（auth）](#2-认证与用户auth)
3. [RBAC 管理（auth）](#3-rbac-管理auth)
4. [智能问答会话（qa-agent / Python）](#4-智能问答会话qa-agent--python)
5. [模型配置与统计（qa-chat-service / Java）](#5-模型配置与统计qa-chat-service--java)
6. [Gateway 路由一览](#6-gateway-路由一览)
7. [权限码速查](#7-权限码速查)
8. [初始测试账号](#8-初始测试账号)

---

## 1. 通用约定

### 1.1 请求头

| Header | 必填 | 说明 |
|--------|------|------|
| `Authorization` | 受保护接口必填 | `Bearer <accessToken>`，由登录接口获取 |
| `Content-Type` | 有 Body 时 | `application/json` |

**客户端不需要、也不应手动传 `user-id`**。Gateway 验签 JWT 后向下游注入：

| 内部 Header（Gateway → 微服务） | 说明 |
|--------------------------------|------|
| `user-id` | 当前用户 ID |
| `username` | 登录名 |
| `roles` | 角色列表，逗号分隔 |
| `permissions` | 权限码列表，逗号分隔 |

Java 侧通过 `UserContextHolder.getUserId()` 获取；Python（qa-agent）通过 `get_user_id()` 获取。

### 1.2 响应格式

**Java 服务（auth、qa-chat-service）**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": { }
}
```

**Python 服务（qa-agent）**

```json
{
  "code": 0,
  "message": "success",
  "data": { }
}
```

### 1.3 常见错误码

| code | 含义 |
|------|------|
| `200` | Java 成功 |
| `0` | Python 成功 |
| `401` | 未登录 / Token 无效 |
| `403` | 无权限 |
| `1001` | 用户不存在 |
| `1002` | 用户已存在 |
| `1003` | 密码错误 |
| `1005` | Token 过期 |
| `1006` | 账号禁用 |
| `7001` | 参数校验失败 |
| `7006` | 数据已存在（如邮箱重复） |

### 1.4 Gateway 鉴权策略

| 路径模式 | Gateway JWT 校验 |
|----------|------------------|
| `/api/auth/**` | **白名单**，不校验（auth 服务自行校验 Token） |
| `/api/conversations/**`、`/api/chat/**` | **必须** Token |
| `/api/model-configs/**`、`/api/stats/qa/**` | **必须** Token |
| 其他已注册路由 | **必须** Token |

---

## 2. 认证与用户（auth）

> 服务端口：8081（经 Gateway 访问 `/api/auth/**`）

### 2.1 公开接口（无需 Token）

#### POST `/api/auth/register` — 注册

**Body**

```json
{
  "username": "newuser",
  "password": "123456"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | string | 是 | 3–50 字符 |
| password | string | 是 | 6–100 字符 |

**响应**: HTTP `201`，`code=200`，新用户默认绑定 `ROLE_USER`。

---

#### POST `/api/auth/login` — 登录

**Body**

```json
{
  "username": "user",
  "password": "admin123"
}
```

**响应 data**

```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "username": "user",
  "roles": ["ROLE_USER"],
  "permissions": ["chat:conversation:use", "..."]
}
```

---

#### POST `/api/auth/refresh` — 刷新 Token

**Body**

```json
{
  "refreshToken": "eyJ..."
}
```

**响应**: 同登录，返回新的 access + refresh（旧 refresh 作废）。

---

### 2.2 当前用户自助（需 Token，无额外权限码）

#### GET `/api/auth/me` — 当前用户摘要

**响应 data 示例**

```json
{
  "id": 3,
  "username": "user",
  "nickname": "user",
  "realName": null,
  "email": null,
  "phone": null,
  "avatar": null,
  "gender": 0,
  "roles": ["ROLE_USER"],
  "permissions": ["chat:conversation:use", "..."]
}
```

---

#### GET `/api/auth/me/profile` — 完整资料

**响应 data**: `UserVO`（含 `id、username、nickname、realName、email、phone、avatar、gender、enabled、roles、lastLoginAt、createdAt`）

---

#### PUT `/api/auth/me/profile` — 编辑自己的资料

**Body**（字段均可选，传 null 可清空 email/phone）

```json
{
  "nickname": "小明",
  "realName": "张三",
  "email": "user@example.com",
  "phone": "13800138000",
  "avatar": "https://example.com/avatar.png",
  "gender": 1
}
```

| gender | 说明 |
|--------|------|
| 0 | 未知 |
| 1 | 男 |
| 2 | 女 |

---

#### PUT `/api/auth/me/password` — 修改密码

**Body**

```json
{
  "oldPassword": "admin123",
  "newPassword": "newpass123"
}
```

成功后全部 Refresh Token 失效，需重新登录。

---

#### POST `/api/auth/me/logout` — 登出

撤销当前用户全部 Refresh Token。Access Token 在过期前仍 technically 有效，客户端应丢弃本地 Token。

---

#### GET `/api/auth/me/permissions` — 我的权限码

**响应 data**: `["chat:conversation:use", "chat:model:view", ...]`

---

#### GET `/api/auth/me/menus` — 我的菜单树

**响应 data**: 树形 `MenuVO[]`（前端动态路由用）。

---

### 2.3 用户管理（管理员）

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/auth/users` | `auth:user:list` | 用户列表 |
| GET | `/api/auth/users/{id}` | `auth:user:list` | 用户详情 |
| POST | `/api/auth/users` | `auth:user:create` | 新增用户 |
| PUT | `/api/auth/users/{id}` | `auth:user:update` | 编辑用户（含启用/禁用、重置密码） |
| DELETE | `/api/auth/users/{id}` | `auth:user:delete` | 逻辑删除 |
| POST | `/api/auth/users/{id}/roles` | `auth:role:assign` | 分配角色 |

**POST `/api/auth/users` Body**

```json
{
  "username": "tom",
  "password": "123456",
  "nickname": "Tom",
  "realName": "汤姆",
  "email": "tom@example.com",
  "phone": "13900000000"
}
```

**PUT `/api/auth/users/{id}` Body**（管理员版，可含 `enabled`、`password`）

```json
{
  "nickname": "Tom",
  "realName": "汤姆",
  "email": "tom@example.com",
  "phone": "13900000000",
  "avatar": null,
  "gender": 1,
  "enabled": true,
  "password": "可选，重置密码"
}
```

**POST `/api/auth/users/{id}/roles` Body**

```json
{
  "roleCodes": ["ROLE_USER", "ROLE_ADMIN"]
}
```

---

## 3. RBAC 管理（auth）

### 3.1 角色

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/auth/roles` | `auth:role:list` 或 `auth:role:manage` | 角色列表 |
| GET | `/api/auth/roles/{id}` | `auth:role:manage` | 角色详情 |
| POST | `/api/auth/roles` | `auth:role:manage` | 新增角色 |
| PUT | `/api/auth/roles/{id}` | `auth:role:manage` | 编辑角色 |
| DELETE | `/api/auth/roles/{id}` | `auth:role:manage` | 删除角色 |
| POST | `/api/auth/roles/{id}/permissions` | `auth:role:manage` | 角色绑定权限 |
| POST | `/api/auth/roles/{id}/menus` | `auth:role:manage` | 角色绑定菜单 |

**SaveRoleRequest**

```json
{
  "roleCode": "ROLE_CUSTOM",
  "roleName": "自定义角色",
  "description": "说明",
  "enabled": true,
  "sortOrder": 10
}
```

**RoleGrantRequest**

```json
{
  "ids": [1, 2, 3]
}
```

### 3.2 菜单

| 方法 | 路径 | 权限 |
|------|------|------|
| GET | `/api/auth/menus` | `auth:menu:list` 或 `auth:menu:manage` |
| POST | `/api/auth/menus` | `auth:menu:manage` |
| PUT | `/api/auth/menus/{id}` | `auth:menu:manage` |
| DELETE | `/api/auth/menus/{id}` | `auth:menu:manage` |

### 3.3 权限配置

| 方法 | 路径 | 权限 |
|------|------|------|
| GET | `/api/auth/permissions` | `auth:permission:manage` |

### 3.4 操作日志

| 方法 | 路径 | 权限 | Query |
|------|------|------|-------|
| GET | `/api/auth/logs` | `auth:log:list` | `pageNum`、`pageSize`、`username`、`module` |

---

## 4. 智能问答会话（qa-agent / Python）

> 服务端口：8000  
> Gateway 路径：`/api/conversations/**`、`/api/chat/**`  
> **所有接口需 Gateway Token**；**请求 Body 不含 userId**，用户身份由 Gateway 注入的 `user-id` 自动获取。

### 4.1 会话管理

#### GET `/api/conversations` — 会话列表

**Query**

| 参数 | 类型 | 默认 | 说明 |
|------|------|------|------|
| page | int | 1 | 页码 |
| size | int | 20 | 每页条数（最大 100） |

**响应 data**

```json
{
  "items": [
    {
      "session_id": "727435017566818304",
      "title": "新对话",
      "message_count": 0,
      "last_message_at": "2026-07-01T12:00:00",
      "created_at": "2026-07-01T12:00:00"
    }
  ],
  "total": 1,
  "page": 1,
  "size": 20
}
```

> 注：`session_id` 在 JSON 中为字符串（雪花 ID）。

---

#### POST `/api/conversations` — 创建会话

**Body**（可选）

```json
{
  "title": "我的对话"
}
```

---

#### PATCH `/api/conversations/{conversation_id}` — 修改标题

**Body**

```json
{
  "title": "新标题"
}
```

---

#### DELETE `/api/conversations/{conversation_id}` — 删除会话

逻辑删除会话及消息。仅能操作**自己的**会话。

---

#### GET `/api/conversations/{conversation_id}/messages` — 消息列表

**Query**: `page`（默认 1）、`size`（默认 50，最大 200）

**响应 data**

```json
{
  "session_id": "727435017566818304",
  "title": "新对话",
  "total": 2,
  "messages": [
    {
      "message_id": "123",
      "seq": 1,
      "role": "user",
      "content": "你好",
      "intent_type": null,
      "thinking_steps": null,
      "citations": null,
      "generate_status": 1,
      "token_usage": null,
      "created_at": "...",
      "updated_at": "..."
    }
  ]
}
```

---

### 4.2 聊天

#### POST `/api/chat` — SSE 流式对话

**Body**

```json
{
  "conversation_id": 727435017566818304,
  "question": "什么是电力技术监督？",
  "selected_kb_ids": []
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| conversation_id | int | 是 | 会话 ID |
| question | string | 是 | 用户问题 |
| selected_kb_ids | int[] | 否 | 选中的知识库 ID |

> **不含 `user_id`**。服务端从 `UserContext` 自动获取，并校验会话归属。

**响应**: `text/event-stream`，事件类型包括 `thinking`、`message`、`citation`、`error`、`done`。

---

#### POST `/api/chat/test` — 非流式测试（调试用）

**Body**

```json
{
  "question": "什么是电力技术监督？",
  "selected_kb_ids": [],
  "messages": [
    { "role": "user", "content": "上一轮问题" },
    { "role": "assistant", "content": "上一轮回答" }
  ]
}
```

**响应 data**

```json
{
  "intent": "CHAT",
  "mode": "...",
  "needs_clarification": false,
  "classification_source": "...",
  "retrieved_docs_count": 0,
  "thinking_steps": [],
  "citations": [],
  "final_response": "..."
}
```

---

## 5. 模型配置与统计（qa-chat-service / Java）

> 服务端口：8090  
> 用户 ID 由 `UserContextHolder` 自动获取，**接口不传 userId**。

### 5.1 模型配置 `/api/model-configs`

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/model-configs` | `chat:model:view` 或 `chat:model:manage` | 当前用户的配置列表 |
| POST | `/api/model-configs` | `chat:model:manage` | 新增配置 |
| PUT | `/api/model-configs/{id}` | `chat:model:manage` | 修改（仅自己的） |
| DELETE | `/api/model-configs/{id}` | `chat:model:manage` | 删除（仅自己的） |
| POST | `/api/model-configs/{id}/default` | `chat:model:manage` | 设为默认 |

**SaveModelConfigReq**

```json
{
  "provider": "deepseek",
  "baseUrl": "https://api.deepseek.com",
  "modelName": "deepseek-chat",
  "apiKey": "sk-xxx",
  "scenario": "chat",
  "enabled": 1
}
```

**ModelConfigVO**（响应）: `id、userId、provider、baseUrl、modelName、apiKeyMasked、scenario、enabled、isDefault、createdAt、updatedAt`

---

### 5.2 QA 统计 `/api/stats/qa`

#### GET `/api/stats/qa/overview` — 统计概览

**权限**: `chat:stats:view`

**响应 data**

```json
{
  "totalCount": 128,
  "trend": [
    { "date": "2026-06-01", "count": 5 },
    { "date": "2026-06-02", "count": 0 }
  ]
}
```

---

### 5.3 内部接口（不经过 Gateway，仅服务间调用）

#### GET `/internal/model-configs/default`

**Query**: `userId`、`scenario`（默认 `chat`）

供 qa-agent 拉取用户默认 LLM 配置。由 qa-agent 内部 HTTP 调用，**不对前端暴露**。

---

## 6. Gateway 路由一览

| 路由 ID | 目标 | 路径 |
|---------|------|------|
| auth | `localhost:8081` | `/api/auth/**` |
| qa-agent | `localhost:8000` | `/api/conversations/**`、`/api/chat/**` |
| qa-chat-service | `localhost:8090` | `/api/model-configs/**`、`/api/stats/qa/**` |
| report | `localhost:8092` | `/api/reports/**`、`/api/admin/**`、`/api/health` |
| km | `localhost:8091` | `/api/knowledge-bases/**`、`/api/documents/**`、`/api/search`、`/api/stats/summary` |

---

## 7. 权限码速查

### 系统管理（auth）

| 权限码 | 说明 |
|--------|------|
| `auth:user:list` | 用户列表/详情 |
| `auth:user:create` | 新增用户 |
| `auth:user:update` | 编辑用户 |
| `auth:user:delete` | 删除用户 |
| `auth:role:assign` | 分配角色 |
| `auth:role:list` | 角色列表 |
| `auth:role:manage` | 角色 CRUD + 授权 |
| `auth:menu:list` | 菜单列表 |
| `auth:menu:manage` | 菜单 CRUD |
| `auth:permission:manage` | 权限配置 |
| `auth:log:list` | 操作日志 |

### 智能问答（chat）

| 权限码 | 说明 |
|--------|------|
| `chat:conversation:use` | 会话/聊天 |
| `chat:model:view` | 查看模型配置 |
| `chat:model:manage` | 管理模型配置 |
| `chat:stats:view` | 查看 QA 统计 |

### 三角色默认分配

| 角色 | 能力概要 |
|------|----------|
| `ROLE_SUPER_ADMIN` | 全部权限 |
| `ROLE_ADMIN` | 用户管理 + 全业务，**不含** `auth:permission:manage` |
| `ROLE_USER` | 会话、模型只读、统计查看 |

---

## 8. 初始测试账号

| 用户名 | 密码 | 角色 | user-id |
|--------|------|------|---------|
| superadmin | admin123 | 超级管理员 | 1 |
| admin | admin123 | 管理员 | 2 |
| user | admin123 | 普通用户 | 3 |

---

## 附录：典型调用流程

```
1. POST /api/auth/login          → 获取 accessToken
2. GET  /api/auth/me/profile     → 查看资料（Header: Authorization）
3. POST /api/conversations       → 创建会话（Gateway 注入 user-id）
4. POST /api/chat                → SSE 对话（Body 无 user_id）
5. GET  /api/model-configs       → 模型列表（Java UserContextHolder）
6. PUT  /api/auth/me/profile     → 修改昵称
7. POST /api/auth/me/logout      → 登出
```

---

## 附录：Python userId 获取机制

```
客户端 Authorization: Bearer <JWT>
        ↓
Gateway 验签 → Header: user-id=3, username=user, roles=..., permissions=...
        ↓
qa-agent UserContextMiddleware → get_user_id() / require_user_id()
        ↓
业务代码 / Repository 按 user_id 过滤数据
```

**客户端与前端请求体、Query 中均不需要传 `userId` / `user_id`。**
