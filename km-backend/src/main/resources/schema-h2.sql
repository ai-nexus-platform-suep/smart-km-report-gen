-- H2 兼容建表脚本（与 Flyway V1__init_km.sql 保持一致）

CREATE TABLE IF NOT EXISTS knowledge_base (
    id                  VARCHAR(36)  NOT NULL PRIMARY KEY,
    name                VARCHAR(100) NOT NULL,
    description         VARCHAR(500) DEFAULT NULL,
    doc_type            VARCHAR(32)  NOT NULL,
    chunk_strategy_json CLOB         NOT NULL,
    search_strategy     VARCHAR(32)  NOT NULL,
    doc_count           INT          NOT NULL DEFAULT 0,
    owner_id            BIGINT       NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS document (
    id          VARCHAR(36)   NOT NULL PRIMARY KEY,
    kb_id       VARCHAR(36)   NOT NULL,
    filename    VARCHAR(255)  NOT NULL,
    file_path   VARCHAR(512)  NOT NULL,
    file_size   BIGINT        DEFAULT 0,
    mime_type   VARCHAR(128)  DEFAULT NULL,
    status      VARCHAR(16)   NOT NULL DEFAULT 'UPLOADED',
    error_msg   VARCHAR(1000) DEFAULT NULL,
    tags_json   CLOB          DEFAULT NULL,
    created_by  BIGINT        NOT NULL,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chunk (
    id            VARCHAR(36)  NOT NULL PRIMARY KEY,
    doc_id        VARCHAR(36)  NOT NULL,
    content       CLOB         NOT NULL,
    chapter_path  VARCHAR(512) DEFAULT NULL,
    chunk_index   INT          NOT NULL,
    chunk_type    VARCHAR(32)  DEFAULT 'paragraph',
    vector_id     VARCHAR(64)  DEFAULT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS system_config (
    config_key   VARCHAR(64)  NOT NULL PRIMARY KEY,
    config_value CLOB         NOT NULL,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_doc_kb ON document(kb_id);
CREATE INDEX IF NOT EXISTS idx_doc_status ON document(status);
CREATE INDEX IF NOT EXISTS idx_chunk_doc ON chunk(doc_id);
