# km-platform — 知识管理子系统

> 知识管理组独立仓库，模块化单体：Spring Boot 2.6 + MyBatis + Vue 3  
> Git scope：**a** | 集成分支：**feat-a** | 后端端口：**8091**

## 目录结构

```
km-platform/
├── km-common/          # 公共 DTO、异常、常量
├── km-backend/         # 主服务（REST API + 业务编排）
├── km-frontend/        # Vue 3 管理后台 + 前台检索（各组前端另有独立分支）
├── km-ai-service/      # Python AI 服务占位（EPIC-04 管线，端口 8092）
├── docs/               # PRD、api-contract.yaml、目录规范
├── docker-compose.yml  # MySQL / Redis / RabbitMQ / MinIO / Milvus
├── scripts/            # 本地开发脚本
└── .github/workflows/  # CI（Maven verify）
```

详细约定见 [`docs/项目目录结构规范-知识管理组.md`](docs/项目目录结构规范-知识管理组.md)。

## 环境要求

| 工具 | 版本 |
|------|------|
| JDK | 8+ |
| Maven | 3.6+ |
| Node.js | 18+（仅前端本地开发） |
| Docker Desktop | 最新 |

## 快速启动

### 1. 启动中间件

```bash
docker compose up -d
```

等待 MySQL 健康检查通过（约 30 秒）。管理界面：

| 服务 | 地址 | 账号 |
|------|------|------|
| RabbitMQ | http://localhost:15672 | km / km123456 |
| MinIO Console | http://localhost:9001 | minioadmin / minioadmin |
| Milvus | localhost:19530 | — |

### 2. 启动后端

```bash
mvn clean install -DskipTests
cd km-backend
mvn spring-boot:run
```

验证：

```bash
curl http://localhost:8091/api/health
# {"code":0,"message":"ok","data":{"service":"km-backend","status":"UP","version":"1.0.0-SNAPSHOT"}}
```

Swagger UI: http://localhost:8091/swagger-ui/index.html

### 3. 启动前端（可选）

```bash
cd km-frontend
npm install
npm run dev
```

浏览器打开 http://localhost:5173

### 一键脚本

| 平台 | 命令 |
|------|------|
| Git Bash / WSL | `bash scripts/start-dev.sh` |
| Windows PowerShell | `.\scripts\verify-backend.ps1` 仅编译测试；`.\scripts\start-dev.ps1` 启动全栈 |

### 4. 环境变量

复制 `.env.example` 为 `.env`（勿提交），主要配置：

- `MYSQL_*` / `REDIS_*` / `RABBITMQ_*` / `MINIO_*` / `MILVUS_*`
- `SILICONFLOW_API_KEY`（嵌入/重排，Python 服务使用）
- `JWT_SECRET`

后端 Spring Boot 配置统一维护在 `km-backend/src/main/resources/application.yml`；本地数据库密码、MinIO、RabbitMQ 等连接信息都在该文件中修改。

MySQL 表结构由 Flyway 自动执行 `km-backend/src/main/resources/db/migration` 初始化；如果本地库先执行过 `docs/user.sql` 导致 KM 表缺失，可用 `docs/mysql-km-schema.sql` 手动修复。不要把 `schema-h2.sql` 用于 MySQL。

## 对外 API 契约

问答组 / 报告组对接请阅读 [`docs/api-contract.yaml`](docs/api-contract.yaml)。

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/health` | GET | 健康检查 |
| `/api/knowledge-bases` | GET/POST | 知识库列表 / 创建 |
| `/api/knowledge-bases/{id}` | GET/PUT/DELETE | 知识库详情 / 更新 / 删除 |
| `/api/knowledge-bases/{kbId}/documents` | GET/POST | 文档列表 / 上传 |
| `/api/search` | POST | 向量检索（问答 RAG 核心） |
| `/api/stats/summary` | GET | 统计概览 |
| `/api/admin/config/*` | GET/PUT | 嵌入 / 重排 / 解析器配置 |

内部 Python 契约见 [`docs/ai-service-contract.yaml`](docs/ai-service-contract.yaml)。

MVP 阶段未接入 JWT，创建知识库、上传文档、检索等需要用户上下文的接口必须传请求头 `userid`，后端会写入 `knowledge_base.owner_id` 或 `document.created_by`。

```bash
curl -X POST http://localhost:8091/api/knowledge-bases \
  -H "Content-Type: application/json" \
  -H "userid: 1" \
  -d '{"name":"demo-kb","description":"demo","docType":"通用文档","chunkStrategy":{"type":"fixed_size","chunkSize":512,"overlap":50},"searchStrategy":"vector_rerank"}'

curl -F "file=@sample.pdf" \
  -F "tags=demo" \
  -H "userid: 1" \
  http://localhost:8091/api/knowledge-bases/{kbId}/documents
```

## 组员开发流程

```bash
git checkout feat-a
git pull --rebase origin feat-a
git checkout -b feat/a-your-feature

# 后端改动：km-backend/src/main/java/com/km/...
# 提交信息：feat(a): 描述

git push -u origin feat/a-your-feature
# 在 GitHub 创建 PR → feat-a（组长审核）
```

合并前建议本地执行：

```bash
mvn -B clean verify
# 或 Windows: .\scripts\verify-backend.ps1
```

## EPIC-00 交付清单

| 项 | 状态 |
|----|------|
| 仓库目录结构（km-common / km-backend / km-frontend） | ✅ |
| Docker Compose（MySQL、Redis、RabbitMQ、MinIO、**Milvus**） | ✅ |
| Flyway 初始化 `V1__init_km.sql` + 配置种子 `V2__` | ✅ |
| 统一响应 `ApiResponse` + 全局异常处理 | ✅ |
| 健康检查 `GET /api/health` | ✅ |
| 对外契约 `docs/api-contract.yaml` | ✅ |
| 前端 Vue3 骨架（登录 / 检索 / 管理后台占位） | ✅ |
| `.env.example` + 本地开发脚本 | ✅ |
| `km-ai-service/` 目录占位 + README | ✅ |
| GitHub Actions CI（`mvn verify`） | ✅ |

## 相关文档

- [PRD-知识管理模块](docs/PRD-知识管理模块.md)
- [知识管理组中间件与服务方案](docs/知识管理组-中间件与服务方案.md)
- [大组协作说明](docs/README.md)
