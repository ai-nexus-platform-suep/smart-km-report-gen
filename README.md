# km-platform — 知识管理子系统

> 知识管理组独立仓库，模块化单体：Spring Boot 2.6 + MyBatis + Vue 3  
> Git scope：**a** | 集成分支：**feat-a** | 后端端口：**8091**

## 目录结构

```
km-platform/
├── km-common/          # 公共 DTO、异常、常量
├── km-backend/         # 主服务（REST API + 文档处理管线）
├── km-frontend/        # Vue 3 管理后台 + 前台检索
├── docs/               # PRD、api-contract.yaml、目录规范
├── docker-compose.yml  # MySQL / Redis / RabbitMQ / MinIO
└── scripts/            # 本地开发脚本
```

详细约定见 [`docs/项目目录结构规范-知识管理组.md`](docs/项目目录结构规范-知识管理组.md)。

## 环境要求

| 工具 | 版本 |
|------|------|
| JDK | 8+ |
| Maven | 3.6+ |
| Node.js | 18+ |
| Docker Desktop | 最新 |

## 快速启动

### 1. 启动中间件

```bash
docker-compose up -d
```

等待 MySQL 健康检查通过（约 30 秒）。管理界面：

- RabbitMQ：http://localhost:15672（km / km123456）
- MinIO Console：http://localhost:9001（minioadmin / minioadmin）

### 2. 启动后端

```bash
mvn clean install -DskipTests
cd km-backend
mvn spring-boot:run
```

验证：

```bash
curl http://localhost:8091/api/v1/health
# {"code":0,"message":"ok","data":{"service":"km-backend","status":"UP","version":"1.0.0-SNAPSHOT"}}
```

### 3. 启动前端

```bash
cd km-frontend
npm install
npm run dev
```

浏览器打开 http://localhost:5173 ，检索页会显示后端连接状态。

### 一键脚本（Git Bash / WSL）

```bash
bash scripts/start-dev.sh
```

## 对外 API 契约

问答组对接请阅读 [`docs/api-contract.yaml`](docs/api-contract.yaml)。

核心接口：

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/v1/health` | GET | 健康检查 |
| `/api/v1/search` | POST | 向量检索（问答 RAG 依赖） |
| `/api/v1/knowledge-bases` | GET | 知识库列表 |

脚手架阶段 `/api/v1/search` 返回空结果，EPIC-06 接入真实检索。

## 环境变量

复制 `.env.example` 为 `.env`（勿提交），主要配置：

- `MYSQL_*` / `REDIS_*` / `RABBITMQ_*` / `MINIO_*`
- `SILICONFLOW_API_KEY`（嵌入模型，EPIC-06 使用）
- `JWT_SECRET`

## 组员开发流程

```bash
git checkout feat-a
git pull --rebase
git checkout -b feat/a-your-feature

# 改动示例：
#   km-backend/src/main/java/com/km/controller/knowledge/
#   km-frontend/src/views/admin/knowledge/

git commit -m "feat(a): 你的功能描述"
# MR → feat-a（组长审核后合入 develop）
```

## EPIC-00 交付清单

- [x] 仓库目录结构（km-common / km-backend / km-frontend）
- [x] Docker Compose（MySQL、Redis、RabbitMQ、MinIO）
- [x] Flyway 初始化表结构 `V1__init_km.sql`
- [x] 统一响应 `ApiResponse` + 全局异常处理
- [x] 健康检查 `/api/v1/health`
- [x] 对外契约 `docs/api-contract.yaml`
- [x] 前端 Vue3 骨架（登录 / 检索 / 管理后台占位页）

## 相关文档

- [PRD-知识管理模块](docs/PRD-知识管理模块.md)
- [大组 Git 协作规范](docs/README.md)
