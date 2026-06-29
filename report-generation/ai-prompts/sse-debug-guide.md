# SSE 流式内容调试指南

> 协助 **后端 2** 调试 `GET /api/reports/{reportId}/sections/stream`  
> 目标：**首 Token 延迟 < 2 秒**，前端逐字渲染流畅。

---

## 1. SSE 事件规格（与接口清单一致）

| 事件名 | 触发时机 | data 格式 | 示例 |
|--------|----------|-----------|------|
| `progress` | 开始生成新章节 | JSON 字符串 | `{"current":2,"total":5,"sectionTitle":"现场自查情况"}` |
| `content` | LLM 每个 token/chunk | **纯文本**（非 JSON） | `本次检查共涉及` |
| `section_done` | 单章生成完毕 | 固定文本 | `[SECTION_DONE]` |
| `done` | 全报告生成完毕 | 固定文本 | `[DONE]` |

### Spring 示例（SseEmitter）

```java
SseEmitter emitter = new SseEmitter(300_000L);

// 1. 章节开始
emitter.send(SseEmitter.event()
    .name("progress")
    .data("{\"current\":1,\"total\":5,\"sectionTitle\":\"检查概况\"}"));

// 2. 流式内容 — data 为纯文本，不要 JSON 序列化
emitter.send(SseEmitter.event()
    .name("content")
    .data("根据2026年迎峰度夏工作要求"));

// 3. 章节结束
emitter.send(SseEmitter.event()
    .name("section_done")
    .data("[SECTION_DONE]"));

// 4. 全部结束
emitter.send(SseEmitter.event()
    .name("done")
    .data("[DONE]"));
emitter.complete();
```

---

## 2. 首 Token 延迟优化（< 2s）

### 2.1 调用链路

```
前端 EventSource
  → 后端 2 SSE Controller
    → AI 服务 POST /api/ai/section/stream（流式）
      → LLM API（stream=true）
```

### 2.2 优化清单

| 优先级 | 措施 | 预期效果 |
|--------|------|----------|
| P0 | LLM 请求 `stream=true`，收到首个 chunk 立即 `content` 推送 | 首 Token < 1s（视模型而定） |
| P0 | 不要在首 Token 前做全章 Prompt 以外的 IO（如查大 JSON） | 减少 200～500ms |
| P1 | 章节列表、大纲上下文在 `POST .../sections/generate` 时预加载到内存/Redis | SSE 连接时零 DB 查询 |
| P1 | HTTP 连接池复用 LLM 客户端，避免每章新建 TCP | 每章省 100～300ms |
| P2 | System Prompt 缓存（同报告类型不变） | 减少 token 与解析时间 |
| P2 | 超时：连接 5s，读超时 120s，与 `timeoutSeconds` 配置一致 | 避免 hung 连接 |

### 2.3 AI 服务流式接口约定

后端 2 调用 AI 服务：

```http
POST /api/ai/section/stream
Content-Type: application/json
Accept: text/event-stream

{
  "reportType": "SUMMER_PEAK_CHECK",
  "reportTypeLabel": "迎峰度夏检查报告",
  "subject": "...",
  "powerPlant": "...",
  "specialty": "电气",
  "reportYear": 2026,
  "sectionNumber": "2.1",
  "sectionTitle": "主设备运行情况",
  "sectionLevel": 2,
  "promptHint": "...",
  "outlineContext": "1 检查概况 > 2 设备运行与安全保障情况"
}
```

AI 服务响应：标准 SSE，`event: content` + `data: 文本片段`，结束时 `event: end` + `data: [END]`。

后端 2 将 AI 服务的 `content` 事件**原样转发**为对前端的 `content` 事件（勿二次包装 JSON）。

---

## 3. 本地调试步骤

### 3.1 启动 AI 服务

```powershell
cd report-generation/ai-service
pip install -r requirements.txt
copy .env.example .env
python -m uvicorn app.main:app --host 127.0.0.1 --port 9000
```

### 3.2 用 curl 测 AI 流式

```powershell
curl -N -X POST http://127.0.0.1:9000/api/ai/section/stream `
  -H "Content-Type: application/json" `
  -d "{\"reportType\":\"SUMMER_PEAK_CHECK\",\"reportTypeLabel\":\"迎峰度夏检查报告\",\"subject\":\"2026年迎峰度夏检查\",\"powerPlant\":\"示例电厂\",\"specialty\":\"电气\",\"reportYear\":2026,\"sectionNumber\":\"1.1\",\"sectionTitle\":\"检查背景\",\"sectionLevel\":2,\"promptHint\":\"说明迎峰度夏背景与政策依据\",\"outlineContext\":\"1 检查概况\"}"
```

记录从请求发出到**第一条 `data:`** 的时间，应 < 2s。

### 3.3 浏览器测后端 2 SSE（联调阶段）

```javascript
const es = new EventSource('/api/reports/{reportId}/sections/stream');
const t0 = Date.now();
let firstContent = false;

es.addEventListener('progress', e => console.log('progress', JSON.parse(e.data)));
es.addEventListener('content', e => {
  if (!firstContent) {
    console.log('首 Token 延迟 ms:', Date.now() - t0);
    firstContent = true;
  }
  process.stdout?.write?.(e.data); // 或 append 到 DOM
});
es.addEventListener('section_done', () => console.log('\n--- section done ---'));
es.addEventListener('done', () => { console.log('DONE'); es.close(); });
```

### 3.4 无 LLM Key 时的 Mock 模式

AI 服务设置 `LLM_MOCK=true` 时，不调用真实 LLM，按固定间隔推送模拟 chunk，用于纯 SSE 通道调试。首条 content 应在 **500ms 内** 到达。

---

## 4. 常见问题排查

| 现象 | 可能原因 | 处理 |
|------|----------|------|
| 前端收不到 content | 后端把 content 包进了 JSON `{"text":"..."}` | data 必须是纯文本 |
| 首 Token > 5s | 未开 stream；或 generate 阶段未预加载 | 检查 LLM `stream=true` |
| 中文乱码 | 未设 UTF-8 | `produces = TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8"` |
| 连接很快断开 | SseEmitter 超时过短 | 建议 300s+ |
| 表格被拆成多个 content | 正常行为 | 前端按顺序拼接即可 |
| section_done 未触发 | AI 流异常中断 | catch 后仍发 section_done 或 error 事件 |

---

## 5. 章节生成顺序建议

1. 按大纲 `sort_order` 深度优先或仅 **level≥2 的叶子节点** 生成正文（level=1 章标题可跳过或写短导语）
2. 与 `reports.total_sections`、`completed_sections` 字段同步更新
3. 每章完成后 `PUT` 保存 `content_markdown` 到 `report_sections`（可在 section_done 后异步写库）

---

## 6. 性能验收标准

| 指标 | 标准 |
|------|------|
| 首 Token 延迟 | < 2s（Mock 模式 < 0.5s） |
| 单章 500 字生成 | 流式推送无明显卡顿（chunk 间隔 < 200ms 均值） |
| 5 章报告 | 全程无 SSE 断连 |
| 错误恢复 | LLM 失败时前端收到明确错误，不 silent hang |

联调通过后，请后端 2 在 PR 中附一张浏览器 Network → EventStream 截图，标注首条 content 时间戳。
