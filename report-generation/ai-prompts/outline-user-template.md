请为以下电力行业报告生成多级章节大纲：

## 报告信息

| 项目 | 内容 |
|------|------|
| 报告类型 | {reportTypeLabel}（{reportType}） |
| 报告主题 | {subject} |
| 报告名称 | {name} |
| 专业 | {specialty} |
| 电厂 | {powerPlant} |
| 年份 | {reportYear} |

## 附加背景

{contextJson}

## 要求

1. 严格按 System Prompt 中的 JSON 格式输出，字段名使用 `number`、`title`、`level`、`promptHint`、`children`
2. 根据报告类型选择对应的专业章节结构
3. 若专业为「{specialty}」，在设备/检查相关章节中突出该专业视角
4. 每个 `promptHint` 写清楚该节正文应覆盖的要点，如需表格请在 hint 中注明「需含检查项表格」或「需含库存数据表格」

请直接输出 JSON，不要用 ```json 代码块包裹。
