# 模型配置后端开发梳理

## 1. 目标

本文档面向负责模型配置功能的后端同学，说明模型配置在 Java 和 Python 智能体服务之间如何分工、如何传递、以及各自需要开发哪些内容。

本项目整体建议采用：Java 作为主业务系统和任务编排方，Python 作为智能体执行服务。模型配置属于用户级业务配置，应由 Java 统一管理，Python 只在执行智能体任务时使用 Java 传入的运行配置。

## 2. 推荐主流程

```text
前端 -> Java
Java 查 model_config 表
Java 解密 apiKey
Java 组装任务参数 + modelConfig
Java 调 Python
Python 直接用 modelConfig 执行
Python 返回结果
```

这套流程的关键点是：Python 不需要为了模型配置再反向调用 Java，也不需要直接读取模型配置表。Java 在调用 Python 前已经完成配置查询、权限校验和 apiKey 解密，Python 只消费请求体里的 `modelConfig`。

## 3. Java 部分怎么开发

### 3.1 数据库表

建议新增一张用户维度的模型配置表：`model_config`。

推荐字段：

| 字段 | 说明 |
|---|---|
| `id` | 主键 |
| `user_id` | 所属用户 ID |
| `provider` | 模型供应商，例如 `deepseek`、`qwen`、`openai` |
| `base_url` | 模型接口地址，支持 OpenAI-compatible API |
| `model_name` | 模型名称，例如 `deepseek-chat` |
| `api_key_encrypted` | 加密后的 API Key |
| `temperature` | 生成随机性 |
| `max_tokens` | 最大输出 token 数 |
| `timeout_seconds` | 调用超时时间 |
| `retry_count` | 重试次数 |
| `scenario` | 使用场景，例如 `report_generate`、`chat` |
| `enabled` | 是否启用 |
| `is_default` | 是否为该用户该场景默认配置 |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

如果当前只做课程设计或最小可用版，可以先保留核心字段：`user_id`、`provider`、`base_url`、`model_name`、`api_key_encrypted`、`scenario`、`enabled`、`is_default`。

### 3.2 Java 接口

Java 需要提供前端使用的配置管理接口：

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/api/model-configs` | 查询当前用户的模型配置列表 |
| `POST` | `/api/model-configs` | 新增模型配置 |
| `PUT` | `/api/model-configs/{id}` | 修改模型配置 |
| `DELETE` | `/api/model-configs/{id}` | 删除模型配置 |
| `POST` | `/api/model-configs/{id}/default` | 设置默认模型配置 |
| `POST` | `/api/model-configs/{id}/test` | 测试模型配置是否可用 |

这些接口必须基于当前登录用户过滤数据，不能允许用户操作其他用户的配置。

### 3.3 API Key 处理

API Key 不允许明文落库。推荐做法：

- 前端提交明文 `apiKey` 给 Java。
- Java 使用 AES 等方式加密后写入 `api_key_encrypted`。
- 加密密钥放在后端环境变量或配置文件中，不写死在代码里。
- 查询配置列表时，Java 只返回脱敏值，例如 `sk-****abcd`。
- Java 调 Python 前才解密 API Key，并放入请求体里的 `modelConfig`。
- Java 和 Python 日志都不能打印完整 API Key。

### 3.4 默认配置逻辑

建议按 `user_id + scenario` 控制默认模型。一个用户在同一个场景下只能有一个默认配置。

设置默认配置时，建议在事务里完成：

1. 校验配置属于当前用户。
2. 将该用户该场景下其他配置的 `is_default` 改为 `false`。
3. 将目标配置的 `is_default` 改为 `true`。
4. 提交事务。

### 3.5 Java 调 Python 的请求体

Java 在发起智能体任务时，需要把模型配置和任务参数一起传给 Python。

示例：

```json
{
  "taskId": 1001,
  "userId": 12,
  "scenario": "report_generate",
  "inputFileIds": [1, 2, 3],
  "modelConfig": {
    "provider": "deepseek",
    "baseUrl": "https://api.deepseek.com",
    "modelName": "deepseek-chat",
    "apiKey": "sk-xxx",
    "temperature": 0.7,
    "maxTokens": 4096,
    "timeoutSeconds": 60,
    "retryCount": 2
  }
}
```

Java 调用 Python 前应完成：

- 校验当前用户有权限执行该任务。
- 查询该用户该场景下启用的默认模型配置。
- 解密 API Key。
- 组装 `modelConfig`。
- 调用 Python 智能体接口。

## 4. Python 部分怎么开发

### 4.1 Python 的职责

Python 不负责模型配置的增删改查，也不直接维护 `model_config` 表。Python 只负责接收 Java 传来的 `modelConfig`，并用它创建模型客户端、执行智能体逻辑、返回结果。

Python 侧建议把模型配置作为请求级运行参数处理，不长期保存明文 API Key。

### 4.2 Python 接口入参

Python 智能体接口需要支持 Java 传来的 `modelConfig` 字段。

示例接口：

```text
POST /agent/report/generate
```

Python 需要校验：

- `modelConfig.provider` 是否存在。
- `modelConfig.baseUrl` 是否存在。
- `modelConfig.modelName` 是否存在。
- `modelConfig.apiKey` 是否存在。
- `timeoutSeconds`、`maxTokens`、`temperature` 是否在合理范围内。

如果缺少必要配置，Python 应返回明确错误，例如“模型配置不完整”，而不是抛出未处理异常。

### 4.3 Python 模型客户端适配

建议 Python 封装一个模型客户端创建函数，例如：

```text
create_model_client(modelConfig)
```

它根据 `provider`、`baseUrl`、`modelName`、`apiKey` 创建实际调用客户端。DeepSeek、通义千问、OpenAI-compatible 模型可以优先走兼容 OpenAI 的调用方式，减少分支。

Python 智能体代码不要到处读取 `apiKey`、`baseUrl`，应集中在模型客户端适配层处理。

### 4.4 Python 返回结果

Python 执行完成后，将结果返回给 Java。同步模式可以直接返回响应体，异步模式可以调用 Java 的任务完成接口。

返回内容建议包含：

- `taskId`
- `status`，例如 `completed` 或 `failed`
- `content`，生成结果
- `errorMessage`，失败原因
- 可选的 `usage`，例如 token 消耗

## 5. 测试连接怎么做

测试连接建议仍由 Java 入口发起，Python 负责实际模型试调用。

推荐流程：

```text
前端点击测试连接
Java 校验配置归属和用户权限
Java 解密 apiKey
Java 组装临时 modelConfig
Java 调 Python 的模型测试接口
Python 使用 modelConfig 发起一次轻量模型调用
Python 返回成功或失败
Java 返回测试结果给前端
```

Python 测试接口示例：

```text
POST /agent/model/test
```

测试时可以让模型回答一个极短问题，例如 `ping` 或 `请回复 ok`，并设置较短超时时间。

## 6. 边界规则

为了避免 Java 和 Python 互相调用导致架构混乱，建议遵守以下规则：

- Java 是主业务系统，负责用户、权限、配置、数据库主业务和任务编排。
- Python 是智能体执行服务，负责模型调用、智能体流程、内容生成。
- 模型配置从 Java 流向 Python，不由 Python 反向读取。
- Python 不写 `model_config` 表。
- Python 不记录完整 API Key 日志。
- Java 和 Python 之间的内部接口需要内部 token 或其他鉴权方式。
- 如果任务很长，Python 可以只回调固定的进度、完成、失败接口，不要随意增加反向调用链路。

## 7. 最小可用开发清单

Java 同学优先完成：

- 新增 `model_config` 表。
- 完成模型配置 CRUD。
- 完成 API Key 加密存储和脱敏返回。
- 完成按当前用户查询默认模型配置。
- 完成 Java 调 Python 时携带 `modelConfig`。
- 完成测试连接入口。

Python 同学优先完成：

- 接口入参支持 `modelConfig`。
- 封装模型客户端创建逻辑。
- 使用传入的 `baseUrl`、`modelName`、`apiKey` 调用模型。
- 缺少配置时返回可读错误。
- 测试连接接口返回成功或失败。

## 8. 推荐结论

模型配置的长期存储、权限和加密逻辑放在 Java；Python 不直接读取配置表，也不维护配置，只消费 Java 在任务请求中传来的 `modelConfig`。这样可以保持主链路为 `前端 -> Java -> Python`，避免出现 Java 调 Python 后 Python 又反向调用 Java 读取配置的复杂链路。
