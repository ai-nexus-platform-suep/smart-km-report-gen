# 电力报告生成系统后端

![Java](https://img.shields.io/badge/Java-17-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen)
![Maven](https://img.shields.io/badge/Maven-3.8%2B-C71A36)
![MySQL](https://img.shields.io/badge/MySQL-8-4479A1)
![Redis](https://img.shields.io/badge/Redis-cache-DC382D)
![Nacos](https://img.shields.io/badge/Nacos-config%20center-2F74C0)
![License](https://img.shields.io/badge/License-MIT-green)

## 1. 项目概述

本项目面向电力行业报告编写场景，支持固定类型专业报告的大纲生成、章节内容保存、DOCX 导出和文件下载。系统完整需求覆盖用户认证、报告大纲、内容生成、报告记录、模板管理、管理后台和统计监控等模块。

当前仓库在 `report-generation/` 下提供报告组公共模块 `report-common` 和后端 1 负责的 Spring Boot 服务 `report-outline-docx`。后端 1 核心交付范围是：

- 大纲生成接口
- 大纲确认保存接口
- DOCX 静态重构导出
- 已生成 Word 文件下载
- DOCX 样式渲染
- 图表编号逻辑

支持的报告类型：

| 枚举值 | 名称 |
| --- | --- |
| `SUMMER_PEAK_CHECK` | 迎峰度夏检查报告 |
| `COAL_INVENTORY_AUDIT` | 煤库存审计报告 |

## 2. 团队分工

| 角色 | 负责内容 |
| --- | --- |
| 组长 | 技术决策、JWT 鉴权、网关与跨组联调 |
| 后端 1 | 大纲接口、DOCX 导出核心业务 |
| 后端 2 | 历史记录、SSE 传输通道、配置与统计接口 |
| 前端 1 | 新建页面、大纲树状交互、DOCX 下载与历史表格页 |
| 前端 2 | 流式工作台、正文/表格在线编辑、后台数据大屏 |
| AI 全栈 | LLM 大纲/章节 Prompt 编写、SSE 内容流调试 |
| 测试 | 用例编写、Bug 登记追踪、Wiki 文档整理 |

本文档当前重点说明和实现后端 1 范围。

## 3. 技术栈

| 技术 | 说明 |
| --- | --- |
| Java 17 | 运行环境 |
| Spring Boot 3.3.5 | 后端框架 |
| Maven | 项目构建 |
| MyBatis-Plus | ORM 和 Mapper |
| MySQL 8 | 业务数据库 |
| Redis | 大纲临时状态缓存 |
| Nacos | 配置中心和服务发现 |
| Apache POI | DOCX 生成 |

## 4. 项目结构

```text
ai-nexus-platform/
├── README.md
├── pom.xml
├── qa-common/
└── report-generation/
    ├── database/
    │   ├── schema_mysql.sql
    │   └── sample_seed_core.sql
    ├── input/
    │   └── requirements.pdf
    ├── report-common/
    │   ├── pom.xml
    │   └── src/main/java/com/powerreport/
    │       ├── entity/
    │       └── enums/
    └── report-outline-docx/
        ├── pom.xml
        ├── README.md
        ├── src/
        │   ├── nacos-config-example.properties
        │   └── main/
        │       ├── java/com/powerreport/
        │       │   ├── common/
        │       │   ├── config/
        │       │   ├── controller/
        │       │   ├── dto/
        │       │   ├── mapper/
        │       │   └── service/
        │       └── resources/
        │           └── application.properties
```

核心代码位置：

| 文件 | 说明 |
| --- | --- |
| `report-generation/report-common/entity` | 报告主表、大纲、章节、文件等报告组公共实体 |
| `report-generation/report-common/enums` | 报告类型、章节状态、图表编号模式等报告组公共枚举 |
| `qa-common/dto/ApiResponse.java` | 全项目统一接口响应包装 |
| `ReportController.java` | 大纲、导出、下载接口入口 |
| `OutlineServiceImpl.java` | 大纲生成、AI 调用、Redis 临时状态、确认保存 |
| `DocxExportServiceImpl.java` | DOCX 渲染、数据库草稿导出、文件记录、图表编号 |
| `AiIntegrationProperties.java` | 外部 AI 大纲服务配置 |
| `ReportExportProperties.java` | DOCX 导出目录配置 |

## 5. 环境准备

本地需要准备：

- JDK 17 或更高版本
- Maven 3.8+
- MySQL 8
- Redis
- Nacos

检查命令：

```powershell
java -version
mvn -version
```

## 6. 创建数据库

登录 MySQL 后执行：

```sql
SOURCE ./report-generation/database/schema_mysql.sql;
```

核心表：

| 表名 | 说明 |
| --- | --- |
| `reports` | 报告主表 |
| `report_outline_nodes` | 大纲树节点 |
| `report_sections` | 章节正文内容 |
| `report_files` | DOCX 导出文件记录 |
| `report_templates` | 模板元数据 |

可选测试数据：

```sql
SOURCE ./report-generation/database/sample_seed_core.sql;
```

## 7. 配置 Nacos

本项目本地 `application.properties` 只保留 Nacos bootstrap 配置，业务配置放在 Nacos。

Nacos 新建配置：

```text
Data ID: report-outline-docx
Group: DEFAULT_GROUP
Format: Properties
```

配置内容参考：

```text
report-generation/report-outline-docx/src/nacos-config-example.properties
```

示例：

```properties
server.port=8080

spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.datasource.url=jdbc:mysql://127.0.0.1:3306/power_report?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
spring.datasource.username=your-mysql-username
spring.datasource.password=your-mysql-password

spring.data.redis.host=127.0.0.1
spring.data.redis.port=6379
spring.data.redis.database=0
#spring.data.redis.password=your-redis-password

mybatis-plus.configuration.map-underscore-to-camel-case=true
mybatis-plus.global-config.db-config.id-type=input

app.report.export-dir=./storage/reports
app.ai.outline-url=http://127.0.0.1:9000/api/ai/outline/generate
app.ai.timeout-seconds=60
app.ai.outline-temp-ttl-seconds=1800
app.ai.fallback-enabled=true

management.health.redis.enabled=false
management.health.db.enabled=false
```

配置说明：

| 配置项 | 说明 |
| --- | --- |
| `spring.datasource.*` | MySQL 连接 |
| `spring.data.redis.*` | Redis 连接 |
| `app.report.export-dir` | DOCX 本地导出目录 |
| `app.ai.outline-url` | 外部 AI 大纲服务地址 |
| `app.ai.fallback-enabled` | AI 服务不可用时是否使用本地模板兜底 |

正式联调时，如果必须验证 AI 服务调用结果，建议设置：

```properties
app.ai.fallback-enabled=false
```

## 8. 启动后端

在仓库根目录启动后端 1 服务：

```powershell
mvn -pl report-generation/report-outline-docx -am spring-boot:run
```

也可以先打包验证：

```powershell
mvn clean package -DskipTests
```

服务地址：

```text
http://127.0.0.1:8080
```

健康检查：

```http
GET http://127.0.0.1:8080/api/health
```

预期响应：

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

## 9. 接口定义

统一响应格式：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

请求头建议：

```http
Content-Type: application/json; charset=UTF-8
```

前后端路由接口总览：

| 模块 | 前端触发场景 | Method | 后端路由 | 主要请求数据 | 主要返回数据 | 说明 |
| --- | --- | --- | --- | --- | --- | --- |
| 健康检查 | 启动后连通性检查 | `GET` | `/api/health` | 无 | `status`、`scope` | 用于确认后端服务是否启动 |
| 大纲生成 | 新建报告页填写主题后点击生成大纲 | `POST` | `/api/reports/outline/generate` | `reportType`、`subject`、`name`、`specialty`、`powerPlant`、`reportYear` | `tempId`、`source`、`expireSeconds`、`outline` | 后端调用外部 AI 大纲服务，并将结果写入 Redis 临时状态 |
| 大纲确认保存 | 用户在前端编辑树状大纲后点击确认 | `POST` | `/api/reports/outline/confirm` | `tempId`、报告基础信息、最终 `outline` JSON | `reportId`、`status`、`outlineCount`、带数据库 ID 的 `outline` | 保存 `reports` 和 `report_outline_nodes` |
| DOCX 导出 | 用户点击导出 Word | `POST` | `/api/reports/{reportId}/export/docx` | `figureNumberingMode`、`tableNumberingMode`、`includeEmptySections` | `fileId`、`fileName`、`fileSize`、`sha256`、`downloadUrl` | 从数据库读取草稿静态生成 DOCX，不重新调用 AI |
| 文件下载 | 用户点击下载 Word | `GET` | `/api/reports/files/{fileId}/download` | 路径参数 `fileId` | DOCX 文件流 | 前端可直接打开 `downloadUrl`，或按 `blob` 下载 |

### 9.1 生成大纲

```http
POST /api/reports/outline/generate
```

用途：接收报告主题和报告类型，调用外部 AI 大纲服务，解析后写入 Redis 临时状态。

请求：

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

响应：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "tempId": "9d8a0d71-6b14-41b3-a7fb-c9969d38b80c",
    "source": "AI",
    "expireSeconds": 1800,
    "outline": [
      {
        "id": "temp-node-id",
        "number": "1",
        "title": "检查概况",
        "level": 1,
        "promptHint": "说明检查背景、范围和依据",
        "children": []
      }
    ]
  }
}
```

`source` 说明：

| 值 | 说明 |
| --- | --- |
| `AI` | 已调用外部 AI 大纲服务 |
| `LOCAL_TEMPLATE` | AI 服务未配置或失败，后端使用本地模板兜底 |

### 9.2 外部 AI 大纲服务约定

后端会向 `app.ai.outline-url` 发送：

```json
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

推荐 AI 服务返回：

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
            "children": []
          }
        ]
      }
    ]
  }
}
```

后端兼容字段：

| 含义 | 兼容字段 |
| --- | --- |
| 顶层大纲 | `outline`、`nodes`、`sections`、`items` |
| 节点标题 | `title`、`name`、`heading` |
| 节点编号 | `number`、`no`、`index` |
| 子节点 | `children`、`sections`、`items` |

### 9.3 确认保存大纲

```http
POST /api/reports/outline/confirm
```

用途：接收前端确认后的大纲 JSON，保存 `reports` 和 `report_outline_nodes`。

请求：

```json
{
  "tempId": "9d8a0d71-6b14-41b3-a7fb-c9969d38b80c",
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
      "promptHint": "说明检查背景、范围和依据",
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

响应：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "reportId": "2972bb8e-bb2b-47f9-8a99-dfb385a22538",
    "status": "DRAFT",
    "outlineCount": 2,
    "outline": [
      {
        "id": "2cd47590-62f0-46c8-9c4c-61b8b9820cb7",
        "number": "1",
        "title": "检查概况",
        "level": 1,
        "promptHint": "说明检查背景、范围和依据",
        "children": []
      }
    ]
  }
}
```

前端注意：确认保存后，响应中的 `outline[].id` 是数据库 `report_outline_nodes.id`，后续章节内容应使用它作为 `outlineNodeId`。

### 9.4 导出 DOCX

```http
POST /api/reports/{reportId}/export/docx
```

用途：从数据库读取 `reports`、`report_outline_nodes`、`report_sections`，静态重构 Word，不重新调用 AI。

请求：

```json
{
  "figureNumberingMode": "GLOBAL",
  "tableNumberingMode": "SECTION",
  "includeEmptySections": true
}
```

编号模式：

| 值 | 说明 |
| --- | --- |
| `GLOBAL` | 全文连续编号，生成 `图 1`、`图 2`、`表 1` |
| `SECTION` | 按一级章节编号，生成 `图 1.1`、`图 1.2`、`表 1.1` |

响应：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "fileId": "c7ddf38a-1382-4bda-a1b0-df0f677600bb",
    "reportId": "2972bb8e-bb2b-47f9-8a99-dfb385a22538",
    "fileName": "2026_年迎峰度夏专项检查报告_20260627212301.docx",
    "fileSize": 18342,
    "sha256": "file-sha256",
    "downloadUrl": "/api/reports/files/c7ddf38a-1382-4bda-a1b0-df0f677600bb/download"
  }
}
```

### 9.5 下载 DOCX

```http
GET /api/reports/files/{fileId}/download
```

用途：根据 `report_files.id` 下载已生成 Word 文件。

前端处理：

- 浏览器直接打开 `downloadUrl`。
- 使用 axios/fetch 时按 `blob` 接收。

## 10. DOCX 样式与内容格式

导出样式：

| 项目 | 实现 |
| --- | --- |
| 标题 | 黑体 |
| 正文 | 仿宋_GB2312 |
| 页边距 | 上下 2.54cm，左右约 2.79cm |
| 段落 | 1.5 倍行距、首行缩进 |
| 表格 | 三线表 |
| 图题注 | `图 1` 或 `图 1.1` |
| 表题注 | `表 1` 或 `表 1.1` |
