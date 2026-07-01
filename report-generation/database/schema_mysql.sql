-- Power Report Backend - clean MySQL schema
-- Target: MySQL 8.x / InnoDB / utf8mb4
-- Scope: Java Spring Boot backend 1, outline and DOCX export.

CREATE DATABASE IF NOT EXISTS power_report
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE power_report;

CREATE TABLE IF NOT EXISTS reports (
  id CHAR(36) NOT NULL COMMENT '报告 ID，UUID',
  name VARCHAR(200) NOT NULL COMMENT '报告名称',
  type VARCHAR(50) NOT NULL COMMENT '报告类型：SUMMER_PEAK_CHECK / COAL_INVENTORY_AUDIT',
  subject VARCHAR(500) NOT NULL COMMENT '报告主题',
  specialty VARCHAR(100) NOT NULL COMMENT '专业',
  power_plant VARCHAR(200) NOT NULL COMMENT '电厂',
  report_year INT NOT NULL COMMENT '年份',
  status VARCHAR(50) NOT NULL DEFAULT 'DRAFT' COMMENT '报告状态',
  owner_name VARCHAR(100) NOT NULL DEFAULT 'local_user' COMMENT '创建人，未接登录时先用文本字段',
  total_sections INT NOT NULL DEFAULT 0 COMMENT '总章节数',
  completed_sections INT NOT NULL DEFAULT 0 COMMENT '已完成章节数',
  generated_at DATETIME(3) NULL COMMENT '内容生成完成时间',
  deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否软删除',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_reports_type (type),
  KEY idx_reports_status (status),
  KEY idx_reports_year (report_year),
  KEY idx_reports_power_plant (power_plant),
  KEY idx_reports_created_at (created_at),
  KEY idx_reports_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报告记录表';

CREATE TABLE IF NOT EXISTS report_outline_nodes (
  id CHAR(36) NOT NULL COMMENT '大纲节点 ID，UUID',
  report_id CHAR(36) NOT NULL COMMENT '报告 ID',
  parent_id CHAR(36) NULL COMMENT '父节点 ID',
  level INT NOT NULL COMMENT '层级：1=章，2=节，3=子节',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '同级排序',
  number VARCHAR(50) NOT NULL COMMENT '章节编号，例如 2.1.3',
  title VARCHAR(300) NOT NULL COMMENT '章节标题',
  prompt_hint TEXT NULL COMMENT '章节生成提示',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_outline_report_id (report_id),
  KEY idx_outline_parent_id (parent_id),
  KEY idx_outline_report_parent_sort (report_id, parent_id, sort_order),
  KEY idx_outline_report_number (report_id, number),
  CONSTRAINT fk_outline_report
    FOREIGN KEY (report_id) REFERENCES reports(id)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_outline_parent
    FOREIGN KEY (parent_id) REFERENCES report_outline_nodes(id)
    ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报告大纲节点表';

CREATE TABLE IF NOT EXISTS report_sections (
  id CHAR(36) NOT NULL COMMENT '章节内容 ID，UUID',
  report_id CHAR(36) NOT NULL COMMENT '报告 ID',
  outline_node_id CHAR(36) NULL COMMENT '对应大纲节点 ID',
  number VARCHAR(50) NOT NULL COMMENT '章节编号',
  title VARCHAR(300) NOT NULL COMMENT '章节标题',
  content_markdown LONGTEXT NULL COMMENT '正文 Markdown',
  table_json JSON NULL COMMENT '结构化表格 JSON',
  status VARCHAR(50) NOT NULL DEFAULT 'PENDING' COMMENT '章节状态',
  source VARCHAR(50) NOT NULL DEFAULT 'AI' COMMENT '内容来源：AI / USER_EDITED / REGENERATED / MANUAL',
  version INT NOT NULL DEFAULT 1 COMMENT '内容版本',
  error_message TEXT NULL COMMENT '失败原因',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_sections_report_id (report_id),
  KEY idx_sections_outline_node_id (outline_node_id),
  KEY idx_sections_report_number (report_id, number),
  KEY idx_sections_status (status),
  CONSTRAINT fk_sections_report
    FOREIGN KEY (report_id) REFERENCES reports(id)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_sections_outline_node
    FOREIGN KEY (outline_node_id) REFERENCES report_outline_nodes(id)
    ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报告章节内容表';

CREATE TABLE IF NOT EXISTS report_files (
  id CHAR(36) NOT NULL COMMENT '文件 ID，UUID',
  report_id CHAR(36) NOT NULL COMMENT '报告 ID',
  file_name VARCHAR(300) NOT NULL COMMENT '文件名',
  file_path VARCHAR(1000) NOT NULL COMMENT '存储路径',
  file_size BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小，字节',
  sha256 CHAR(64) NULL COMMENT '文件 SHA256',
  created_by VARCHAR(100) NOT NULL DEFAULT 'local_user' COMMENT '生成人',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '生成时间',
  PRIMARY KEY (id),
  KEY idx_files_report_id (report_id),
  KEY idx_files_created_at (created_at),
  CONSTRAINT fk_files_report
    FOREIGN KEY (report_id) REFERENCES reports(id)
    ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报告导出文件表';

CREATE TABLE IF NOT EXISTS report_templates (
  id CHAR(36) NOT NULL COMMENT '模板 ID，UUID',
  name VARCHAR(200) NOT NULL COMMENT '模板名称',
  report_type VARCHAR(50) NOT NULL COMMENT '报告类型',
  version VARCHAR(50) NOT NULL DEFAULT '1.0.0' COMMENT '模板版本',
  file_path VARCHAR(1000) NULL COMMENT 'DOCX 模板路径',
  config_json JSON NULL COMMENT '样式和结构配置',
  enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  created_by VARCHAR(100) NOT NULL DEFAULT 'local_user' COMMENT '上传人',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_templates_report_type (report_type),
  KEY idx_templates_enabled (enabled),
  KEY idx_templates_type_enabled (report_type, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报告模板表';

CREATE TABLE IF NOT EXISTS project_assets (
  id CHAR(36) NOT NULL COMMENT '素材 ID，UUID',
  name VARCHAR(300) NOT NULL COMMENT '素材名称',
  category VARCHAR(50) NOT NULL DEFAULT 'OTHER' COMMENT '素材分类：STANDARD_DOC / REPORT_DATA / OTHER',
  file_type VARCHAR(20) NOT NULL COMMENT '文件类型扩展名，例如 pdf / xlsx',
  file_path VARCHAR(1000) NOT NULL COMMENT '存储路径',
  file_size BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小，字节',
  sha256 CHAR(64) NULL COMMENT '文件 SHA256',
  description VARCHAR(1000) NULL COMMENT '描述',
  tags VARCHAR(500) NULL COMMENT '标签，逗号分隔',
  enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  created_by VARCHAR(100) NOT NULL DEFAULT 'local_user' COMMENT '上传人',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_assets_category (category),
  KEY idx_assets_enabled (enabled),
  KEY idx_assets_file_type (file_type),
  KEY idx_assets_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='专业素材文档表';
