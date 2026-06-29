-- 知识管理子系统初始化表结构 v1
-- 用户表见 docs/user.sql（qa-common 公共模块，各子系统共用，不在此重复建表）

CREATE TABLE IF NOT EXISTS `knowledge_base` (
    `id`                  VARCHAR(36)  NOT NULL PRIMARY KEY,
    `name`                VARCHAR(100) NOT NULL,
    `description`         VARCHAR(500) DEFAULT NULL,
    `doc_type`            VARCHAR(32)  NOT NULL COMMENT '规程规范/技术报告论文/术语条目/通用文档',
    `chunk_strategy_json` JSON         NOT NULL,
    `search_strategy`     VARCHAR(32)  NOT NULL COMMENT 'vector / vector_rerank',
    `doc_count`           INT          NOT NULL DEFAULT 0 COMMENT '文档数量，上传/删除时维护',
    `owner_id`            BIGINT       NOT NULL COMMENT '创建人，关联 user.id',
    `created_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_kb_owner (`owner_id`),
    INDEX idx_kb_type (`doc_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `document` (
    `id`          VARCHAR(36)   NOT NULL PRIMARY KEY,
    `kb_id`       VARCHAR(36)   NOT NULL,
    `filename`    VARCHAR(255)  NOT NULL,
    `file_path`   VARCHAR(512)  NOT NULL COMMENT 'MinIO 对象路径',
    `file_size`   BIGINT        DEFAULT 0,
    `mime_type`   VARCHAR(128)  DEFAULT NULL,
    `status`      VARCHAR(16)   NOT NULL DEFAULT 'UPLOADED',
    `error_msg`   VARCHAR(1000) DEFAULT NULL,
    `tags_json`   JSON          DEFAULT NULL,
    `created_by`  BIGINT        NOT NULL COMMENT '上传人，关联 user.id',
    `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_doc_kb (`kb_id`),
    INDEX idx_doc_status (`status`),
    CONSTRAINT fk_doc_kb FOREIGN KEY (`kb_id`) REFERENCES `knowledge_base`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `chunk` (
    `id`            VARCHAR(36)  NOT NULL PRIMARY KEY,
    `doc_id`        VARCHAR(36)  NOT NULL,
    `content`       TEXT         NOT NULL,
    `chapter_path`  VARCHAR(512) DEFAULT NULL,
    `chunk_index`   INT          NOT NULL,
    `chunk_type`    VARCHAR(32)  DEFAULT 'paragraph',
    `vector_id`     VARCHAR(64)  DEFAULT NULL,
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_chunk_doc (`doc_id`),
    CONSTRAINT fk_chunk_doc FOREIGN KEY (`doc_id`) REFERENCES `document`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `system_config` (
    `config_key`   VARCHAR(64)  NOT NULL PRIMARY KEY,
    `config_value` JSON         NOT NULL,
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
