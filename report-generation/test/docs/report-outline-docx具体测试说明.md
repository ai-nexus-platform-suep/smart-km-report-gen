# report-common 与 report-outline-docx 具体测试说明

> 依据：`项目接口清单.md`、`报告生成模块测试交付物设计.md`、`report-common`、`report-outline-docx` 源码  
> 测试范围：后端 1 已实现范围，即健康检查、固定大纲、AI 大纲兜底、确认保存、DOCX 导出、文件下载。

---

## 1. 代码阅读结论

### 1.1 可直接测试的接口

| 接口 | 代码位置 | 说明 |
|---|---|---|
| `GET /api/health` | `HealthController` | 不依赖外部服务 |
| `POST /api/reports/outline` | `ReportController#outline` | 返回本地固定大纲，不依赖数据库 |
| `POST /api/reports/outline/generate` | `ReportController#generateOutline` | AI 未配置时可使用本地模板兜底，但会写 Redis 临时状态 |
| `POST /api/reports/outline/confirm` | `ReportController#confirmOutline` | 需要 MySQL；如果只用 `tempId` 还需要 Redis |
| `POST /api/reports/{reportId}/export/docx` | `ReportController#exportReportDocx` | 需要 MySQL 中已有报告、大纲、章节数据 |
| `GET /api/reports/files/{fileId}/download` | `ReportController#downloadFile` | 需要 MySQL 中已有文件记录，且本地文件存在 |

### 1.2 关键校验规则

| 请求对象 | 字段 | 规则 |
|---|---|---|
| `OutlineRequest` | `reportType` | 必填，只能是 `SUMMER_PEAK_CHECK` 或 `COAL_INVENTORY_AUDIT` |
| `OutlineGenerateRequest` | `reportType` | 必填 |
| `OutlineGenerateRequest` | `subject` | 必填，不能为空白 |
| `OutlineGenerateRequest` | `reportYear` | 可选；传入时必须 `>= 2000` |
| `OutlineConfirmRequest` | `reportType` | 必填 |
| `OutlineConfirmRequest` | `subject` | 必填，不能为空白 |
| `OutlineConfirmRequest` | `specialty` | 必填，不能为空白 |
| `OutlineConfirmRequest` | `powerPlant` | 必填，不能为空白 |
| `OutlineConfirmRequest` | `reportYear` | 必填，且必须 `>= 2000` |
| `ReportDocxExportRequest` | `figureNumberingMode` | 可选；只能是 `GLOBAL` 或 `SECTION` |
| `ReportDocxExportRequest` | `tableNumberingMode` | 可选；只能是 `GLOBAL` 或 `SECTION` |
| `ReportDocxExportRequest` | `includeEmptySections` | 可选；默认 `true` |

---

## 2. 已补充的自动化测试

测试文件已集中移动到 `smart-km-report-gen/report-generation/test`：

| 文件 | 覆盖内容 |
|---|---|
| `smart-km-report-gen/report-generation/test/java/com/powerreport/controller/ReportControllerTest.java` | 控制器路由、参数校验、统一响应、文件下载响应头 |
| `smart-km-report-gen/report-generation/test/java/com/powerreport/service/OutlineServiceImplTest.java` | 固定大纲、本地模板兜底、关闭兜底时报错、确认保存边界 |
| `smart-km-report-gen/report-generation/test/java/com/powerreport/service/DocxExportServiceImplTest.java` | DOCX 文件生成、文件记录保存、报告不存在、文件记录不存在 |

说明：测试代码已集中放在 `report-generation/test/java`，作为独立测试交付目录保存。为了不改动业务模块配置，当前未修改 `report-outline-docx/pom.xml`。如需重新运行这些 JUnit 测试，可临时复制到 `report-outline-docx/src/test/java`，或由后端负责人单独配置测试源码目录。

移动前已验证通过的执行命令：

```powershell
cd C:\Users\hp\Desktop\课设\smart-km-report-gen
& 'C:\apache-maven-3.8.8\bin\mvn.cmd' -q -pl report-generation/report-outline-docx test
```

本次执行结果：

- 使用 `C:\apache-maven-3.8.8\bin\mvn.cmd` 执行通过。
- `ReportControllerTest`：7 个用例通过。
- `OutlineServiceImplTest`：5 个用例通过。
- `DocxExportServiceImplTest`：3 个用例通过。
- 合计：15 个用例通过，0 失败，0 错误。

环境注意事项：

- 默认命令 `mvn` 指向 `C:\mavennew\apache-maven-3.6.2`，版本过低，无法编译 Spring Boot 3.3.0 默认插件。
- 如需使用默认 `mvn` 命令，需要先切换到 Maven 3.6.3 或以上。
- `mvn -pl report-generation/report-outline-docx -am test` 会连带执行 `qa-common` 中原有的占位 SpringBootTest，该测试缺少启动类，会失败；本次只验证 `report-outline-docx` 目标模块，所以采用单模块测试命令。

---

## 3. Apifox 具体接口测试步骤

### 3.1 健康检查

```http
GET {{baseUrl}}/api/health
```

预期：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "status": "ok",
    "scope": "outline-and-docx"
  }
}
```

### 3.2 固定大纲成功

```http
POST {{baseUrl}}/api/reports/outline
Content-Type: application/json
```

```json
{
  "reportType": "SUMMER_PEAK_CHECK"
}
```

预期：

- HTTP 200。
- `code=200`。
- `data` 为数组。
- 第一章包含 `number=1`、`title=检查概况`、`level=1`。

### 3.3 固定大纲参数为空

```json
{}
```

预期：

- HTTP 400。
- `code=400`。
- `message` 包含 `reportType`。

### 3.4 固定大纲枚举非法

```json
{
  "reportType": "UNKNOWN_TYPE"
}
```

预期：

- HTTP 400。
- JSON 反序列化失败或参数错误。

### 3.5 AI 大纲生成，走本地模板兜底

前置配置：

```properties
app.ai.outline-url=
app.ai.fallback-enabled=true
```

请求：

```http
POST {{baseUrl}}/api/reports/outline/generate
Content-Type: application/json
```

```json
{
  "reportType": "SUMMER_PEAK_CHECK",
  "subject": "2026 年迎峰度夏专项检查",
  "name": "2026 年迎峰度夏专项检查报告",
  "specialty": "电气",
  "powerPlant": "示例电厂",
  "reportYear": 2026,
  "context": {
    "unit": "1号机组"
  }
}
```

预期：

- HTTP 200。
- `data.tempId` 不为空。
- `data.source=LOCAL_TEMPLATE`。
- `data.expireSeconds >= 60`。
- `data.outline` 不为空。

### 3.6 AI 大纲生成，subject 为空

```json
{
  "reportType": "SUMMER_PEAK_CHECK",
  "subject": " ",
  "reportYear": 2026
}
```

预期：

- HTTP 400。
- `code=400`。
- `message` 包含 `subject`。

### 3.7 AI 大纲生成，年份越界

```json
{
  "reportType": "SUMMER_PEAK_CHECK",
  "subject": "2026 年迎峰度夏专项检查",
  "reportYear": 1999
}
```

预期：

- HTTP 400。
- `message` 包含 `reportYear`。

### 3.8 确认保存大纲成功

前置条件：

- MySQL 已启动。
- 已执行 `report-generation/database/schema_mysql.sql`。
- 服务已配置 datasource。

请求：

```http
POST {{baseUrl}}/api/reports/outline/confirm
Content-Type: application/json
```

```json
{
  "reportType": "SUMMER_PEAK_CHECK",
  "subject": "2026 年迎峰度夏专项检查",
  "name": "2026 年迎峰度夏专项检查报告",
  "specialty": "电气",
  "powerPlant": "示例电厂",
  "reportYear": 2026,
  "outline": [
    {
      "number": "1",
      "title": "检查概况",
      "level": 1,
      "children": [
        {
          "number": "1.1",
          "title": "检查背景",
          "level": 2,
          "children": []
        }
      ]
    }
  ]
}
```

预期：

- HTTP 200。
- `data.reportId` 不为空，保存为 Apifox 变量 `reportId`。
- `data.status=DRAFT`。
- `data.outlineCount=2`。

### 3.9 确认保存大纲，outline 为空且无 tempId

```json
{
  "reportType": "SUMMER_PEAK_CHECK",
  "subject": "2026 年迎峰度夏专项检查",
  "specialty": "电气",
  "powerPlant": "示例电厂",
  "reportYear": 2026,
  "outline": []
}
```

预期：

- HTTP 400。
- `message=outline 为空时必须传 tempId`。

### 3.10 导出 DOCX 成功

前置条件：

- 已通过确认保存接口获得 `reportId`。

```http
POST {{baseUrl}}/api/reports/{{reportId}}/export/docx
Content-Type: application/json
```

```json
{
  "figureNumberingMode": "GLOBAL",
  "tableNumberingMode": "SECTION",
  "includeEmptySections": true
}
```

预期：

- HTTP 200。
- `data.fileId` 不为空，保存为 Apifox 变量 `fileId`。
- `data.fileName` 以 `.docx` 结尾。
- `data.fileSize > 0`。
- `data.sha256` 长度为 64。
- `data.downloadUrl=/api/reports/files/{fileId}/download`。

### 3.11 导出 DOCX，reportId 不存在

```http
POST {{baseUrl}}/api/reports/not-exist/export/docx
```

预期：

- HTTP 400。
- `message` 包含 `报告不存在或已删除`。

### 3.12 下载 DOCX 成功

```http
GET {{baseUrl}}/api/reports/files/{{fileId}}/download
```

预期：

- HTTP 200。
- `Content-Type=application/vnd.openxmlformats-officedocument.wordprocessingml.document`。
- 响应头包含 `Content-Disposition`。
- 返回二进制 DOCX 文件流。

### 3.13 下载 DOCX，fileId 不存在

```http
GET {{baseUrl}}/api/reports/files/not-exist/download
```

预期：

- HTTP 400。
- `message` 包含 `导出文件不存在`。

---

## 4. 仍需真实环境执行的测试

| 测试内容 | 原因 | 处理建议 |
|---|---|---|
| 确认保存接口真实落库 | 需要 MySQL、Nacos 配置或本地 datasource 配置 | 按 README 配好数据库后用 Apifox 执行 |
| AI 真调用链路 | 需要 `app.ai.outline-url` 指向可用 AI 服务 | 联调时设置 `app.ai.fallback-enabled=false` |
| DOCX 下载真实链路 | 需要先导出生成 `fileId`，且文件在本地存在 | 按 3.10、3.12 顺序执行 |
