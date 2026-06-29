## 概述

实现管理后台数据统计概览接口，提供知识库/文档/切片总数及近30天上传统计，支撑概览页核心指标卡片与趋势图展示。

## 测试环境

| 项目 | 说明 |
|------|------|
| 服务地址 | http://localhost:8091 |
| JDK 版本 | 1.8 |
| Spring Boot | 2.6.4 |
| 数据库 | MySQL 8.0，数据库 km_db |

## 新增文件

| 模块 | 文件 |
|------|------|
| Controller | StatsController.java |
| Service | StatsService.java、StatsServiceImpl.java |
| Repository | StatsMapper.java |
| Mapper XML | StatsMapper.xml |
| VO | StatsSummaryVO.java |

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/stats/summary | 统计摘要（核心指标 + 上传趋势） |

## 接口验证结果

> （以下由 Postman 测试，截图见附件）

### 1. 健康检查
- **GET** /api/health → 200 OK

### 2. 统计摘要
- **GET** /api/stats/summary
- 结果：
```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "kbCount": 7,
    "docCount": 0,
    "chunkCount": 0,
    "dailyUploadTrend": []
  }
}
```

### 3. 说明
- 文档/切片数当前为 0，上传文档后自动更新
- 上传趋势数组为空，上传文档后返回近30天每日数据

## 技术说明

- 基于已有 knowledge_base、document、chunk 表直接统计
- 上传趋势按 created_at 日期分组，取近30天
- 未修改任何已有文件（0 改动）