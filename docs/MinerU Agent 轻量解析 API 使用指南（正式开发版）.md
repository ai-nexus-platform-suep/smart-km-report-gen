# MinerU Agent 轻量解析 API 使用指南（正式开发版）

## 一、接口整体介绍

MinerU Agent 轻量解析 API 是面向 AI Agent、RAG 场景的**免登录、免 Token、高轻量化**文档解析服务。

核心定位：快速解析 PDF / 图片 / Office 文档，统一输出 Markdown，适合自动化批量文档入库、知识库构建。

### 核心特性

- **免登录、无密钥**：基于 IP 限频防滥用，无需 Token、无需账号

- **轻量高速**：轻量模型推理，可关闭表格/公式提升速度

- **统一输出**：所有文档最终返回标准 Markdown，适合切片、向量化入库

- **双解析模式**：远程URL解析 / 本地文件上传解析

- **异步任务机制**：提交任务获取 task\_id → 轮询查询结果

### 接口限制（重要）

|限制项|限制值|
|---|---|
|单文件大小上限|10MB|
|20页（超出报错 \-30003）||
|PDF、图片\(png/jpg/jpeg/webp等\)、Docx、PPTx、Xlsx||
|单IP每分钟请求数限制，超限返回429||
|网页HTML、加密PDF、超大文件、超长页数||

---

## 二、公共基础信息

**基础域名**：`https://mineru.net/api/v1/agent`

**请求头统一要求**：

- `Content-Type: application/json`

- 无需 Token、无需 Authorization

**任务模式**：全异步

所有解析接口只提交任务，必须通过 **task\_id 轮询** 获取最终 Markdown 结果。

---

## 三、接口详细文档

### 1\. 远程URL解析接口（推荐用于在线PDF）

**接口地址**：`POST /parse/url`

**功能**：传入远程文件URL，服务自动下载并异步解析

#### 请求参数（JSON）

|参数名|类型|必填|说明|
|---|---|---|---|
|url|string|是|远程文件直链（PDF/图片/Office），不支持网页|
|file\_name|string|否|文件名，不传自动从URL解析|
|language|string|否|默认 ch（中英文），影响OCR|
|enable\_table|bool|否|是否识别表格，默认 true|
|is\_ocr|bool|否|是否强制OCR，默认 false（电子PDF无需开启）|
|enable\_formula|bool|否|是否识别公式，默认 true|
|page\_range|string|否|页码范围，如 1\-10、5|

#### 成功响应示例

```Plain Text
{
  "code": 0,
  "data": {
    "task_id": "a90e6ab6-44f3-4554-b459-b62fe4c6b43605"
  },
  "msg": "ok",
  "trace_id": "xxx"
}
```

---

### 2\. 本地文件上传解析接口（用于本地PDF）

**接口地址**：`POST /parse/file`

**流程**：获取OSS签名URL → PUT上传文件 → 后台自动解析

#### 请求参数（JSON）

|参数名|类型|必填|说明|
|---|---|---|---|
|file\_name|string|是|带后缀完整文件名|
|language|string|否|默认 ch|
|enable\_table|bool|否|默认 true|
|is\_ocr|bool|否|默认 false|
|enable\_formula|bool|否|默认 true|
|page\_range|string|否|页码范围|

#### 成功响应

```Plain Text
{
  "code": 0,
  "data": {
    "task_id": "xxx",
    "file_url": "https://oss-mineru.openxlab.org.cn/agent/xxx.pdf?Expires=xxx"
  },
  "msg": "ok"
}
```

拿到 `file_url` 后，使用 **PUT** 方法上传二进制文件。

---

### 3\. 任务结果查询接口（核心轮询接口）

**接口地址**：`GET /parse/{task_id}`

**用途**：轮询获取解析状态、最终 Markdown 下载地址

#### 任务状态枚举

- **waiting\-file**：等待用户上传文件（仅文件上传模式）

- **uploading**：文件下载中

- **pending**：排队中

- **running**：解析中

- **done**：解析完成，可获取 markdown\_url

- **failed**：解析失败，读取 err\_msg / err\_code

#### 完成响应示例

```Plain Text
{
  "code": 0,
  "data": {
    "task_id": "xxx",
    "state": "done",
    "markdown_url": "https://cdn-mineru.openxlab.org.cn/pdf/xxx/full.md"
  },
  "msg": "ok"
}
```

---

## 四、错误码对照表（开发必看）

|错误码|错误说明|解决方案|
|---|---|---|
|\-30001|文件超出10MB大小限制|压缩/拆分文件或使用标准版API|
|\-30002|不支持的文件类型|仅支持PDF/图片/Office文档|
|\-30003|页数超过20页限制|指定page\_range分页解析|
|\-30004|请求参数错误|检查必填字段、格式|
|\-10002|invalid task\_id|task\_id不存在/已过期，重新提交任务|

---

## 五、语言参数 language 可选值

- **ch**：中英文（默认）

- **ch\_server**：繁體、手写增强

- **en**：纯英文

- **japan**：日文

- **korean**：韩文

- **latin/arabic/cyrillic**：多语种包

---

## 六、项目适配规范（适配你的RAG知识库）

### 1\. 适配你的业务报错现象说明

你之前出现的：

- `invalid task_id`：任务过期/不存在，轮询了无效ID

- 网页解析失败：传入了HTML网页链接，MinerU轻量API**不支持网页**，只支持文件直链

### 2\. 最佳实践（适配你的文档解析 Worker）

- 本地PDF统一走 **文件上传模式**

- 在线PDF直链走 **URL模式**

- 单文档控制 **≤20页、≤10MB**

- 批量任务分批执行，防止IP限流429

- 解析完成自动下载md → 本地切片 → 向量入库

## 七、完整调用逻辑总结

**本地PDF流程**：获取上传签名 → PUT上传 → 轮询task\_id → 下载md → 切片入库

**在线PDF流程**：提交URL → 获取task\_id → 轮询 → 下载md → 切片入库

> （注：部分内容可能由 AI 生成）
