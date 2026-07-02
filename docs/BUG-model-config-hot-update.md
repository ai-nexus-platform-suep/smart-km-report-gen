# 模型配置热更新失效问题分析

## 现象

1. 前端修改/删除/新建模型配置后，聊天仍使用旧配置
2. 删除模型后聊天仍能正常进行（说明根本没用 DB 配置）
3. 新建模型后切换会话报错

## 根因

**`InternalModelConfigController` 获取 userId 的方式错误，导致 Python 永远拿不到远程配置，静默回落 `.env` 本地配置。**

### 调用链路

```
前端 修改模型配置
  ↓
POST /api/model-configs → Gateway (:8080) → qa-chat-service (:8090)
  ↓
写入 model_config 表 ✅ 正常

用户发消息
  ↓
POST /api/chat → Gateway (:8080) → qa-agent (:8000) ✅ 正常
  ↓
chat.py → fetch_llm_config(user_id=1)
  ↓
java_client.py → HTTP GET http://127.0.0.1:8090/internal/model-configs/default?userId=1&scenario=chat
  ↑ 直连 Java :8090，不经过 Gateway，没有 X-User-Id Header
  ↓
InternalModelConfigController
  ↓
UserContextHolder.getUserId() → null  ← 根因
  ↓
selectDefault(null, "chat") → null（SQL: WHERE user_id = null）
  ↓
getDefaultDecrypted 抛异常 → Java 返回 4xx
  ↓
java_client.py _fetch_json() 捕获异常 → return None  ← 静默吞掉
  ↓
fetch_llm_config → 回落 _local_llm_config() → 使用 .env 静态配置
  ↓
前端修改永远不生效
```

### 关键代码

**问题代码** — `InternalModelConfigController.java`

```java
@GetMapping("/default")
public ApiResponse<ModelConfigInternalVO> getDefault(
        @RequestParam(defaultValue = "chat") String scenario) {
    Long userId = UserContextHolder.getUserId(); // ← null！没有 X-User-Id Header
    ...
}
```

**Python 一直在传 userId** — `java_client.py`

```python
remote_config = await _fetch_json(
    f"internal/model-configs/default?userId={user_id}&scenario={scenario}"
)
```

**错误被静默吞掉** — `java_client.py`

```python
except (httpx.HTTPError, ValueError):
    return None  # 连接失败、4xx、5xx 全部吞掉，不输出任何日志

...
if remote_config:
    return {...}
return _local_llm_config()  # 静默回落 .env 配置
```

## 修复方案

### 修复 1：InternalModelConfigController 恢复 `@RequestParam Long userId`

```java
@GetMapping("/default")
public ApiResponse<ModelConfigInternalVO> getDefault(
        @RequestParam Long userId,                    // ← 恢复
        @RequestParam(defaultValue = "chat") String scenario) {
    log.info("内部查询默认配置 userId={} scenario={}", userId, scenario);
    return ApiResponse.success(modelConfigService.getDefaultDecrypted(userId, scenario));
}
```

### 修复 2：java_client.py 错误日志

```python
except httpx.ConnectError as e:
    logger.error("连接 Java 服务失败 %s: %s", url, e)      // ← 明确错误
    return None
except httpx.HTTPStatusError as e:
    logger.error("Java 服务返回错误 %s -> %s", url, ...)   // ← 明确错误
    return None
```

### 修复 3：`.env` 端口确认

```
JAVA_CONFIG_BASE_URL=http://127.0.0.1:8090   # qa-chat-service 实际端口
```

## 受影响的文件

| 文件 | 问题 | 修复 |
|------|------|------|
| `qa-chat-service/.../InternalModelConfigController.java` | `UserContextHolder.getUserId()` 返回 null | 恢复 `@RequestParam Long userId` |
| `qa-agent/app/client/java_client.py` | 错误静默吞掉 | 加 `logger.error/warning` |
| `qa-agent/.env.example` | 端口 8082 已废弃 | 改为 8090 |
