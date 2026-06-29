# AI Prompt 交付物 · 报告生成模块

> 技术监督辅助平台 · 第 3 组 · **AI 全栈** 负责  
> 本文档说明 Prompt 文件用途、变量占位符及后端集成方式。

---

## 1. 文件清单

| 文件 | 用途 | 集成方 |
|------|------|--------|
| `outline-system.md` | 大纲生成 System Prompt | 后端 1 · `OutlineServiceImpl` |
| `outline-user-template.md` | 大纲生成 User Prompt 模板 | 后端 1 |
| `section-system.md` | 章节正文 System Prompt | 后端 1 / 后端 2 |
| `section-user-template.md` | 章节正文 User Prompt 模板 | 后端 1 / 后端 2 |
| `table-guidelines.md` | Markdown 表格输出规范 | 后端 1 · DOCX 导出前校验 |
| `regenerate-template.md` | 单章节重新生成 Prompt | 后端 1 · 加分项 |
| `sse-debug-guide.md` | SSE 流式调试指南 | 后端 2 · SSE 通道 |

---

## 2. 变量占位符约定

后端集成时使用 `{变量名}` 替换，示例：

| 占位符 | 来源 | 说明 |
|--------|------|------|
| `{reportType}` | 请求体 | `SUMMER_PEAK_CHECK` / `COAL_INVENTORY_AUDIT` |
| `{reportTypeLabel}` | 枚举 label | 迎峰度夏检查报告 / 煤库库存审计报告 |
| `{subject}` | 请求体 | 报告主题 |
| `{name}` | 请求体 | 报告名称 |
| `{specialty}` | 请求体 | 专业（电气、热控、锅炉等） |
| `{powerPlant}` | 请求体 | 电厂名称 |
| `{reportYear}` | 请求体 | 报告年份 |
| `{contextJson}` | 请求体 context | 附加上下文 JSON 字符串 |
| `{sectionNumber}` | 大纲节点 | 章节编号，如 `2.1` |
| `{sectionTitle}` | 大纲节点 | 章节标题 |
| `{sectionLevel}` | 大纲节点 | 层级 1/2/3 |
| `{promptHint}` | 大纲节点 | AI 生成提示 |
| `{outlineContext}` | 拼接大纲 | 上级章节标题链 |
| `{userHint}` | 重新生成请求 | 用户补充意见 |

---

## 3. 大纲生成 · 后端 1 集成要点

### 3.1 调用方式

后端 1 已通过 `app.ai.outline-url` 调用 AI 服务（默认 `http://127.0.0.1:9000/api/ai/outline/generate`）。  
AI 服务内部使用本目录 Prompt，后端只需保证请求体字段与 `OutlineServiceImpl.requestAiOutline` 一致。

### 3.2 期望返回 JSON

```json
{
  "success": true,
  "data": {
    "outline": [
      {
        "number": "1",
        "title": "检查概况",
        "level": 1,
        "promptHint": "说明检查背景、范围和依据",
        "children": [
          {
            "number": "1.1",
            "title": "检查背景",
            "level": 2,
            "promptHint": "结合电厂与年份描述迎峰度夏背景",
            "children": []
          }
        ]
      }
    ]
  }
}
```

### 3.3 大纲结构约束

- 一级章节（level=1）：4～8 章
- 二级章节（level=2）：每章 2～5 节
- 三级章节（level=3）：可选，审计类报告「审计发现」章建议有子节
- 每个节点必须含 `number`、`title`、`level`、`promptHint`、`children`
- `number` 采用 `1`、`1.1`、`1.1.1` 格式，与 `children` 嵌套一致

---

## 4. 章节内容生成 · 后端 2 集成要点

### 4.1 调用时机

SSE 通道 `GET /api/reports/{reportId}/sections/stream` 逐章调用 LLM，每章：

1. 发送 SSE `progress` 事件
2. 流式推送 `content` 文本片段
3. 章节完成后发送 `section_done`
4. 全部完成后发送 `done`

### 4.2 Prompt 组装

```
messages = [
  { "role": "system", "content": section-system.md 全文 },
  { "role": "user",   "content": section-user-template.md 替换变量后 }
]
```

### 4.3 输出要求

- 纯 Markdown 正文，不要输出 JSON 包裹
- 不要重复输出章节标题（DOCX 导出会单独渲染标题）
- 需要数据对比时使用 Markdown 表格，规范见 `table-guidelines.md`
- 单章建议 300～800 字；含表格时可至 1200 字

---

## 5. 本地 AI 服务联调

本目录同级 `ai-service/` 提供可运行的 Prompt 托管与 LLM 代理服务：

```powershell
cd report-generation/ai-service
pip install -r requirements.txt
copy .env.example .env
# 编辑 .env 填入 LLM_API_KEY
python -m uvicorn app.main:app --host 127.0.0.1 --port 9000
```

联调检查：

```http
POST http://127.0.0.1:9000/api/ai/outline/generate
Content-Type: application/json

{
  "reportType": "SUMMER_PEAK_CHECK",
  "reportTypeLabel": "迎峰度夏检查报告",
  "subject": "2026 年迎峰度夏专项检查",
  "name": "2026 年迎峰度夏专项检查报告",
  "specialty": "电气",
  "powerPlant": "示例电厂",
  "reportYear": 2026,
  "context": {}
}
```

---

## 6. 两种报告类型差异摘要

| 维度 | 迎峰度夏 `SUMMER_PEAK_CHECK` | 煤库存审计 `COAL_INVENTORY_AUDIT` |
|------|------------------------------|-----------------------------------|
| 报告性质 | 季节性安全检查、运行保障 | 内控审计、账实核对 |
| 核心章节 | 设备运行、度夏措施、应急预案 | 库存台账、入厂煤、审计发现 |
| 数据表格 | 设备检查项、缺陷清单 | 库存盘点、入出库明细 |
| 结论风格 | 整改建议 + 责任时限 | 审计结论 + 管理改进 |

Prompt 文件中已通过 `{reportType}` 分支说明，后端无需额外处理。
