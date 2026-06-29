# AI 代理服务

> 报告生成模块 · AI 全栈交付 · 端口 **9000**

本服务托管 `../ai-prompts/` 中的 Prompt，对外提供 LLM 调用接口，供后端 1 / 后端 2 联调。

**说明：** 本服务不属于 Java 后端模块，不实现 `项目接口清单.md` 中的业务 API，仅作为 AI 层代理。

## 接口

| 方法 | 路径 | 消费者 | 说明 |
|------|------|--------|------|
| GET | `/api/health` | 运维 | 健康检查 |
| POST | `/api/ai/outline/generate` | 后端 1 | 大纲生成，返回 JSON |
| POST | `/api/ai/section/stream` | 后端 2 | 章节 SSE 流式生成 |

## 快速启动

```powershell
cd report-generation/ai-service
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
copy .env.example .env
# 编辑 .env：填入 LLM_API_KEY，或设置 LLM_MOCK=true 做离线联调
python -m uvicorn app.main:app --host 127.0.0.1 --port 9000 --reload
```

## 与 Nacos 配置对应

后端 1 的 `app.ai.outline-url` 默认指向：

```properties
app.ai.outline-url=http://127.0.0.1:9000/api/ai/outline/generate
```

后端 2 章节流式生成建议配置（自行添加到 Nacos）：

```properties
app.ai.section-stream-url=http://127.0.0.1:9000/api/ai/section/stream
```

## Mock 模式

无 LLM API Key 时，在 `.env` 中设置：

```properties
LLM_MOCK=true
```

- 大纲接口返回固定 JSON 结构
- 流式接口按 50ms 间隔推送模拟文本，用于 SSE 通道调试（首 Token < 500ms）

## 测试

```powershell
# 健康检查
curl http://127.0.0.1:9000/api/health

# 大纲生成
curl -X POST http://127.0.0.1:9000/api/ai/outline/generate `
  -H "Content-Type: application/json" `
  -d "{\"reportType\":\"SUMMER_PEAK_CHECK\",\"subject\":\"2026年迎峰度夏检查\",\"specialty\":\"电气\",\"powerPlant\":\"示例电厂\",\"reportYear\":2026}"

# 章节流式（需 -N 禁用缓冲）
curl -N -X POST http://127.0.0.1:9000/api/ai/section/stream `
  -H "Content-Type: application/json" `
  -d "{\"reportType\":\"SUMMER_PEAK_CHECK\",\"subject\":\"测试\",\"powerPlant\":\"示例电厂\",\"specialty\":\"电气\",\"reportYear\":2026,\"sectionNumber\":\"1.1\",\"sectionTitle\":\"检查背景\",\"sectionLevel\":2,\"promptHint\":\"说明背景\",\"outlineContext\":\"1 检查概况\"}"
```

更多 Prompt 说明与 SSE 调试见 `../ai-prompts/README.md` 与 `../ai-prompts/sse-debug-guide.md`。
