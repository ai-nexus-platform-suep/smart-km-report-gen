# Java + Python 混合项目 — 三人 Git 协作防冲突方案

> 技术监督辅助平台 · 第 2 组 | 2026-06-28

---

## 一、三人分工与文件领土划分

```
人员 A：Java 平台 + Agent 工作流
人员 B：对话管理 + SSE 流式输出
人员 C：多轮上下文 + 思考过程 + 引用溯源
```

---

## 二、一人一亩三分地（文件级别硬隔离）

### 人员 A — Java 平台 + LangGraph 工作流

```
Java 服务（独占，别人不碰）：
├── qa-gateway/                       # 网关路由 + JWT 拦截
├── qa-auth/                          # 注册/登录/JWT 签发
└── qa-admin/                         # 配置管理 + 统计 + 日志

Python（独占）：
└── qa-agent/
    ├── graph/
    │   ├── state.py                  # AgentState 定义（A 先写，B/C 只读）
    │   ├── nodes.py                  # 各节点实现（A 独占）
    │   └── workflow.py               # StateGraph 组装 + 条件路由（A 独占）
    ├── model/
    │   ├── embedding.py              # 向量化模型（A 独占）
    │   └── reranker.py               # 重排序模型（A 独占）
    ├── core/
    │   └── config.py                 # 全局配置（A 先写，B/C 只读）
    └── client/
        └── java_client.py            # 调 Java 拉配置（A 独占）
```

### 人员 B — 对话管理 + SSE 流式

```
Python（独占）：
└── qa-agent/
    ├── api/
    │   ├── chat.py                   # POST /chat → SSE 流式（B 独占）
    │   │                              #   调用 graph/workflow 的 agent_graph.astream_events()
    │   └── conversation.py           # GET/POST/DELETE /conversations（B 独占）
    ├── db/
    │   ├── models.py                 # Conversation, Message ORM 定义（B 先写定，C 只读）
    │   └── repository.py             # 对话/消息 CRUD 方法（B 独占）
    └── alembic/                      # 数据库迁移脚本（B 独占）
        └── versions/
```

### 人员 C — 多轮上下文 + 思考过程 + 引用溯源

```
Python（独占）：
└── qa-agent/
    ├── core/
    │   └── constants.py              # SSE 事件类型枚举（C 独占）
    ├── graph/
    │   └── context.py                # 上下文管理：历史消息拼装 + Token 截断（C 独占）
    ├── service/
    │   ├── thinking_service.py       # 思考步骤生成逻辑（C 独占）
    │   └── citation_service.py       # 引用提取 + 标记插入（C 独占）
    └── tests/
        └── test_chat.py              # 集成测试（C 独占）
```

---

## 三、关键文件归属总览

| 文件 | 谁写 | 谁读 | 冲突风险 |
|------|:---:|:---:|:---:|
| `qa-agent/graph/state.py` | A | B, C | A 先写完定稿，B/C 后续只读 |
| `qa-agent/graph/nodes.py` | A | — | 无冲突 |
| `qa-agent/graph/workflow.py` | A | B | B 的 chat.py 只 import，不改 |
| `qa-agent/graph/context.py` | C | A | A 在 nodes.py 里调 C 的 context 方法 |
| `qa-agent/api/chat.py` | B | — | 无冲突 |
| `qa-agent/api/conversation.py` | B | — | 无冲突 |
| `qa-agent/db/models.py` | B | A, C | B 先写定，A/C 只读 |
| `qa-agent/db/repository.py` | B | — | 无冲突 |
| `qa-agent/service/thinking_service.py` | C | A | A 在 nodes.py 调 thinking |
| `qa-agent/service/citation_service.py` | C | A | A 在 generate_node 调 citation |
| `qa-agent/core/config.py` | A | B, C | A 先写定 |
| `qa-agent/core/constants.py` | C | B | B 的 chat.py 引用 |
| `qa-agent/main.py` | A | — | FastAPI 入口，A 一人维护 |
| Java 三个服务全部 | A | — | 无冲突 |
| `frontend/` | 前端负责 | — | 按页面拆分即可 |

> **核心原则：一个文件一个人写，绝不同时改。** A 和 C 通过**函数调用**协作（A 调 C 写好的函数），而不是改同一个文件。

