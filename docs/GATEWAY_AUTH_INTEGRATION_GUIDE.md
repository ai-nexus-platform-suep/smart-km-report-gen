# Gateway + Auth 集成指南

> 分支：`develop-jwt` | 日期：2026-07-01 | 维护：QA 组

---

## 一、修改内容

### 架构变化

```
改前：gateway (:8088) 内含 Auth（C 组代码）
改后：gateway (:8080) 纯路由 + auth (:8081) 独立认证服务
```

### 新增 auth 模块 (:8081)

| 文件 | 说明 |
|------|------|
| `auth/pom.xml` | Spring Boot 3.3.6 + Web + MyBatis-Plus + JWT |
| `auth/src/main/java/com/qa/auth/AuthApplication.java` | 启动类 |
| `auth/src/main/java/com/qa/auth/controller/AuthController.java` | 注册/登录/刷新/me |
| `auth/src/main/java/com/qa/auth/service/UserService.java` | BCrypt 用户管理 |
| `auth/src/main/java/com/qa/auth/service/RefreshTokenService.java` | Refresh Token SHA256 |
| `auth/src/main/java/com/qa/auth/entity/SysUserEntity.java` | sys_user 表 |
| `auth/src/main/java/com/qa/auth/util/JwtTokenProvider.java` | JWT 签发+验签（含 userId） |
| `auth/src/main/resources/application.yaml` | 端口 8081、数据库 auth_db |
| `auth/src/main/resources/db/auth.sql` | 建表脚本 |

### 重构 gateway (:8080)

| 改动 | 说明 |
|------|------|
| `application.properties` → `application.yaml` | 解决编码问题 |
| 去掉 Nacos/MySQL/MyBatis/Redis | 纯路由，不管理业务数据 |
| `com.powerreport.gateway` → `com.qa.gateway` | 包名统一 |
| JWT Filter 增加 X-User-Id Header | 下游服务可用 userId |
| 补齐三模块路由 | QA/KM/Report 全覆盖 |
| `gateway/src/test/http/` | 集成测试文件 |

### 修改的其他文件

| 文件 | 说明 |
|------|------|
| `pom.xml` | Spring Boot 3.3.6，Spring Cloud 2023.0.5，加入 gateway + auth 模块 |
| `qa-common` → `common` | 目录重命名 |

---

## 二、服务端口

| 服务 | 端口 | 说明 |
|------|:---:|------|
| gateway | 8080 | 统一入口，JWT 验签 + 路由转发 |
| auth | 8081 | 注册/登录/JWT 签发 |
| qa-chat-service | 8082 | QA 模型配置 + 统计 |
| qa-agent | 8000 | QA Python 智能体 |
| km-backend | 8091 | 知识管理 |
| report-backend | 8092 | 报告生成 |

---

## 三、全部 API 接口

### Auth 认证 (:8081)

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|:---:|------|
| POST | `/api/auth/register` | 无 | 用户注册 |
| POST | `/api/auth/login` | 无 | 登录，返回 JWT |
| POST | `/api/auth/refresh` | 无 | 刷新 Token |
| GET | `/api/auth/me` | JWT | 当前用户信息 |

### QA 模型配置 (:8082)

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|:---:|------|
| GET | `/api/model-configs` | JWT | 配置列表（apiKey 脱敏） |
| POST | `/api/model-configs` | JWT | 新增配置 |
| PUT | `/api/model-configs/{id}` | JWT | 修改配置 |
| DELETE | `/api/model-configs/{id}` | JWT | 删除配置 |
| POST | `/api/model-configs/{id}/default` | JWT | 设为默认 |

### QA 智能体 (:8000)

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|:---:|------|
| POST | `/api/chat` | JWT | SSE 流式对话 |
| POST | `/api/chat/test` | JWT | 非流式测试 |
| GET | `/api/conversations` | JWT | 会话列表 |
| POST | `/api/conversations` | JWT | 创建会话 |
| GET | `/api/conversations/{id}/messages` | JWT | 历史消息 |
| PATCH | `/api/conversations/{id}` | JWT | 改标题 |
| DELETE | `/api/conversations/{id}` | JWT | 软删除 |

### Gateway 路由表

| 路径前缀 | 转发到 | 说明 |
|------|------|------|
| `/api/auth/**` | auth :8081 | 白名单，不鉴权 |
| `/api/model-configs/**` | qa-chat-service :8082 | |
| `/api/stats/qa/**` | qa-chat-service :8082 | |
| `/api/conversations/**` | qa-agent :8000 | |
| `/api/chat/**` | qa-agent :8000 | |
| `/api/reports/**` | report-backend :8092 | |
| `/api/admin/**` | report-backend :8092 | |
| `/api/health` | report-backend :8092 | 白名单 |
| `/api/knowledge-bases/**` | km-backend :8091 | |
| `/api/documents/**` | km-backend :8091 | |
| `/api/search` | km-backend :8091 | |
| `/api/stats/summary` | km-backend :8091 | |

---

## 四、如何测试（供其他模块）

### 前置条件

1. Gateway 已启动：`mvn spring-boot:run -pl gateway`
2. Auth 已启动：`mvn spring-boot:run -pl auth`
3. 已执行建表：`source auth/src/main/resources/db/auth.sql`
4. IDEA 打开 `gateway/src/test/http/test-all.http`，环境选 `dev`

### 注册一个测试账号

```powershell
$body = @{username="kmtest";password="123456"} | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8080/api/auth/register" -Method Post -Body $body -ContentType "application/json"
```

### 获取 Token

```powershell
$body = @{username="kmtest";password="123456"} | ConvertTo-Json
$resp = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -Body $body -ContentType "application/json"
$token = $resp.data.accessToken
```

### 用 Token 访问你的服务

```powershell
# KM 组访问自己的接口
Invoke-RestMethod -Uri "http://localhost:8080/api/search" `
  -Method Post `
  -Headers @{Authorization="Bearer $token"} `
  -Body '{"query":"测试"}' `
  -ContentType "application/json"

# Report 组访问自己的接口
Invoke-RestMethod -Uri "http://localhost:8080/api/reports/history" `
  -Headers @{Authorization="Bearer $token"}

# QA 组访问模型配置
Invoke-RestMethod -Uri "http://localhost:8080/api/model-configs" `
  -Headers @{Authorization="Bearer $token"}
```

### 验证清单

| 步骤 | 预期结果 |
|------|------|
| 注册 | 200 或 1002 |
| 登录 | `{code:200, data:{accessToken, refreshToken, username, roles, expiresIn}}` |
| 无 token 访问 | 401 `{"code":401,"message":"缺少认证信息，请先登录"}` |
| 带 token 访问 | 转发到对应后端服务 |

---

## 五、各模块接入注意事项

1. **所有请求统一走 `http://localhost:8080`**，不要直连各自服务端口
2. **Header 传递**：Gateway 验签后向下游注入 `X-User-Id`、`X-Username`、`X-Roles`
3. **白名单**：`/api/auth/**` 和 `/api/health` 不需要 token
4. **JWT 密钥**：所有服务共用一个 secret，配置在 `application.yaml` 的 `app.jwt.secret`
5. **内部接口**：`/internal/**` 不走 Gateway，服务间直连
