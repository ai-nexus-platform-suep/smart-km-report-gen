-- ============================================
-- 用户表（公共模块，供各子模块共用）
-- ============================================

CREATE TABLE IF NOT EXISTS `user` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '用户ID，主键',
    `username`        VARCHAR(50)  NOT NULL                 COMMENT '登录账号，唯一',
    `password`        VARCHAR(255) NOT NULL                 COMMENT '登录密码（BCrypt 加密存储）',
    `nickname`        VARCHAR(50)  DEFAULT NULL             COMMENT '用户昵称 / 显示名称',
    `email`           VARCHAR(100) DEFAULT NULL             COMMENT '电子邮箱',
    `phone`           VARCHAR(20)  DEFAULT NULL             COMMENT '手机号码',
    `avatar`          VARCHAR(255) DEFAULT NULL             COMMENT '头像 URL',
    `role`            VARCHAR(20)  NOT NULL DEFAULT 'USER'  COMMENT '角色标识：USER-普通用户, ADMIN-管理员',
    `status`          TINYINT      NOT NULL DEFAULT 1       COMMENT '账号状态：1-正常, 0-已禁用',
    `token`           VARCHAR(500) DEFAULT NULL             COMMENT '当前登录 Token（JWT）',
    `last_login_time` DATETIME     DEFAULT NULL             COMMENT '最近一次登录时间',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_status` (`status`),
    KEY `idx_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表（公共模块）';
