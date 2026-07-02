-- 模型配置表（用户级，支持每用户每场景独立配置）
CREATE TABLE IF NOT EXISTS model_config (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT       NOT NULL                  COMMENT '所属用户ID',
    provider          VARCHAR(50)  NOT NULL DEFAULT 'deepseek' COMMENT '模型供应商',
    base_url          VARCHAR(255) NOT NULL                  COMMENT '模型接口地址（OpenAI-compatible）',
    model_name        VARCHAR(100) NOT NULL                  COMMENT '模型名称',
    api_key_encrypted VARCHAR(500) NOT NULL                  COMMENT 'AES加密后的API Key',
    scenario          VARCHAR(50)  NOT NULL DEFAULT 'chat'   COMMENT '使用场景：chat/report_generate',
    enabled           TINYINT(1)   NOT NULL DEFAULT 1        COMMENT '是否启用 1=是 0=否',
    is_default        TINYINT(1)   NOT NULL DEFAULT 0        COMMENT '是否默认 1=是 0=否',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_scenario (user_id, scenario),
    INDEX idx_user_default (user_id, scenario, is_default)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户模型配置表';
