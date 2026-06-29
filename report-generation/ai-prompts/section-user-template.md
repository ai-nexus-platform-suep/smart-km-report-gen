请撰写以下报告章节的正文内容。

## 报告背景

| 项目 | 内容 |
|------|------|
| 报告类型 | {reportTypeLabel}（{reportType}） |
| 报告主题 | {subject} |
| 电厂 | {powerPlant} |
| 专业 | {specialty} |
| 年份 | {reportYear} |

## 当前章节

| 项目 | 内容 |
|------|------|
| 编号 | {sectionNumber} |
| 标题 | {sectionTitle} |
| 层级 | {sectionLevel}（1=章，2=节，3=子节） |
| 写作要点 | {promptHint} |

## 大纲上下文（上级章节路径）

{outlineContext}

## 写作要求

1. 内容紧扣「{sectionTitle}」，与报告类型「{reportTypeLabel}」的专业规范一致
2. 若写作要点提到表格，请输出符合规范的 Markdown 表格
3. 不要输出章节标题「{sectionNumber} {sectionTitle}」
4. 直接输出 Markdown 正文
5. 严禁所有章节使用相似段落。
6. 当前章节必须围绕 sectionTitle 和 promptHint 展开。
7. 不得泛泛重复“本次检查围绕……”。
8. 如果章节标题包含“背景”，只写背景与目的。
9. 如果章节标题包含“范围”，只写检查范围、对象和重点。
10. 如果章节标题包含“问题”，只写问题表现、风险影响和原因分析。
11. 如果章节标题包含“建议”，只写整改措施、责任部门和完成时限。
12. 如果章节标题包含“结论”，只写总体评价和后续要求。