# LLM 模型配置与 API 接口文档

> 分支：`feat/b-llm-config` | 日期：2026-06-29

---

## 一、架构概述

```
前端 (管理配置)              Python qa-agent (:8000)
      │                            │
      ▼                            ▼
Java qa-chat-service (:8082)      GET /internal/model-configs/default
      │                            │
      ▼                            │
┌─────────────┐                    │
│ model_config │ ◄─────────────────┘
│   (MySQL)    │   只读，拿解密后的 apiKey
└─────────────┘
```

- **Java**：配置的 CRUD、AES 加密存储、权限校验
- **Python**：只调 Java 接口拿配置，不碰数据库
- **Fallback**：Java 不可用时，Python 自动降级到 `.env`

---

## 二、数据库

### 新增表 `model_config`

```sql
CREATE TABLE model_config (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT       NOT NULL,
    provider          VARCHAR(50)  NOT NULL DEFAULT 'deepseek',
    base_url          VARCHAR(255) NOT NULL,
    model_name        VARCHAR(100) NOT NULL,
    api_key_encrypted VARCHAR(500) NOT NULL,
    scenario          VARCHAR(50)  NOT NULL DEFAULT 'chat',
    enabled           TINYINT(1)   NOT NULL DEFAULT 1,
    is_default        TINYINT(1)   NOT NULL DEFAULT 0,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_scenario (user_id, scenario),
    INDEX idx_user_default (user_id, scenario, is_default)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 已有表（不变）

| 表 | 说明 |
|----|------|
| `qa_session` | 会话元数据 |
| `qa_message` | 消息记录（含 thinking_steps、citations JSON） |

---

## 三、全部 API 接口

### Java — 模型配置管理 (`:8082`)

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/model-configs` | 配置列表（apiKey 脱敏 `sk-****abcd`） |
| `POST` | `/api/model-configs` | 新增配置（明文 → AES 加密落库） |
| `PUT` | `/api/model-configs/{id}` | 修改配置 |
| `DELETE` | `/api/model-configs/{id}` | 删除配置 |
| `POST` | `/api/model-configs/{id}/default` | 设为默认（事务切换同场景其他默认） |

### Java — Python 内部接口 (`:8082`)

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/internal/model-configs/default?userId=1&scenario=chat` | 获取默认配置（apiKey 已解密明文） |

### Python — 智能体 (`:8000`)

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/chat` | SSE 流式对话 |
| `POST` | `/api/chat/test` | 非流式测试（不持久化） |
| `GET` | `/api/conversations` | 会话列表（分页） |
| `POST` | `/api/conversations` | 创建会话 |
| `GET` | `/api/conversations/{id}/messages` | 会话消息（分页） |
| `PATCH` | `/api/conversations/{id}` | 修改会话标题 |
| `DELETE` | `/api/conversations/{id}` | 软删除会话 |

---

## 四、配置加载优先级

```
qa-agent 请求进入
  → java_client.fetch_llm_config()
    → 尝试 Java GET /internal/model-configs/default
      → 成功 → 用 Java 返回的配置
      → 失败 → Fallback 到 .env 本地配置
  → 注入 agent_input.model_config
  → nodes.py 从 state 取配置调 LLM
```

---

## 五、安全设计

| 措施 | 说明 |
|------|------|
| 加密算法 | AES-256，密钥通过 SHA-256 派生 |
| 存储 | `api_key_encrypted` 存 Base64 密文，不存明文 |
| 脱敏 | 前端列表只显示 `sk-****abcd` |
| 日志 | 不打印完整 apiKey |
| 环境隔离 | 密钥配置在 `application.yaml` 的 `model-config.aes-key` |

---

## 六、如何测试

### 前提条件

```sql
CREATE DATABASE smart_km_report_gen CHARACTER SET utf8mb4;

USE smart_km_report_gen;
SOURCE qa-chat-service/src/main/resources/db/conversation.sql;
SOURCE qa-chat-service/src/main/resources/db/model_config.sql;
SOURCE qa-chat-service/src/main/resources/db/mock_data.sql;
```

```powershell
$env:MYSQL_PASSWORD="你的MySQL密码"
$env:PYTHONPATH="qa-agent"
```

### 启动服务

```bash
# 终端 1：Java
mvn spring-boot:run -pl qa-chat-service

# 终端 2：Python
python qa-agent/main.py
```

### IDEA HTTP Client 测试

打开 `qa-chat-service/src/test/http/test-model-config.http`，按顺序执行：

```
1. GET  /api/model-configs                   确认列表为空
2. POST /api/model-configs                   新增 DeepSeek 配置
3. GET  /api/model-configs                   确认列表有数据，apiKey 脱敏
4. GET  /internal/model-configs/default      确认 apiKey 已解密明文
5. POST /api/conversations                   创建会话，记下 id
6. 更新 http-client.env.json 的 conversation_id
7. POST /api/chat                            流式对话，观察 SSE 事件
8. GET  /api/conversations/{id}/messages     查看历史消息
```

### 单元测试

```bash
# Python（32 tests）
cd smart-km-report-gen
PYTHONPATH="qa-agent" python -m pytest qa-agent/tests/test_chat.py -v

# Java 编译
mvn compile -pl qa-chat-service
```

---

## 七、修改文件清单

### Java 新增（12 个文件）

```
qa-chat-service/src/main/java/com/myenglish/qachat/
├── entity/ModelConfig.java
├── mapper/ModelConfigMapper.java
├── util/AesUtil.java
├── service/ModelConfigService.java
├── service/impl/ModelConfigServiceImpl.java
├── controller/ModelConfigController.java
├── controller/InternalModelConfigController.java
├── dto/req/SaveModelConfigReq.java
├── dto/resp/ModelConfigVO.java
├── dto/resp/ModelConfigInternalVO.java
├── resources/db/model_config.sql
└── test/http/test-model-config.http
```

### 已有文件修改（9 个）

| 文件 | 改动 |
|------|------|
| `qa-chat-service/.../application.yaml` | DB 名、密码环境变量、aes-key |
| `qa-agent/core/config.py` | DB 名更新 |
| `qa-agent/.env.example` | DB 名更新、格式整理 |
| `qa-agent/graph/state.py` | 新增 `model_config` 字段 |
| `qa-agent/graph/nodes.py` | LLM 调用从 state 取配置 |
| `qa-agent/api/chat.py` | 请求时拉取 model_config |
| `qa-agent/client/java_client.py` | 改调新内部接口 |
| `qa-agent/tests/test_chat.py` | 适配新字段 |
| `.gitignore` | 新增排除规则 |
