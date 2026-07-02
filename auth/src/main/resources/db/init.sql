-- ============================================================
-- 技术监督辅助平台 - Auth & RBAC 数据库最终版（一键初始化）
-- ============================================================
--
-- 执行：mysql -u root -p < auth/src/main/resources/db/init.sql
--
-- 包含内容：
--   1. 认证：sys_user（账号+资料合一）、refresh_token
--   2. RBAC：sys_role / sys_user_role / sys_permission / sys_role_permission
--           sys_menu（树形，来源 docs/menu(1).sql） / sys_role_menu / sys_log
--   3. 三角色：ROLE_SUPER_ADMIN / ROLE_ADMIN / ROLE_USER
--   4. 初始账号、权限、菜单、角色授权
--
-- 初始账号（密码均为 admin123，部署后请修改）：
--   superadmin → 超级管理员
--   admin      → 管理员
--   user       → 普通用户
-- ============================================================

CREATE DATABASE IF NOT EXISTS auth_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE auth_db;

-- ============================================================
-- 一、表结构 DDL
-- ============================================================

-- 1.1 用户表（账号 + 资料合一）
CREATE TABLE IF NOT EXISTS sys_user (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    username        VARCHAR(50)  NOT NULL COMMENT '登录账号（唯一）',
    password        VARCHAR(255) NOT NULL COMMENT '密码（BCrypt 加密）',
    nickname        VARCHAR(50)           DEFAULT NULL COMMENT '昵称/显示名',
    real_name       VARCHAR(50)           DEFAULT NULL COMMENT '真实姓名',
    email           VARCHAR(100)          DEFAULT NULL COMMENT '邮箱',
    phone           VARCHAR(20)           DEFAULT NULL COMMENT '手机号',
    avatar          VARCHAR(500)          DEFAULT NULL COMMENT '头像 URL',
    gender          TINYINT               DEFAULT 0 COMMENT '性别：0=未知 1=男 2=女',
    remark          VARCHAR(255)          DEFAULT NULL COMMENT '备注',
    enabled         TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用 1=启用 0=禁用',
    deleted         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=正常 1=已删除',
    last_login_at   DATETIME              DEFAULT NULL COMMENT '最后登录时间',
    last_login_ip   VARCHAR(50)           DEFAULT NULL COMMENT '最后登录 IP',
    token_version   BIGINT       NOT NULL DEFAULT 0 COMMENT 'Token 版本号，权限变更时递增，旧JWT失效',
    created_by      BIGINT                DEFAULT NULL COMMENT '创建人 user_id',
    updated_by      BIGINT                DEFAULT NULL COMMENT '更新人 user_id',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_email (email),
    UNIQUE KEY uk_phone (phone),
    KEY idx_enabled (enabled),
    KEY idx_deleted (deleted),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表（账号+资料合一）';

-- 1.2 Refresh Token
CREATE TABLE IF NOT EXISTS refresh_token (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id    BIGINT       NOT NULL COMMENT '关联用户ID',
    token_hash VARCHAR(64)  NOT NULL COMMENT 'Refresh Token SHA256 哈希',
    expires_at DATETIME     NOT NULL COMMENT '过期时间',
    revoked    TINYINT(1)            DEFAULT 0 COMMENT '0=有效 1=已撤销',
    created_at DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_token_hash (token_hash),
    KEY idx_user_id (user_id),
    KEY idx_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Refresh Token 持久化表';

-- 1.3 角色表
CREATE TABLE IF NOT EXISTS sys_role (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    role_code   VARCHAR(50)  NOT NULL COMMENT '角色编码，写入 JWT',
    role_name   VARCHAR(100) NOT NULL COMMENT '角色名称',
    description VARCHAR(255)          DEFAULT NULL COMMENT '角色描述',
    data_scope  TINYINT      NOT NULL DEFAULT 1 COMMENT '数据范围：1=全部 2=本部门 3=本人',
    enabled     TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
    sort_order  INT          NOT NULL DEFAULT 0 COMMENT '排序',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code),
    KEY idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色表';

-- 1.4 用户-角色
CREATE TABLE IF NOT EXISTS sys_user_role (
    id         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id    BIGINT   NOT NULL COMMENT '用户ID',
    role_id    BIGINT   NOT NULL COMMENT '角色ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id),
    KEY idx_user_id (user_id),
    KEY idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

-- 1.5 权限表（API + 按钮）
CREATE TABLE IF NOT EXISTS sys_permission (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    parent_id   BIGINT        NOT NULL DEFAULT 0 COMMENT '父权限ID，0=顶级',
    perm_code   VARCHAR(100)  NOT NULL COMMENT '权限编码',
    perm_name   VARCHAR(100)  NOT NULL COMMENT '权限名称',
    perm_type   VARCHAR(20)   NOT NULL DEFAULT 'API' COMMENT 'API / BUTTON',
    http_method VARCHAR(10)            DEFAULT NULL COMMENT 'HTTP 方法',
    api_path    VARCHAR(255)           DEFAULT NULL COMMENT '接口路径（Ant 通配）',
    module      VARCHAR(50)            DEFAULT NULL COMMENT '模块：auth/chat/km/report',
    description VARCHAR(255)           DEFAULT NULL COMMENT '描述',
    enabled     TINYINT(1)    NOT NULL DEFAULT 1,
    sort_order  INT           NOT NULL DEFAULT 0,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_perm_code (perm_code),
    KEY idx_parent_id (parent_id),
    KEY idx_perm_type (perm_type),
    KEY idx_module (module),
    KEY idx_api_path (api_path(100))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统权限表';

-- 1.6 角色-权限
CREATE TABLE IF NOT EXISTS sys_role_permission (
    id            BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    role_id       BIGINT   NOT NULL COMMENT '角色ID',
    permission_id BIGINT   NOT NULL COMMENT '权限ID',
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_permission (role_id, permission_id),
    KEY idx_role_id (role_id),
    KEY idx_permission_id (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

-- 1.7 菜单表（树形 + Vue Router）
-- parent_id + ancestors + level；menu_type: DIR / MENU / BUTTON
CREATE TABLE IF NOT EXISTS sys_menu (
    id           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    parent_id    BIGINT        NOT NULL DEFAULT 0 COMMENT '父菜单ID，0=顶级',
    ancestors    VARCHAR(500)  NOT NULL DEFAULT '0' COMMENT '祖先路径，如 0,12,34',
    level        TINYINT       NOT NULL DEFAULT 1 COMMENT '层级 1/2/3',
    menu_name    VARCHAR(100)  NOT NULL COMMENT '菜单名称',
    menu_code    VARCHAR(100)  NOT NULL COMMENT '菜单编码',
    route_path   VARCHAR(255)           DEFAULT NULL COMMENT 'Vue Router path',
    route_name   VARCHAR(100)           DEFAULT NULL COMMENT 'Vue Router name',
    component    VARCHAR(255)           DEFAULT NULL COMMENT 'Layout 或 views/xxx/index',
    redirect     VARCHAR(255)           DEFAULT NULL COMMENT '目录默认重定向',
    query_param  VARCHAR(500)           DEFAULT NULL COMMENT '路由 query（JSON）',
    perm_code    VARCHAR(100)           DEFAULT NULL COMMENT '关联权限编码',
    menu_type    VARCHAR(20)   NOT NULL DEFAULT 'MENU' COMMENT 'DIR / MENU / BUTTON',
    icon         VARCHAR(100)           DEFAULT NULL COMMENT '图标',
    sort_order   INT           NOT NULL DEFAULT 0,
    visible      TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '侧边栏显示',
    hidden       TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '隐藏路由',
    keep_alive   TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '页面缓存',
    always_show  TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '始终显示父目录',
    is_frame     TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否外链',
    frame_url    VARCHAR(500)           DEFAULT NULL COMMENT '外链地址',
    enabled      TINYINT(1)    NOT NULL DEFAULT 1,
    remark       VARCHAR(255)           DEFAULT NULL,
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_menu_code (menu_code),
    KEY idx_parent_id (parent_id),
    KEY idx_ancestors (ancestors(191)),
    KEY idx_level (level),
    KEY idx_perm_code (perm_code),
    KEY idx_menu_type (menu_type),
    KEY idx_sort_order (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统菜单表';

-- 1.8 角色-菜单
CREATE TABLE IF NOT EXISTS sys_role_menu (
    id         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    role_id    BIGINT   NOT NULL COMMENT '角色ID',
    menu_id    BIGINT   NOT NULL COMMENT '菜单ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_menu (role_id, menu_id),
    KEY idx_role_id (role_id),
    KEY idx_menu_id (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色菜单关联表';

-- 1.9 操作日志
CREATE TABLE IF NOT EXISTS sys_log (
    id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id        BIGINT                 DEFAULT NULL COMMENT '操作用户ID',
    username       VARCHAR(50)            DEFAULT NULL COMMENT '操作用户名',
    module         VARCHAR(50)            DEFAULT NULL COMMENT '模块',
    operation      VARCHAR(100)           DEFAULT NULL COMMENT '操作描述',
    method         VARCHAR(200)           DEFAULT NULL COMMENT '类名.方法',
    request_uri    VARCHAR(500)           DEFAULT NULL COMMENT '请求 URI',
    request_method VARCHAR(10)            DEFAULT NULL COMMENT 'HTTP 方法',
    request_params TEXT                   DEFAULT NULL COMMENT '请求参数',
    response_code  INT                    DEFAULT NULL COMMENT '业务响应码',
    status         TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '1=成功 0=失败',
    error_msg      TEXT                   DEFAULT NULL COMMENT '错误信息',
    request_ip     VARCHAR(50)            DEFAULT NULL COMMENT '客户端 IP',
    user_agent     VARCHAR(500)           DEFAULT NULL COMMENT 'User-Agent',
    cost_ms        INT                    DEFAULT NULL COMMENT '耗时 ms',
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_username (username),
    KEY idx_module (module),
    KEY idx_status (status),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统操作日志表';

-- ============================================================
-- 二、初始用户
-- BCrypt(admin123): $2b$10$AzF/zuUg9UmBHcsxsiD.teyhV9XiL6FrTJjrvmK3oIeiriWn1PLXe
-- ============================================================
INSERT IGNORE INTO sys_user (username, password, nickname, real_name, enabled, remark)
VALUES
('superadmin', '$2b$10$AzF/zuUg9UmBHcsxsiD.teyhV9XiL6FrTJjrvmK3oIeiriWn1PLXe', '超级管理员', '超级管理员', 1, '初始超级管理员'),
('admin',      '$2b$10$AzF/zuUg9UmBHcsxsiD.teyhV9XiL6FrTJjrvmK3oIeiriWn1PLXe', '管理员',     '业务管理员',   1, '初始管理员'),
('user',       '$2b$10$AzF/zuUg9UmBHcsxsiD.teyhV9XiL6FrTJjrvmK3oIeiriWn1PLXe', '普通用户',   '演示用户',     1, '初始普通用户');

-- ============================================================
-- 三、三角色定义
-- ROLE_SUPER_ADMIN 超级管理员 | ROLE_ADMIN 管理员 | ROLE_USER 普通用户
-- ============================================================
INSERT INTO sys_role (role_code, role_name, description, sort_order)
SELECT 'ROLE_SUPER_ADMIN', '超级管理员', '拥有系统全部权限，含角色、菜单、权限配置', 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'ROLE_SUPER_ADMIN');

INSERT INTO sys_role (role_code, role_name, description, sort_order)
SELECT 'ROLE_ADMIN', '管理员', '用户管理 + 全业务模块，不含系统底层配置', 2
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'ROLE_ADMIN');

INSERT INTO sys_role (role_code, role_name, description, sort_order)
SELECT 'ROLE_USER', '普通用户', '基础业务使用与只读', 3
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'ROLE_USER');

INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
INNER JOIN sys_role r ON (
    (u.username = 'superadmin' AND r.role_code = 'ROLE_SUPER_ADMIN') OR
    (u.username = 'admin'      AND r.role_code = 'ROLE_ADMIN') OR
    (u.username = 'user'       AND r.role_code = 'ROLE_USER')
);

-- ============================================================
-- 四、权限数据
-- ============================================================

-- auth 模块
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, http_method, api_path, module, sort_order)
SELECT 0, 'auth', '认证管理', 'API', NULL, NULL, 'auth', 100
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'auth');

INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, http_method, api_path, module, sort_order)
SELECT p.id, 'auth:user:list', '用户列表', 'API', 'GET', '/api/auth/users/**', 'auth', 101 FROM sys_permission p WHERE p.perm_code = 'auth'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'auth:user:list');
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, http_method, api_path, module, sort_order)
SELECT p.id, 'auth:user:create', '新增用户', 'API', 'POST', '/api/auth/users', 'auth', 102 FROM sys_permission p WHERE p.perm_code = 'auth'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'auth:user:create');
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, http_method, api_path, module, sort_order)
SELECT p.id, 'auth:user:update', '编辑用户', 'API', 'PUT', '/api/auth/users/**', 'auth', 103 FROM sys_permission p WHERE p.perm_code = 'auth'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'auth:user:update');
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, http_method, api_path, module, sort_order)
SELECT p.id, 'auth:user:delete', '删除用户', 'API', 'DELETE', '/api/auth/users/**', 'auth', 104 FROM sys_permission p WHERE p.perm_code = 'auth'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'auth:user:delete');
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, http_method, api_path, module, sort_order)
SELECT p.id, 'auth:role:list', '角色列表', 'API', 'GET', '/api/auth/roles/**', 'auth', 105 FROM sys_permission p WHERE p.perm_code = 'auth'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'auth:role:list');
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, http_method, api_path, module, sort_order)
SELECT p.id, 'auth:role:assign', '分配角色', 'API', 'POST', '/api/auth/users/*/roles', 'auth', 106 FROM sys_permission p WHERE p.perm_code = 'auth'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'auth:role:assign');
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, http_method, api_path, module, sort_order)
SELECT p.id, 'auth:menu:list', '菜单列表', 'API', 'GET', '/api/auth/menus/**', 'auth', 107 FROM sys_permission p WHERE p.perm_code = 'auth'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'auth:menu:list');
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, http_method, api_path, module, sort_order)
SELECT p.id, 'auth:log:list', '操作日志', 'API', 'GET', '/api/auth/logs/**', 'auth', 108 FROM sys_permission p WHERE p.perm_code = 'auth'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'auth:log:list');
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, http_method, api_path, module, sort_order)
SELECT p.id, 'auth:role:manage', '角色增删改', 'API', '*', '/api/auth/roles/**', 'auth', 109 FROM sys_permission p WHERE p.perm_code = 'auth'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'auth:role:manage');
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, http_method, api_path, module, sort_order)
SELECT p.id, 'auth:menu:manage', '菜单增删改', 'API', '*', '/api/auth/menus/**', 'auth', 110 FROM sys_permission p WHERE p.perm_code = 'auth'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'auth:menu:manage');
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, http_method, api_path, module, sort_order)
SELECT p.id, 'auth:permission:manage', '权限配置', 'API', '*', '/api/auth/permissions/**', 'auth', 111 FROM sys_permission p WHERE p.perm_code = 'auth'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'auth:permission:manage');

-- chat 模块
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, http_method, api_path, module, sort_order)
SELECT 0, 'chat', '智能问答', 'API', NULL, NULL, 'chat', 200 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'chat');
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, http_method, api_path, module, sort_order)
SELECT p.id, 'chat:conversation:use', '会话对话', 'API', '*', '/api/conversations/**,/api/chat/**', 'chat', 201 FROM sys_permission p WHERE p.perm_code = 'chat'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'chat:conversation:use');
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, http_method, api_path, module, sort_order)
SELECT p.id, 'chat:model:view', '查看模型配置', 'API', 'GET', '/api/model-configs/**', 'chat', 202 FROM sys_permission p WHERE p.perm_code = 'chat'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'chat:model:view');
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, http_method, api_path, module, sort_order)
SELECT p.id, 'chat:model:manage', '管理模型配置', 'API', '*', '/api/model-configs/**', 'chat', 203 FROM sys_permission p WHERE p.perm_code = 'chat'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'chat:model:manage');
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, http_method, api_path, module, sort_order)
SELECT p.id, 'chat:stats:view', '查看 QA 统计', 'API', 'GET', '/api/stats/qa/**', 'chat', 204 FROM sys_permission p WHERE p.perm_code = 'chat'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'chat:stats:view');
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, module, sort_order)
SELECT p.id, 'chat:model:create', '新增模型配置', 'BUTTON', 'chat', 205 FROM sys_permission p WHERE p.perm_code = 'chat:model:manage'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'chat:model:create');
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, module, sort_order)
SELECT p.id, 'chat:model:edit', '编辑模型配置', 'BUTTON', 'chat', 206 FROM sys_permission p WHERE p.perm_code = 'chat:model:manage'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'chat:model:edit');
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, module, sort_order)
SELECT p.id, 'chat:model:delete', '删除模型配置', 'BUTTON', 'chat', 207 FROM sys_permission p WHERE p.perm_code = 'chat:model:manage'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'chat:model:delete');

-- 菜单页面对应的细粒度权限码（用于前端菜单可见性控制，对齐 sys_menu.perm_code）
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, module, sort_order)
SELECT p.id, 'qa:settings:view', '查看问答配置页', 'BUTTON', 'chat', 210 FROM sys_permission p WHERE p.perm_code = 'chat'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'qa:settings:view');
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, module, sort_order)
SELECT p.id, 'qa:retrieval-test:view', '查看检索测试页', 'BUTTON', 'chat', 211 FROM sys_permission p WHERE p.perm_code = 'chat'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'qa:retrieval-test:view');
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, module, sort_order)
SELECT p.id, 'qa:llm:view', '查看LLM配置页', 'BUTTON', 'chat', 212 FROM sys_permission p WHERE p.perm_code = 'chat'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'qa:llm:view');
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, module, sort_order)
SELECT p.id, 'model:config:view', '查看模型总配置页', 'BUTTON', 'chat', 215 FROM sys_permission p WHERE p.perm_code = 'chat'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'model:config:view');

-- km 模块
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, http_method, api_path, module, sort_order)
SELECT 0, 'km', '知识库', 'API', NULL, NULL, 'km', 300 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'km');
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, http_method, api_path, module, sort_order)
SELECT p.id, 'km:base:view', '查看知识库', 'API', 'GET', '/api/knowledge-bases/**', 'km', 301 FROM sys_permission p WHERE p.perm_code = 'km'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'km:base:view');
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, http_method, api_path, module, sort_order)
SELECT p.id, 'km:base:manage', '管理知识库', 'API', '*', '/api/knowledge-bases/**', 'km', 302 FROM sys_permission p WHERE p.perm_code = 'km'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'km:base:manage');
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, http_method, api_path, module, sort_order)
SELECT p.id, 'km:doc:view', '查看文档', 'API', 'GET', '/api/documents/**', 'km', 303 FROM sys_permission p WHERE p.perm_code = 'km'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'km:doc:view');
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, http_method, api_path, module, sort_order)
SELECT p.id, 'km:doc:manage', '管理文档', 'API', '*', '/api/documents/**', 'km', 304 FROM sys_permission p WHERE p.perm_code = 'km'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'km:doc:manage');
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, http_method, api_path, module, sort_order)
SELECT p.id, 'km:search:use', '知识检索', 'API', 'POST', '/api/search', 'km', 305 FROM sys_permission p WHERE p.perm_code = 'km'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'km:search:use');
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, http_method, api_path, module, sort_order)
SELECT p.id, 'km:stats:view', '知识库统计', 'API', 'GET', '/api/stats/summary', 'km', 306 FROM sys_permission p WHERE p.perm_code = 'km'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'km:stats:view');
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, module, sort_order)
SELECT p.id, 'km:settings:view', '查看知识库配置页', 'BUTTON', 'km', 307 FROM sys_permission p WHERE p.perm_code = 'km'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'km:settings:view');

-- report 模块
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, http_method, api_path, module, sort_order)
SELECT 0, 'report', '报告管理', 'API', NULL, NULL, 'report', 400 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'report');
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, http_method, api_path, module, sort_order)
SELECT p.id, 'report:view', '查看报告', 'API', 'GET', '/api/reports/**', 'report', 401 FROM sys_permission p WHERE p.perm_code = 'report'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'report:view');
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, http_method, api_path, module, sort_order)
SELECT p.id, 'report:manage', '管理报告', 'API', '*', '/api/reports/**', 'report', 402 FROM sys_permission p WHERE p.perm_code = 'report'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'report:manage');
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, http_method, api_path, module, sort_order)
SELECT p.id, 'report:admin', '报告后台管理', 'API', '*', '/api/admin/**', 'report', 403 FROM sys_permission p WHERE p.perm_code = 'report'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'report:admin');

-- 报告模块菜单页面对应的细粒度权限码
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, module, sort_order)
SELECT p.id, 'reports:dashboard:view', '查看报告仪表盘页', 'BUTTON', 'report', 410 FROM sys_permission p WHERE p.perm_code = 'report'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'reports:dashboard:view');
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, module, sort_order)
SELECT p.id, 'reports:templates:view', '查看报告模板页', 'BUTTON', 'report', 411 FROM sys_permission p WHERE p.perm_code = 'report'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'reports:templates:view');
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, module, sort_order)
SELECT p.id, 'reports:materials:view', '查看素材映射页', 'BUTTON', 'report', 412 FROM sys_permission p WHERE p.perm_code = 'report'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'reports:materials:view');

-- 系统管理菜单页面对应的细粒度权限码
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, module, sort_order)
SELECT p.id, 'system:manage', '系统管理入口', 'BUTTON', 'auth', 112 FROM sys_permission p WHERE p.perm_code = 'auth'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'system:manage');
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, module, sort_order)
SELECT p.id, 'system:overview:view', '查看总览统计页', 'BUTTON', 'auth', 113 FROM sys_permission p WHERE p.perm_code = 'auth'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'system:overview:view');
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, module, sort_order)
SELECT p.id, 'system:user:view', '查看用户管理页', 'BUTTON', 'auth', 114 FROM sys_permission p WHERE p.perm_code = 'auth'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'system:user:view');
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, module, sort_order)
SELECT p.id, 'system:role:view', '查看角色权限页', 'BUTTON', 'auth', 115 FROM sys_permission p WHERE p.perm_code = 'auth'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'system:role:view');

-- ============================================================
-- 五、菜单树（parent_id + ancestors + level）
-- 来源：docs/menu(1).sql（platform-web/src/layout/nav.ts → platformNavItems）
-- ============================================================

INSERT INTO sys_menu (
    id, parent_id, ancestors, level, menu_name, menu_code,
    route_path, route_name, component, redirect, query_param, perm_code,
    menu_type, icon, sort_order, visible, hidden, keep_alive,
    always_show, is_frame, frame_url, enabled, remark
) VALUES
    (10000, 0, '0', 1, '平台首页', 'platform:dashboard', '/dashboard', 'Dashboard', 'views/platform/DashboardPage', NULL, NULL, NULL, 'MENU', 'DataAnalysis', 10, 1, 0, 0, 0, 0, NULL, 1, '统一平台首页'),

    (10100, 0, '0', 1, '知识管理', 'platform:km', '/km', 'KnowledgeManagement', 'Layout', '/km/bases', NULL, NULL, 'DIR', 'Collection', 20, 1, 0, 0, 1, 0, NULL, 1, '知识管理一级菜单'),
    (10101, 10100, '0,10100', 2, '知识库管理', 'platform:km:bases', '/km/bases', 'KnowledgeBases', 'views/km/KnowledgeList', NULL, NULL, NULL, 'MENU', NULL, 10, 1, 0, 0, 0, 0, NULL, 1, NULL),
    (10102, 10100, '0,10100', 2, '文档与素材', 'platform:km:resources', '/km/resources', 'KnowledgeResources', 'views/platform/km/DocumentsPage', NULL, NULL, NULL, 'MENU', NULL, 20, 1, 0, 0, 0, 0, NULL, 1, NULL),
    (10103, 10100, '0,10100', 2, '知识检索', 'platform:km:search', '/km/search', 'KnowledgeSearch', 'views/km/SearchPage', NULL, NULL, NULL, 'MENU', NULL, 30, 1, 0, 0, 0, 0, NULL, 1, NULL),

    (10200, 0, '0', 1, '智能问答', 'platform:qa', '/qa', 'QaManagement', 'Layout', '/qa/chat', NULL, NULL, 'DIR', 'ChatDotRound', 30, 1, 0, 0, 1, 0, NULL, 1, '智能问答一级菜单'),
    (10201, 10200, '0,10200', 2, '智能对话', 'platform:qa:chat', '/qa/chat', 'QaChat', 'views/qa/ChatView', NULL, NULL, NULL, 'MENU', NULL, 10, 1, 0, 0, 0, 0, NULL, 1, NULL),
    (10202, 10200, '0,10200', 2, '会话记录', 'platform:qa:conversations', '/qa/conversations', 'QaConversations', 'views/qa/Conversations', NULL, NULL, NULL, 'MENU', NULL, 20, 1, 0, 0, 0, 0, NULL, 1, NULL),
    (10203, 10200, '0,10200', 2, '检索测试', 'platform:qa:retrieval-test', '/qa/retrieval-test', 'QaRetrievalTest', 'views/qa/admin/RetrievalTest', NULL, NULL, 'qa:retrieval-test:view', 'MENU', NULL, 30, 1, 0, 0, 0, 0, NULL, 1, '管理员可见'),
    (10204, 10200, '0,10200', 2, '问答配置', 'platform:qa:settings', '/qa/settings', 'QaSettings', 'views/qa/admin/QaConfig', NULL, NULL, 'qa:settings:view', 'MENU', NULL, 40, 1, 0, 0, 0, 0, NULL, 1, '管理员可见'),

    (10300, 0, '0', 1, '报告生成', 'platform:reports', '/reports', 'ReportManagement', 'Layout', '/reports', NULL, NULL, 'DIR', 'DocumentCopy', 40, 1, 0, 0, 1, 0, NULL, 1, '报告生成一级菜单'),
    (10301, 10300, '0,10300', 2, '报告记录', 'platform:reports:list', '/reports', 'ReportList', 'views/reports/ReportListPage', NULL, NULL, NULL, 'MENU', NULL, 10, 1, 0, 0, 0, 0, NULL, 1, NULL),
    (10302, 10300, '0,10300', 2, '新建报告', 'platform:reports:new', '/reports/new', 'ReportCreate', 'views/reports/NewReportPage', NULL, NULL, NULL, 'MENU', NULL, 20, 1, 0, 0, 0, 0, NULL, 1, NULL),
    (10303, 10300, '0,10300', 2, '趋势统计', 'platform:reports:dashboard', '/reports/dashboard', 'ReportDashboard', 'views/reports/admin/AdminDashboardPage', NULL, NULL, 'reports:dashboard:view', 'MENU', NULL, 30, 1, 0, 0, 0, 0, NULL, 1, '管理员可见'),
    (10304, 10300, '0,10300', 2, '模板管理', 'platform:reports:templates', '/reports/templates', 'ReportTemplates', 'views/reports/admin/TemplateAdminPage', NULL, NULL, 'reports:templates:view', 'MENU', NULL, 40, 1, 0, 0, 0, 0, NULL, 1, '管理员可见'),
    (10305, 10300, '0,10300', 2, '素材映射', 'platform:reports:materials', '/reports/materials', 'ReportMaterials', 'views/platform/PlaceholderPage', NULL, NULL, 'reports:materials:view', 'MENU', NULL, 50, 1, 0, 0, 0, 0, NULL, 1, '管理员可见'),

    (10400, 0, '0', 1, '模型配置', 'platform:model-config', '/model-config', 'ModelConfigManagement', 'Layout', '/km/settings', NULL, 'model:config:view', 'DIR', 'SetUp', 50, 1, 0, 0, 1, 0, NULL, 1, '管理员可见'),
    (10401, 10400, '0,10400', 2, '知识模型配置', 'platform:model-config:km', '/km/settings', 'KnowledgeSettings', 'views/km/admin/EmbedConfig', NULL, NULL, 'km:settings:view', 'MENU', NULL, 10, 1, 0, 0, 0, 0, NULL, 1, '管理员可见'),
    (10402, 10400, '0,10400', 2, 'LLM / 报告模型配置', 'platform:model-config:llm', '/qa/llm', 'QaLlmSettings', 'views/qa/admin/LlmConfig', NULL, NULL, 'qa:llm:view', 'MENU', NULL, 20, 1, 0, 0, 0, 0, NULL, 1, '管理员可见，报告生成复用同一套 LLM 配置'),

    (10500, 0, '0', 1, '系统管理', 'platform:admin', '/admin', 'SystemManagement', 'Layout', '/admin/overview', NULL, 'system:manage', 'DIR', 'Management', 60, 1, 0, 0, 1, 0, NULL, 1, '管理员可见'),
    (10501, 10500, '0,10500', 2, '总览统计', 'platform:admin:overview', '/admin/overview', 'AdminOverview', 'views/platform/admin/SystemOverviewPage', NULL, NULL, 'system:overview:view', 'MENU', NULL, 10, 1, 0, 0, 0, 0, NULL, 1, '管理员可见'),
    (10502, 10500, '0,10500', 2, '用户管理', 'platform:admin:users', '/admin/users', 'AdminUsers', 'views/platform/admin/UserManagementPage', NULL, NULL, 'system:user:view', 'MENU', NULL, 20, 1, 0, 0, 0, 0, NULL, 1, '管理员可见'),
    (10503, 10500, '0,10500', 2, '角色权限', 'platform:admin:roles', '/admin/roles', 'AdminRoles', 'views/platform/admin/RolePermissionPage', NULL, NULL, 'system:role:view', 'MENU', NULL, 30, 1, 0, 0, 0, 0, NULL, 1, '管理员可见')
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    ancestors = VALUES(ancestors),
    level = VALUES(level),
    menu_name = VALUES(menu_name),
    route_path = VALUES(route_path),
    route_name = VALUES(route_name),
    component = VALUES(component),
    redirect = VALUES(redirect),
    query_param = VALUES(query_param),
    perm_code = VALUES(perm_code),
    menu_type = VALUES(menu_type),
    icon = VALUES(icon),
    sort_order = VALUES(sort_order),
    visible = VALUES(visible),
    hidden = VALUES(hidden),
    keep_alive = VALUES(keep_alive),
    always_show = VALUES(always_show),
    is_frame = VALUES(is_frame),
    frame_url = VALUES(frame_url),
    enabled = VALUES(enabled),
    remark = VALUES(remark);

ALTER TABLE sys_menu AUTO_INCREMENT = 11000;

-- ============================================================
-- 六、三角色授权（先清空再重建，保证幂等）
-- 超管=全部 | 管理员=业务+用户/日志 | 普通用户=基础只读
-- ============================================================

-- 6.1 先清空旧的权限/菜单关联数据（使用 JOIN 避免 MySQL 同表子查询限制）
DELETE rp FROM sys_role_permission rp
INNER JOIN sys_role r ON rp.role_id = r.id
WHERE r.role_code IN ('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_USER');

DELETE rm FROM sys_role_menu rm
INNER JOIN sys_role r ON rm.role_id = r.id
WHERE r.role_code IN ('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_USER');

-- 6.2 超级管理员 = 全部权限 + 全部菜单
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code = 'ROLE_SUPER_ADMIN' AND p.enabled = 1 AND p.perm_type IN ('API', 'BUTTON');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role r CROSS JOIN sys_menu m
WHERE r.role_code = 'ROLE_SUPER_ADMIN' AND m.enabled = 1;

-- 6.3 管理员 = 用户管理 + 全业务模块 + 管理菜单可见
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r
INNER JOIN sys_permission p ON p.perm_code IN (
    'auth:user:list','auth:user:create','auth:user:update','auth:user:delete',
    'auth:role:list','auth:role:assign','auth:log:list',
    'chat:conversation:use','chat:model:view','chat:model:manage',
    'chat:model:create','chat:model:edit','chat:model:delete','chat:stats:view',
    'qa:settings:view','qa:retrieval-test:view','qa:llm:view','model:config:view',
    'km:base:view','km:base:manage','km:doc:view','km:doc:manage','km:search:use',
    'km:stats:view','km:settings:view',
    'report:view','report:manage','report:admin',
    'reports:dashboard:view','reports:templates:view','reports:materials:view',
    'system:manage','system:overview:view','system:user:view','system:role:view'
) WHERE r.role_code = 'ROLE_ADMIN';

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role r
INNER JOIN sys_menu m ON m.menu_code IN (
    'platform:dashboard',
    'platform:km','platform:km:bases','platform:km:resources','platform:km:search',
    'platform:qa','platform:qa:chat','platform:qa:conversations',
    'platform:qa:retrieval-test','platform:qa:settings',
    'platform:reports','platform:reports:list','platform:reports:new',
    'platform:reports:dashboard','platform:reports:templates','platform:reports:materials',
    'platform:model-config','platform:model-config:km','platform:model-config:llm',
    'platform:admin','platform:admin:overview','platform:admin:users','platform:admin:roles'
) WHERE r.role_code = 'ROLE_ADMIN';

-- 6.4 普通用户 = 基础业务只读 + 模型配置查看
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r
INNER JOIN sys_permission p ON p.perm_code IN (
    'chat:conversation:use','chat:model:view','chat:stats:view',
    'km:base:view','km:doc:view','km:search:use','report:view',
    'qa:llm:view','model:config:view'
) WHERE r.role_code = 'ROLE_USER';

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role r
INNER JOIN sys_menu m ON m.menu_code IN (
    'platform:dashboard',
    'platform:km','platform:km:bases','platform:km:resources','platform:km:search',
    'platform:qa','platform:qa:chat','platform:qa:conversations',
    'platform:reports','platform:reports:list','platform:reports:new',
    'platform:model-config','platform:model-config:km','platform:model-config:llm'
) WHERE r.role_code = 'ROLE_USER';

