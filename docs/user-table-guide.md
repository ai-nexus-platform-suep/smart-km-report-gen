# 用户表（user）使用指南

## 概述

`user` 表是系统的核心基础表，位于 `qa-common` 公共模块，供所有子模块（知识管理、报表生成、技术监督等）共用。各子模块的业务表可通过 `user_id` 外键关联本表，无需各自维护用户数据。

- **所属模块**：`qa-common`
- **数据源**：主数据库
- **存储引擎**：InnoDB
- **字符集**：utf8mb4（支持 emoji 及多语言）

---

## 表结构

| 字段 | 类型 | 是否必填 | 默认值 | 说明 |
|------|------|:---:|--------|------|
| `id` | BIGINT | Y | AUTO_INCREMENT | 用户唯一标识，主键 |
| `username` | VARCHAR(50) | Y | — | 登录账号，唯一索引，建议使用英文/数字/下划线 |
| `password` | VARCHAR(255) | Y | — | 加密后的密码，**必须使用 BCrypt** 加密存储，禁止明文 |
| `nickname` | VARCHAR(50) | N | NULL | 用户昵称，用于页面展示 |
| `email` | VARCHAR(100) | N | NULL | 邮箱地址，可用于找回密码 |
| `phone` | VARCHAR(20) | N | NULL | 手机号码，可用于短信通知 |
| `avatar` | VARCHAR(255) | N | NULL | 头像图片 URL |
| `role` | VARCHAR(20) | Y | `USER` | 角色标识，控制权限 |
| `status` | TINYINT | Y | `1` | 账号状态 |
| `token` | VARCHAR(500) | N | NULL | JWT 令牌，用于无状态会话 |
| `last_login_time` | DATETIME | N | NULL | 最近登录时间 |
| `created_at` | DATETIME | Y | CURRENT_TIMESTAMP | 记录创建时间（自动填充） |
| `updated_at` | DATETIME | Y | CURRENT_TIMESTAMP | 记录更新时间（自动更新） |

---

## 字段详解

### `id` — 用户唯一标识
自增主键，全局唯一。各业务模块通过此字段与用户进行关联。例如下游表添加 `creator_id` 或 `user_id` 字段即可建立关联。
```
用户表 id = 1  →  知识条目表 creator_id = 1  →  表示该条目由 id=1 的用户创建
```

### `username` — 登录账号
- 系统登录的唯一凭证，**不允许重复**（设有唯一索引 `uk_username`）。
- 建议格式：小写英文字母 + 数字 + 下划线，长度 4~50 字符。
- 用于登录认证、用户搜索等场景。

### `password` — 登录密码
- 存储的是 **BCrypt 加密后的密文**，绝对不能存储明文密码。
- Spring Security 环境下推荐使用 `PasswordEncoder`（BCryptPasswordEncoder）进行加密和校验。
- 密码强度建议：至少 8 位，包含大小写字母、数字和特殊字符。

### `nickname` — 显示名称
- 在页面右上角、评论区、报表署名等处展示。
- 可与 `username` 不同，支持中文和特殊字符。
- 允许为空，为空时前端可回退显示 `username`。

### `email` / `phone` — 联系方式
- 用于密码找回、重要通知、验证码发送等。
- 均为可选字段，但建议至少填写一项以便账号恢复。
- `phone` 建议存储格式：`+86-13800138000` 或纯数字 `13800138000`。

### `avatar` — 头像
- 存储头像图片的 URL 地址（相对路径或完整 URL）。
- 推荐使用对象存储服务（如 MinIO、OSS）上传后写入此字段。

### `role` — 角色
用于前端路由控制和后端接口权限校验。当前预定义两种角色：

| 值 | 含义 | 典型权限 |
|----|------|----------|
| `USER` | 普通用户 | 查看知识库、生成报表、编辑自己的内容 |
| `ADMIN` | 系统管理员 | 用户管理、系统配置、所有数据的增删改查 |

后续如需更细粒度控制，可配合权限码表（permission）实现 RBAC。

### `status` — 账号状态

| 值 | 含义 | 行为 |
|:--:|------|------|
| `1` | 正常 | 可正常登录和使用系统 |
| `0` | 已禁用 | 无法登录，客户端收到 `ACCOUNT_DISABLED`（1006）错误码 |

```java
// 校验示例
if (user.getStatus() == 0) {
    return ApiResponse.error(ApiCode.ACCOUNT_DISABLED, "账号已被禁用");
}
```

### `token` — JWT 令牌
- 用户登录成功后生成，存储于数据库以便服务端可控吊销。
- 校验时先验证 JWT 签名和有效期，再对比数据库中的 token 值。
- Token 相关错误码：`INVALID_TOKEN(1004)`、`TOKEN_EXPIRED(1005)`。

### `last_login_time` — 最近登录时间
- 每次登录成功时更新，用于用户活跃度统计和安全审计。

### `created_at` / `updated_at` — 时间戳
- **自动维护**，无需在业务代码中手动赋值。
- `created_at`：记录创建时自动填入当前时间，插入后不再变化。
- `updated_at`：每次 UPDATE 操作时自动更新为当前时间（MySQL `ON UPDATE CURRENT_TIMESTAMP`）。

---

## 索引说明

| 索引名 | 类型 | 字段 | 用途 |
|--------|:---:|------|------|
| `PRIMARY` | 主键 | `id` | 唯一标识，聚簇索引 |
| `uk_username` | 唯一 | `username` | 保证登录账号唯一，加速登录查询 |
| `idx_status` | 普通 | `status` | 按状态筛选用户（如查询所有禁用账号） |
| `idx_role` | 普通 | `role` | 按角色筛选用户（如查询所有管理员） |

---

## Java 实体类

实体类位于：`qa-common/src/main/java/com/myenglish/qacommon/entity/User.java`

使用 Lombok `@Data` 自动生成 getter/setter/toString/equals/hashCode，类型映射如下：

| SQL 类型 | Java 类型 |
|----------|-----------|
| BIGINT | `Long` |
| VARCHAR | `String` |
| TINYINT | `Integer` |
| DATETIME | `LocalDateTime` |

---

## 子模块关联示例

假设知识管理模块有一张 `knowledge_entry` 表，关联用户作为创建人：

```sql
CREATE TABLE knowledge_entry (
    id          BIGINT  NOT NULL AUTO_INCREMENT,
    title       VARCHAR(200) NOT NULL,
    content     TEXT,
    creator_id  BIGINT  NOT NULL  COMMENT '创建人ID，关联 user.id',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_creator (creator_id),
    CONSTRAINT fk_entry_creator FOREIGN KEY (creator_id) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

```java
// 查询时 JOIN 即可获取创建人姓名
SELECT e.*, u.nickname AS creator_name
FROM knowledge_entry e
LEFT JOIN user u ON e.creator_id = u.id
WHERE e.id = #{entryId}
```

---

## 注意事项

1. **密码安全**：密码必须 BCrypt 加密，日志中禁止打印密码（含密文）。
2. **Token 管理**：登出或修改密码时应清空 `token` 字段，使旧令牌失效。
3. **外键策略**：建议各业务模块使用逻辑外键（仅存储 `user_id`，由应用层维护引用完整性），避免数据库级外键带来的性能和维护成本。
4. **字段扩展**：当前字段设计覆盖了基础用户管理和认证场景，如需新增字段（如部门、岗位等），请在当前表上追加，避免各模块自行创建用户扩展表。
5. **SQL 脚本位置**：`qa-common/src/main/resources/sql/user.sql`，首次部署时执行即可。
