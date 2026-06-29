feat(a): 文档管理模块后端实现

## 概述
文档管理模块后端实现（EPIC-03），包含文档 CRUD、文件存储、切片管理等功能。

## 新增功能
- **上传文档** - 支持 PDF/DOCX/PPTX/XLSX/MD/TXT/JPG/PNG，单个 <=50MB
- **文档列表** - 按知识库查询，支持状态筛选和分页
- **文档详情 / 下载** - 元数据查询 + 原始文件流式下载
- **删除文档** - 单删与批量删除，级联清理切片和文件
- **切片查看** - 按文档查询切片列表
- **更新标签** - 全量覆盖键值对标签
- **重试处理** - 仅 FAILED 状态可重试

## 技术细节
- 严格遵循《项目目录结构规范》，12 层架构分层
- 接口路径前缀 /api/，统一返回 ApiResponse<VO>
- 文件存储抽象层：默认本地文件系统，可切换 MinIO
- 响应字段与 docs/api-contract.yaml 完全对齐
- 适配 JDK 21：升级 Lombok 1.18.36 + maven-compiler-plugin 3.13.0

## 涉及文件
| 模块 | 说明 |
|------|------|
| km-backend/ | 文档管理全部后端代码（约 30 文件） |
| km-common/ | ErrorCode 补充 + JsonUtils 公共方法 |
| pom.xml（根） | JDK 21 兼容配置 |
| docs/api-contract.yaml | 同步更新 API 路径 |

## 待办（后续迭代）
- RabbitMQ 异步处理管道（EPIC-04）
- JWT 鉴权接入（EPIC-01）
