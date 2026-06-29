-- 系统配置默认值（EPIC-06）

INSERT INTO system_config (config_key, config_value) VALUES
('embedding', JSON_OBJECT(
    'modelName', 'BAAI/bge-m3',
    'apiUrl', 'https://api.siliconflow.cn/v1',
    'apiKey', '',
    'dimension', 1024
)),
('rerank', JSON_OBJECT(
    'modelName', 'BAAI/bge-reranker-v2-m3',
    'apiUrl', 'https://api.siliconflow.cn/v1',
    'apiKey', '',
    'topN', 20
)),
('parser', JSON_OBJECT(
    'backend', 'tika',
    'maxConcurrency', 3
))
ON DUPLICATE KEY UPDATE config_key = config_key;
