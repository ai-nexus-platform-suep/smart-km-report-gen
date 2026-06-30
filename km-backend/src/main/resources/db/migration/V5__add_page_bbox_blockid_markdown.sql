-- V5: chunk/document补充字段（page_no, bbox, block_id, markdown_path）
ALTER TABLE chunk
  ADD COLUMN page_no INT DEFAULT NULL COMMENT '页码' AFTER chunk_index,
  ADD COLUMN box VARCHAR(255) DEFAULT NULL COMMENT '坐标 bbox' AFTER page_no,
  ADD COLUMN lock_id VARCHAR(64) DEFAULT NULL COMMENT 'block_id 映射' AFTER ector_id;

ALTER TABLE document
  ADD COLUMN markdown_path VARCHAR(512) DEFAULT NULL COMMENT 'Markdown 文件 MinIO 路径' AFTER ile_path;
