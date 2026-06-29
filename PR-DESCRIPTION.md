## 概述

实现知识库管理模块的完整 CRUD 接口，支持知识库的创建、查询、更新、批量删除、分页列表及模糊搜索。

## 测试环境

| 项目 | 说明 |
|------|------|
| 服务地址 | http://localhost:8091 |
| JDK 版本 | 1.8 |
| Spring Boot | 2.6.4 |
| 数据库 | MySQL 8.0，数据库 km_db |
| ORM | MyBatis（非 MyBatis-Plus） |
| 主键策略 | UUID |
| 统一返回格式 | ApiResponse.ok() / ApiResponse.fail() |
| 错误码管理 | ErrorCode 枚举 |

## 新增文件

| 模块 | 文件 |
|------|------|
| Controller | KnowledgeBaseController.java |
| Service | KnowledgeBaseService.java、KnowledgeBaseServiceImpl.java |
| Repository | KnowledgeBaseMapper.java |
| Entity | KnowledgeBase.java、Document.java、Chunk.java |
| DTO | CreateKnowledgeBaseRequest.java、UpdateKnowledgeBaseRequest.java、BatchDeleteRequest.java、KnowledgeBaseVO.java |
| Mapper XML | KnowledgeBaseMapper.xml |

## 变更文件

| 文件 | 说明 |
|------|------|
| km-backend/pom.xml | 添加 spring-boot-starter-validation 依赖 |

## API 接口列表

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/knowledge-bases | 分页查询列表（支持 keyword 模糊搜索、docType 过滤） |
| POST | /api/knowledge-bases | 创建知识库 |
| GET | /api/knowledge-bases/{id} | 查询知识库详情 |
| PUT | /api/knowledge-bases/{id} | 更新知识库 |
| DELETE | /api/knowledge-bases/batch | 批量删除知识库 |

## 接口验证结果

> （以下由 Postman 测试，截图见附件）

### 1. 健康检查
- **GET** /api/health
- 结果：200，返回 {"code":0,"message":"ok","data":{"status":"UP","service":"km-backend"}}

### 2. 创建知识库
- **POST** /api/knowledge-bases
- 请求体：{"name":"测试知识库","description":"Postman测试","docType":"规程规范","retrievalStrategy":"VECTOR_RERANK"}
- 结果：200，返回完整知识库信息（含 UUID）

### 3. 查询列表
- **GET** /api/knowledge-bases
- 结果：200，返回分页数据，total 正确，支持 ?keyword= 和 ?docType= 过滤

### 4. 查询详情
- **GET** /api/knowledge-bases/{id}
- 结果：200，返回对应知识库的完整信息

### 5. 更新知识库
- **PUT** /api/knowledge-bases/{id}
- 请求体：{"name":"更新后的名称","description":"新的描述"}
- 结果：200，返回更新后的数据

### 6. 批量删除
- **DELETE** /api/knowledge-bases/batch
- 请求体：{"ids":["uuid1","uuid2"]}
- 结果：200，删除成功后再查询确认已移除

## 补充说明

- 接口路径统一使用 /api/ 前缀（已按要求去掉 v1）
- 错误处理统一使用 BusinessException + GlobalExceptionHandler，返回规范错误码
- 数据库迁移使用 Flyway，已配置 baseline-on-migrate
