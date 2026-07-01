# 系统管理后端待实现功能清单

更新时间：2026-07-01

对应前端入口：

- `/admin/overview`：系统管理总览
- `/admin/users`：用户管理
- `/admin/roles`：角色权限

当前状态说明：

- 这三个页面目前是前端静态骨架，数据全部写在 Vue 页面中，没有接真实后端接口。
- 当前前端权限只做了基础登录判断和 `ADMIN / USER` 路由拦截，尚未接入后端菜单权限、按钮权限、能力点权限。
- 建议后端先交付 P0 接口，前端即可把静态数据替换为真实数据。

## 优先级说明

- `P0`：前端页面能正式使用必须要有。
- `P1`：管理体验完整化，建议第二批实现。
- `P2`：审计、安全、批量操作等增强能力。

## 1. 通用约定

建议统一响应格式：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

分页响应建议：

```json
{
  "items": [],
  "page": 1,
  "pageSize": 10,
  "total": 36
}
```

建议统一枚举：

```ts
type UserStatus = "ACTIVE" | "PENDING" | "DISABLED" | "LOCKED"
type RoleCode = "SUPER_ADMIN" | "ADMIN" | "USER" | string
type PermissionAction = "view" | "edit" | "config" | "delete" | "approve" | "export"
```

## 2. 系统管理总览 `/admin/overview`

### 2.1 P0：总览统计接口

当前前端静态数据：

- 平台用户数量
- 管理员数量
- 角色类型数量
- 受控菜单数量

建议接口：

```http
GET /api/admin/overview/summary
```

建议返回：

```json
{
  "userTotal": 36,
  "adminTotal": 6,
  "roleTotal": 4,
  "protectedMenuTotal": 21,
  "userWeeklyDelta": 4
}
```

### 2.2 P0：模块接入与权限边界

当前前端静态展示：

- 知识管理、智能问答、报告生成三个模块的功能范围、状态、适用角色、权限说明。

建议接口：

```http
GET /api/admin/overview/modules
```

建议返回：

```json
[
  {
    "moduleCode": "KM",
    "moduleName": "知识管理",
    "scope": "知识库 / 文档 / 检索",
    "status": "ENABLED",
    "roleScope": ["SUPER_ADMIN", "ADMIN", "USER"],
    "permissionSummary": "普通用户可访问，模型配置管理员可见"
  }
]
```

### 2.3 P1：安全状态

当前前端静态展示：

- 统一登录状态
- 路由守卫状态
- 菜单隐藏状态
- 接口鉴权状态

建议接口：

```http
GET /api/admin/overview/security
```

建议返回：

```json
[
  { "key": "login", "label": "统一登录", "status": "OK", "value": "已启用" },
  { "key": "routeGuard", "label": "路由守卫", "status": "OK", "value": "ADMIN / USER" },
  { "key": "apiAuth", "label": "接口鉴权", "status": "WARNING", "value": "部分接口待接入" }
]
```

### 2.4 P1：最近管理动作

当前前端静态展示最近管理事件。

建议接口：

```http
GET /api/admin/audit-logs?module=SYSTEM&page=1&pageSize=5
```

建议字段：

- `id`
- `operatorId`
- `operatorName`
- `action`
- `targetType`
- `targetName`
- `detail`
- `ip`
- `result`
- `createdAt`

## 3. 用户管理 `/admin/users`

### 3.1 P0：用户列表查询

当前前端需要：

- 关键词搜索：姓名、账号
- 角色筛选
- 状态筛选
- 分页
- 表格字段：用户、账号、角色、可访问模块、状态、最近活跃

建议接口：

```http
GET /api/admin/users?keyword=&role=&status=&page=1&pageSize=10
```

建议返回：

```json
{
  "items": [
    {
      "id": "u-001",
      "username": "admin",
      "nickname": "系统管理员",
      "email": "admin@example.com",
      "phone": "13800000000",
      "roles": [
        { "id": "r-admin", "code": "ADMIN", "name": "管理员" }
      ],
      "status": "ACTIVE",
      "lastActiveAt": "2026-07-01T11:20:00+08:00",
      "createdAt": "2026-06-20T09:00:00+08:00"
    }
  ],
  "page": 1,
  "pageSize": 10,
  "total": 36
}
```

### 3.2 P0：用户详情

当前右侧详情卡需要：

- 用户基础信息
- 当前角色
- 账号状态
- 最近活跃
- 可访问模块

建议接口：

```http
GET /api/admin/users/{userId}
```

建议返回：

```json
{
  "id": "u-001",
  "username": "admin",
  "nickname": "系统管理员",
  "email": "admin@example.com",
  "phone": "13800000000",
  "roles": [
    { "id": "r-admin", "code": "ADMIN", "name": "管理员" }
  ],
  "status": "ACTIVE",
  "lastActiveAt": "2026-07-01T11:20:00+08:00",
  "modules": [
    { "code": "SYSTEM", "name": "系统管理" },
    { "code": "KM", "name": "知识管理" },
    { "code": "QA", "name": "智能问答" },
    { "code": "REPORT", "name": "报告生成" }
  ]
}
```

### 3.3 P0：用户新增与编辑

建议接口：

```http
POST /api/admin/users
PUT /api/admin/users/{userId}
```

新增用户请求建议：

```json
{
  "username": "report-user",
  "nickname": "报告生成用户",
  "password": "InitialPassword123",
  "email": "report@example.com",
  "phone": "13800000001",
  "roleIds": ["r-user"],
  "status": "PENDING"
}
```

编辑用户请求建议：

```json
{
  "nickname": "报告生成用户",
  "email": "report@example.com",
  "phone": "13800000001",
  "status": "ACTIVE"
}
```

### 3.4 P0：分配角色

当前页面右侧有“分配角色”入口，需要后端支持。

建议接口：

```http
PUT /api/admin/users/{userId}/roles
```

请求：

```json
{
  "roleIds": ["r-user", "r-reviewer"]
}
```

### 3.5 P1：账号状态操作

建议接口：

```http
POST /api/admin/users/{userId}/enable
POST /api/admin/users/{userId}/disable
POST /api/admin/users/{userId}/unlock
POST /api/admin/users/{userId}/reset-password
```

重置密码请求建议：

```json
{
  "newPassword": "InitialPassword123",
  "forceChangeOnNextLogin": true
}
```

### 3.6 P1：批量导入用户

当前页面有“导入名单”按钮，需要后端支持。

建议接口：

```http
POST /api/admin/users/import
GET /api/admin/users/import-template
```

导入建议支持：

- Excel / CSV 模板下载
- 导入预校验
- 返回成功条数、失败条数、失败原因

### 3.7 P1：用户统计

当前页面顶部统计卡需要：

- 总用户
- 启用中
- 待启用

建议接口：

```http
GET /api/admin/users/summary
```

建议返回：

```json
{
  "total": 36,
  "active": 31,
  "pending": 4,
  "disabled": 1,
  "locked": 0
}
```

## 4. 角色权限 `/admin/roles`

### 4.1 P0：角色列表

当前前端静态角色：

- 超级管理员
- 管理员
- 普通用户

建议接口：

```http
GET /api/admin/roles
```

建议返回：

```json
[
  {
    "id": "r-admin",
    "code": "ADMIN",
    "name": "管理员",
    "description": "维护系统配置、用户、角色和所有业务模块管理能力。",
    "userCount": 6,
    "enabled": true,
    "builtIn": true
  }
]
```

### 4.2 P0：角色详情与权限矩阵

当前页面需要展示：

- 角色说明
- 角色用户数
- 各模块权限矩阵：可查看、可编辑、可配置

建议接口：

```http
GET /api/admin/roles/{roleId}
```

建议返回：

```json
{
  "id": "r-admin",
  "code": "ADMIN",
  "name": "管理员",
  "description": "维护系统配置、用户、角色和所有业务模块管理能力。",
  "userCount": 6,
  "permissions": [
    {
      "moduleCode": "DASHBOARD",
      "moduleName": "平台首页",
      "actions": ["view", "edit", "config"]
    },
    {
      "moduleCode": "SYSTEM",
      "moduleName": "系统管理",
      "actions": ["view", "edit", "config"]
    }
  ]
}
```

### 4.3 P0：保存角色权限

建议接口：

```http
PUT /api/admin/roles/{roleId}/permissions
```

请求：

```json
{
  "permissions": [
    {
      "moduleCode": "KM",
      "actions": ["view", "edit"]
    },
    {
      "moduleCode": "QA",
      "actions": ["view"]
    }
  ]
}
```

### 4.4 P1：角色新增、编辑、删除

建议接口：

```http
POST /api/admin/roles
PUT /api/admin/roles/{roleId}
DELETE /api/admin/roles/{roleId}
```

注意：

- 内置角色如 `ADMIN`、`USER` 建议不允许删除。
- 删除角色前需要检查是否仍有关联用户。
- 角色 `code` 建议唯一。

### 4.5 P1：角色关联用户

建议接口：

```http
GET /api/admin/roles/{roleId}/users?page=1&pageSize=10
```

用途：

- 点击角色时查看该角色下有哪些用户。
- 删除角色前提示影响范围。

### 4.6 P1：菜单树与能力点

当前前端只用模块级矩阵，后续如果要更细，需要后端提供菜单树。

建议接口：

```http
GET /api/admin/permissions/tree
```

建议返回：

```json
[
  {
    "moduleCode": "KM",
    "moduleName": "知识管理",
    "children": [
      {
        "permissionCode": "km:bases:view",
        "label": "知识库管理-查看",
        "action": "view"
      },
      {
        "permissionCode": "km:settings:config",
        "label": "模型配置-配置",
        "action": "config"
      }
    ]
  }
]
```

## 5. 登录用户与权限守卫

### 5.1 P0：当前登录用户接口增强

当前前端后续需要支持 `role: SUPER_ADMIN | ADMIN | USER`，并由后端返回权限集合。

建议接口：

```http
GET /api/auth/me
```

建议返回：

```json
{
  "id": "u-001",
  "username": "admin",
  "nickname": "系统管理员",
  "email": "admin@example.com",
  "phone": "13800000000",
  "role": "ADMIN",
  "roles": ["ROLE_ADMIN"],
  "status": "ACTIVE",
  "permissions": [
    "system:view",
    "system:user:edit",
    "system:role:config",
    "km:settings:config"
  ],
  "menus": [
    { "path": "/dashboard", "title": "平台首页" },
    { "path": "/admin/users", "title": "用户管理" }
  ]
}
```

### 5.2 P0：后端接口鉴权

要求：

- 所有 `/api/admin/**` 接口必须校验管理员权限。
- 非管理员访问返回 `403 Forbidden`。
- 未登录或 token 失效返回 `401 Unauthorized`。

建议错误格式：

```json
{
  "code": 403,
  "message": "无权限访问该资源"
}
```

### 5.3 P1：按钮级权限

后续前端需要根据权限控制按钮：

- 新建用户
- 编辑用户
- 停用账号
- 重置密码
- 新建角色
- 编辑角色
- 保存权限矩阵

建议权限码：

```text
system:user:view
system:user:create
system:user:update
system:user:disable
system:user:reset-password
system:role:view
system:role:create
system:role:update
system:role:delete
system:role:permission:update
```

## 6. 审计日志

### 6.1 P1：管理操作审计

建议记录以下动作：

- 新建用户
- 编辑用户
- 启用 / 停用用户
- 重置密码
- 分配角色
- 新建 / 编辑 / 删除角色
- 修改权限矩阵

建议接口：

```http
GET /api/admin/audit-logs?operator=&action=&targetType=&startTime=&endTime=&page=1&pageSize=20
```

建议字段：

```json
{
  "id": "log-001",
  "operatorId": "u-001",
  "operatorName": "系统管理员",
  "action": "USER_ROLE_UPDATE",
  "targetType": "USER",
  "targetId": "u-002",
  "targetName": "技术监督专责",
  "detail": "分配角色：普通用户、管理员",
  "ip": "192.168.1.10",
  "result": "SUCCESS",
  "createdAt": "2026-07-01T11:30:00+08:00"
}
```

## 7. 建议交付顺序

### 第一批 P0

- `GET /api/auth/me`
- `GET /api/admin/users`
- `GET /api/admin/users/{userId}`
- `POST /api/admin/users`
- `PUT /api/admin/users/{userId}`
- `PUT /api/admin/users/{userId}/roles`
- `GET /api/admin/roles`
- `GET /api/admin/roles/{roleId}`
- `PUT /api/admin/roles/{roleId}/permissions`
- `GET /api/admin/overview/summary`
- `/api/admin/**` 后端鉴权

### 第二批 P1

- 用户启用、停用、解锁、重置密码
- 用户导入与模板下载
- 用户统计
- 角色 CRUD
- 角色关联用户
- 权限树
- 安全状态
- 审计日志

### 第三批 P2

- 登录日志
- 操作日志高级筛选
- 批量分配角色
- 批量停用用户
- 权限变更前后 diff
- 数据导出

## 8. 前端联调验收标准

- 用户管理页不再使用静态数组，列表、详情、统计都来自接口。
- 新建用户、编辑用户、分配角色、重置密码至少完成 P0/P1 中对应动作。
- 角色权限页不再使用静态角色数组，角色列表、角色详情、权限矩阵都来自接口。
- 修改角色权限后，刷新页面仍能看到最新权限。
- 非管理员访问 `/admin/overview`、`/admin/users`、`/admin/roles` 被前后端同时拦截。
- 后端返回 `401` 时前端跳转登录页，返回 `403` 时前端显示无权限提示。
- 管理动作进入审计日志，至少能查到操作者、动作、目标对象、时间和结果。
