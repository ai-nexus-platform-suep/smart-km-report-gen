-- ============================================================
-- 技术监督辅助平台 - Gateway 模块数据库初始化脚本
-- ============================================================

-- 创建数据库（如尚未创建）
-- CREATE DATABASE IF NOT EXISTS tech_supervision DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT '密码（BCrypt加密）',
    roles VARCHAR(255) NOT NULL DEFAULT 'ROLE_USER' COMMENT '角色，逗号分隔，如 ROLE_ADMIN,ROLE_USER',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用 1=启用 0=禁用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

-- Refresh Token 持久化表
CREATE TABLE IF NOT EXISTS refresh_token (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    username VARCHAR(100) NOT NULL COMMENT '关联的用户名',
    token_hash VARCHAR(64) NOT NULL COMMENT 'Refresh Token 的 SHA256 哈希',
    expires_at DATETIME NOT NULL COMMENT '过期时间',
    revoked TINYINT(1) DEFAULT 0 COMMENT '是否已撤销 0=未撤销 1=已撤销',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_token_hash (token_hash),
    INDEX idx_username (username),
    INDEX idx_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Refresh Token 持久化表';
