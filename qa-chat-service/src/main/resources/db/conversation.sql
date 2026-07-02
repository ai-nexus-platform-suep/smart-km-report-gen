-- 与 docs/sql/V1__init_qa_session.sql 保持同步

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS qa_message;
DROP TABLE IF EXISTS qa_session;

CREATE TABLE qa_session (
    id              BIGINT        NOT NULL                COMMENT 'SessionId，雪花ID',
    user_id         BIGINT        NOT NULL                COMMENT '用户ID',
    title           VARCHAR(200)  NOT NULL DEFAULT '新对话' COMMENT '会话标题',
    status          TINYINT       NOT NULL DEFAULT 1      COMMENT '状态: 1=正常 0=已删除',
    message_count   INT UNSIGNED  NOT NULL DEFAULT 0      COMMENT '消息条数（冗余，便于列表展示）',
    last_message_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后一条消息时间（新建会话与 created_at 一致）',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_at      DATETIME      NULL                    COMMENT '删除时间（逻辑删除时写入）',
    PRIMARY KEY (id),
    CONSTRAINT chk_session_status CHECK (status IN (0, 1)),
    CONSTRAINT chk_session_message_count CHECK (message_count >= 0),
    KEY idx_user_list (user_id, status, last_message_at DESC, id DESC),
    KEY idx_status_last_msg (status, last_message_at DESC, id DESC)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='智能问答会话表';

CREATE TABLE qa_message (
    id               BIGINT        NOT NULL               COMMENT 'MessageId，雪花ID',
    session_id       BIGINT        NOT NULL               COMMENT 'SessionId，关联 qa_session.id',
    user_id          BIGINT        NOT NULL               COMMENT '用户ID（冗余，便于鉴权与统计）',
    seq              INT UNSIGNED  NOT NULL               COMMENT '会话内消息序号，从 1 递增',
    role             VARCHAR(20)   NOT NULL               COMMENT '角色: user / assistant / system',
    content          MEDIUMTEXT    NOT NULL               COMMENT '消息正文',
    intent_type      VARCHAR(50)   NULL                   COMMENT '意图类型，如 KNOWLEDGE_QA / CHAT',
    thinking_steps   JSON          NULL                   COMMENT '思考过程步骤（JSON 数组）',
    citations        JSON          NULL                   COMMENT '引用溯源（JSON 数组）',
    generate_status  TINYINT       NOT NULL DEFAULT 1     COMMENT '生成状态: 0=生成中 1=已完成 2=失败',
    token_usage      INT UNSIGNED  NULL                   COMMENT 'Token 消耗（可选）',
    status           TINYINT       NOT NULL DEFAULT 1     COMMENT '状态: 1=正常 0=已删除',
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_at       DATETIME      NULL                   COMMENT '删除时间（逻辑删除时写入）',
    PRIMARY KEY (id),
    CONSTRAINT fk_message_session
        FOREIGN KEY (session_id) REFERENCES qa_session (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_message_status CHECK (status IN (0, 1)),
    CONSTRAINT chk_message_generate_status CHECK (generate_status IN (0, 1, 2)),
    CONSTRAINT chk_message_role CHECK (role IN ('user', 'assistant', 'system')),
    CONSTRAINT chk_message_seq CHECK (seq >= 1),
    UNIQUE KEY uk_session_seq (session_id, seq),
    KEY idx_session_list (session_id, status, seq ASC),
    KEY idx_user_session (user_id, session_id),
    KEY idx_knowledge_qa_stat (intent_type, role, status, created_at),
    KEY idx_status_created (status, created_at DESC)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='智能问答消息表';

SET FOREIGN_KEY_CHECKS = 1;
