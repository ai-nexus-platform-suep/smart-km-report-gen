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
