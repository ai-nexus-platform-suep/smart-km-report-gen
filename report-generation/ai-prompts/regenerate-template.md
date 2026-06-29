# 单章节重新生成 Prompt

> 加分项：用户在某章节点击「重新生成」并输入补充意见时使用。  
> 集成方：后端 1 · `POST /api/reports/{reportId}/sections/{sectionId}/regenerate`

## System Prompt

在 `section-system.md` 全文基础上追加：

```
## 重新生成模式

用户已对当前章节提出修改意见。请在保留报告整体风格的前提下：
1. 优先响应用户补充意见
2. 可调整段落结构、补充表格、修正表述
3. 不要解释修改过程，直接输出新正文
```

## User Prompt 模板

```
{section-user-template.md 替换变量后的完整内容}

---

## 原有正文（供参考，可大幅改写）

{existingContentMarkdown}

---

## 用户补充意见

{userHint}

---

请根据用户意见重新撰写本章节正文。只输出 Markdown，不要输出标题行。
```

## 变量说明

| 占位符 | 来源 |
|--------|------|
| `{existingContentMarkdown}` | `report_sections.content_markdown` 当前值 |
| `{userHint}` | 请求体 `{"hint":"..."}` |

## 示例 userHint

- 「请补充防汛物资储备数量表格，至少 5 行」
- 「问题描述不够具体，请增加 2 条设备隐患并标注风险等级」
- 「审计发现部分需增加入厂煤计量抽查数据」

## SSE 推送约定

重新生成与首次生成共用同一 SSE 事件流：

1. `progress` — 含 `sectionTitle`
2. `content` — 新正文片段
3. `section_done` — `[SECTION_DONE]`
4. `done` — 单章重新生成时可在 `section_done` 后立即发 `done`，或按后端 2 实现
