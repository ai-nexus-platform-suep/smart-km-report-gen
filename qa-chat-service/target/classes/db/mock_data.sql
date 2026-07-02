-- ============================================================
-- 智能问答 - 模拟测试数据
-- 说明:
--   1. 请先执行 conversation.sql 建表
--   2. 再执行本脚本；可重复执行（会先清空 qa_message / qa_session）
--   3. 时间使用 DATE_SUB(NOW(), ...) 相对当前日期，趋势图始终有效
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM qa_message;
DELETE FROM qa_session;

SET FOREIGN_KEY_CHECKS = 1;

-- ------------------------------------------------------------
-- 会话数据（7 条：5 正常 + 1 空会话 + 1 已删除）
-- ------------------------------------------------------------
INSERT INTO qa_session
    (id, user_id, title, status, message_count, last_message_at, created_at, updated_at, deleted_at)
VALUES
    (1001, 1, '什么是技术监督？', 1, 4,
        DATE_SUB(NOW(), INTERVAL 1 DAY),
        DATE_SUB(NOW(), INTERVAL 3 DAY),
        DATE_SUB(NOW(), INTERVAL 1 DAY), NULL),

    (1002, 1, '变压器故障怎么排查？', 1, 6,
        DATE_SUB(NOW(), INTERVAL 2 HOUR),
        DATE_SUB(NOW(), INTERVAL 5 DAY),
        DATE_SUB(NOW(), INTERVAL 2 HOUR), NULL),

    (1003, 1, '你好，今天天气怎么样？', 1, 2,
        DATE_SUB(NOW(), INTERVAL 3 DAY),
        DATE_SUB(NOW(), INTERVAL 4 DAY),
        DATE_SUB(NOW(), INTERVAL 3 DAY), NULL),

    (1004, 1, '新对话', 1, 0,
        DATE_SUB(NOW(), INTERVAL 1 HOUR),
        DATE_SUB(NOW(), INTERVAL 1 HOUR),
        DATE_SUB(NOW(), INTERVAL 1 HOUR), NULL),

    (1005, 1, '已删除的测试会话', 0, 2,
        DATE_SUB(NOW(), INTERVAL 10 DAY),
        DATE_SUB(NOW(), INTERVAL 12 DAY),
        DATE_SUB(NOW(), INTERVAL 10 DAY),
        DATE_SUB(NOW(), INTERVAL 10 DAY)),

    (1006, 1, 'RAG 引用溯源示例', 1, 2,
        DATE_SUB(NOW(), INTERVAL 6 HOUR),
        DATE_SUB(NOW(), INTERVAL 1 DAY),
        DATE_SUB(NOW(), INTERVAL 6 HOUR), NULL),

    (1007, 1, '近30天统计种子', 1, 50,
        DATE_SUB(NOW(), INTERVAL 1 DAY),
        DATE_SUB(NOW(), INTERVAL 29 DAY),
        DATE_SUB(NOW(), INTERVAL 1 DAY), NULL);

-- ------------------------------------------------------------
-- 会话 1001：技术监督（2 轮知识问答）
-- ------------------------------------------------------------
INSERT INTO qa_message
    (id, session_id, user_id, seq, role, content, intent_type, thinking_steps, citations,
     generate_status, token_usage, status, created_at, updated_at, deleted_at)
VALUES
    (2001, 1001, 1, 1, 'user', '什么是技术监督？', NULL, NULL, NULL,
        1, NULL, 1, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), NULL),

    (2002, 1001, 1, 2, 'assistant',
        '技术监督（Technical Supervision）是指对电力设备从设计、制造、安装到运行、检修全过程进行的技术监督与管理工作，目的是保障设备安全可靠运行。',
        'KNOWLEDGE_QA',
        '[{"step":1,"title":"检索知识库","content":"在术语库中匹配到「技术监督」相关条目 3 条"}]',
        '[{"docId":"doc_001","docName":"技术监督管理办法.pdf","snippet":"技术监督是指对电力生产设备...","score":0.94}]',
        1, 920, 1, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), NULL),

    (2003, 1001, 1, 3, 'user', '技术监督的主要内容包括哪些？', NULL, NULL, NULL,
        1, NULL, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL),

    (2004, 1001, 1, 4, 'assistant',
        '主要包括：绝缘监督、化学监督、金属监督、热工监督、继电保护监督、电能质量监督、环保监督等。',
        'KNOWLEDGE_QA',
        '[{"step":1,"title":"检索知识库","content":"匹配技术监督目录章节"}]',
        '[{"docId":"doc_002","docName":"技术监督实施细则.docx","snippet":"技术监督内容包括绝缘、化学、金属...","score":0.89}]',
        1, 780, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL);

-- ------------------------------------------------------------
-- 会话 1002：变压器故障（3 轮，含 1 条生成失败）
-- ------------------------------------------------------------
INSERT INTO qa_message
    (id, session_id, user_id, seq, role, content, intent_type, thinking_steps, citations,
     generate_status, token_usage, status, created_at, updated_at, deleted_at)
VALUES
    (2005, 1002, 1, 1, 'user', '变压器故障怎么排查？', NULL, NULL, NULL,
        1, NULL, 1, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), NULL),

    (2006, 1002, 1, 2, 'assistant',
        '变压器故障排查一般从以下几个方面入手：1. 外观检查（油位、渗漏、异响）；2. 电气试验（绝缘电阻、介损）；3. 油色谱分析；4. 温度监测。',
        'KNOWLEDGE_QA', NULL,
        '[{"docId":"doc_010","docName":"变压器运维手册.pdf","snippet":"故障排查应优先进行外观检查和油色谱...","score":0.91}]',
        1, 1050, 1, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), NULL),

    (2007, 1002, 1, 3, 'user', '油色谱乙炔含量超标说明什么？', NULL, NULL, NULL,
        1, NULL, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NULL),

    (2008, 1002, 1, 4, 'assistant',
        '乙炔（C2H2）含量超标通常表明变压器内部存在放电性故障，可能涉及绕组匝间短路、局部放电或电弧放电，需立即安排停电检修。',
        'KNOWLEDGE_QA', NULL, NULL,
        1, 890, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NULL),

    (2009, 1002, 1, 5, 'user', '请帮我生成一份完整的检修方案', NULL, NULL, NULL,
        1, NULL, 1, DATE_SUB(NOW(), INTERVAL 3 HOUR), DATE_SUB(NOW(), INTERVAL 3 HOUR), NULL),

    (2010, 1002, 1, 6, 'assistant', '',
        'KNOWLEDGE_QA', NULL, NULL,
        2, NULL, 1, DATE_SUB(NOW(), INTERVAL 2 HOUR), DATE_SUB(NOW(), INTERVAL 2 HOUR), NULL);

-- ------------------------------------------------------------
-- 会话 1003：一般闲聊（不计入知识问答统计）
-- ------------------------------------------------------------
INSERT INTO qa_message
    (id, session_id, user_id, seq, role, content, intent_type, thinking_steps, citations,
     generate_status, token_usage, status, created_at, updated_at, deleted_at)
VALUES
    (2011, 1003, 1, 1, 'user', '你好，今天天气怎么样？', NULL, NULL, NULL,
        1, NULL, 1, DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), NULL),

    (2012, 1003, 1, 2, 'assistant',
        '您好！我是电力行业智能问答助手，暂时无法查询实时天气，如有技术监督、设备运维相关问题欢迎随时提问。',
        'CHAT', NULL, NULL,
        1, 320, 1, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), NULL);

-- ------------------------------------------------------------
-- 会话 1005：已删除会话的消息（status=0，列表不可见）
-- ------------------------------------------------------------
INSERT INTO qa_message
    (id, session_id, user_id, seq, role, content, intent_type, thinking_steps, citations,
     generate_status, token_usage, status, created_at, updated_at, deleted_at)
VALUES
    (2013, 1005, 1, 1, 'user', '这条会话已被删除', NULL, NULL, NULL,
        1, NULL, 0, DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY),
        DATE_SUB(NOW(), INTERVAL 10 DAY)),

    (2014, 1005, 1, 2, 'assistant', '这是已删除会话中的回答。', 'KNOWLEDGE_QA', NULL, NULL,
        1, 500, 0, DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY),
        DATE_SUB(NOW(), INTERVAL 10 DAY));

-- ------------------------------------------------------------
-- 会话 1006：RAG 引用溯源（多引用）
-- ------------------------------------------------------------
INSERT INTO qa_message
    (id, session_id, user_id, seq, role, content, intent_type, thinking_steps, citations,
     generate_status, token_usage, status, created_at, updated_at, deleted_at)
VALUES
    (2015, 1006, 1, 1, 'user', '继电保护定值如何整定？', NULL, NULL, NULL,
        1, NULL, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL),

    (2016, 1006, 1, 2, 'assistant',
        '继电保护定值整定需依据保护类型、电网结构和短路计算结果，遵循选择性、速动性、灵敏性和可靠性四项基本原则。',
        'KNOWLEDGE_QA',
        '[{"step":1,"title":"意图识别","content":"识别为知识问答"},{"step":2,"title":"检索知识库","content":"召回 Top5 文档片段"},{"step":3,"title":"生成回答","content":"综合引用生成答案"}]',
        '[{"docId":"doc_020","docName":"继电保护规程.pdf","snippet":"保护定值整定应满足选择性...","score":0.93},{"docId":"doc_021","docName":"电网继电保护整定计算导则.pdf","snippet":"整定计算需考虑最大运行方式...","score":0.87}]',
        1, 1120, 1, DATE_SUB(NOW(), INTERVAL 6 HOUR), DATE_SUB(NOW(), INTERVAL 6 HOUR), NULL);

-- ------------------------------------------------------------
-- 会话 1007：近 30 天统计种子（仅 assistant / KNOWLEDGE_QA / 已完成）
-- 每日条数设计（从今天往前 29 天）：
--   29天前:1  28:0  27:2  26:1  25:3  24:0  23:2  22:1  21:4  20:0
--   19:2  18:1  17:3  16:0  15:2  14:1  13:3  12:0  11:2  10:1
--    9:4   8:0   7:2   6:1   5:3   4:0   3:2   2:1   1:3   0:5
-- 合计 50 条（与 session.message_count 一致）
-- ------------------------------------------------------------
INSERT INTO qa_message
    (id, session_id, user_id, seq, role, content, intent_type, thinking_steps, citations,
     generate_status, token_usage, status, created_at, updated_at, deleted_at)
VALUES
    (3001, 1007, 1,  1, 'assistant', '【种子】29天前知识问答 #1', 'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 29 DAY) + INTERVAL 10 HOUR, DATE_SUB(CURDATE(), INTERVAL 29 DAY) + INTERVAL 10 HOUR, NULL),
    (3002, 1007, 1,  2, 'assistant', '【种子】27天前知识问答 #1', 'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 27 DAY) + INTERVAL 11 HOUR, DATE_SUB(CURDATE(), INTERVAL 27 DAY) + INTERVAL 11 HOUR, NULL),
    (3003, 1007, 1,  3, 'assistant', '【种子】27天前知识问答 #2', 'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 27 DAY) + INTERVAL 14 HOUR, DATE_SUB(CURDATE(), INTERVAL 27 DAY) + INTERVAL 14 HOUR, NULL),
    (3004, 1007, 1,  4, 'assistant', '【种子】26天前知识问答 #1', 'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 26 DAY) + INTERVAL 9 HOUR,  DATE_SUB(CURDATE(), INTERVAL 26 DAY) + INTERVAL 9 HOUR,  NULL),
    (3005, 1007, 1,  5, 'assistant', '【种子】25天前知识问答 #1', 'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 25 DAY) + INTERVAL 10 HOUR, DATE_SUB(CURDATE(), INTERVAL 25 DAY) + INTERVAL 10 HOUR, NULL),
    (3006, 1007, 1,  6, 'assistant', '【种子】25天前知识问答 #2', 'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 25 DAY) + INTERVAL 12 HOUR, DATE_SUB(CURDATE(), INTERVAL 25 DAY) + INTERVAL 12 HOUR, NULL),
    (3007, 1007, 1,  7, 'assistant', '【种子】25天前知识问答 #3', 'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 25 DAY) + INTERVAL 15 HOUR, DATE_SUB(CURDATE(), INTERVAL 25 DAY) + INTERVAL 15 HOUR, NULL),
    (3008, 1007, 1,  8, 'assistant', '【种子】23天前知识问答 #1', 'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 23 DAY) + INTERVAL 10 HOUR, DATE_SUB(CURDATE(), INTERVAL 23 DAY) + INTERVAL 10 HOUR, NULL),
    (3009, 1007, 1,  9, 'assistant', '【种子】23天前知识问答 #2', 'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 23 DAY) + INTERVAL 14 HOUR, DATE_SUB(CURDATE(), INTERVAL 23 DAY) + INTERVAL 14 HOUR, NULL),
    (3010, 1007, 1, 10, 'assistant', '【种子】22天前知识问答 #1', 'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 22 DAY) + INTERVAL 11 HOUR, DATE_SUB(CURDATE(), INTERVAL 22 DAY) + INTERVAL 11 HOUR, NULL),
    (3011, 1007, 1, 11, 'assistant', '【种子】21天前知识问答 #1', 'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 21 DAY) + INTERVAL 9 HOUR,  DATE_SUB(CURDATE(), INTERVAL 21 DAY) + INTERVAL 9 HOUR,  NULL),
    (3012, 1007, 1, 12, 'assistant', '【种子】21天前知识问答 #2', 'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 21 DAY) + INTERVAL 11 HOUR, DATE_SUB(CURDATE(), INTERVAL 21 DAY) + INTERVAL 11 HOUR, NULL),
    (3013, 1007, 1, 13, 'assistant', '【种子】21天前知识问答 #3', 'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 21 DAY) + INTERVAL 13 HOUR, DATE_SUB(CURDATE(), INTERVAL 21 DAY) + INTERVAL 13 HOUR, NULL),
    (3014, 1007, 1, 14, 'assistant', '【种子】21天前知识问答 #4', 'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 21 DAY) + INTERVAL 16 HOUR, DATE_SUB(CURDATE(), INTERVAL 21 DAY) + INTERVAL 16 HOUR, NULL),
    (3015, 1007, 1, 15, 'assistant', '【种子】19天前知识问答 #1', 'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 19 DAY) + INTERVAL 10 HOUR, DATE_SUB(CURDATE(), INTERVAL 19 DAY) + INTERVAL 10 HOUR, NULL),
    (3016, 1007, 1, 16, 'assistant', '【种子】19天前知识问答 #2', 'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 19 DAY) + INTERVAL 14 HOUR, DATE_SUB(CURDATE(), INTERVAL 19 DAY) + INTERVAL 14 HOUR, NULL),
    (3017, 1007, 1, 17, 'assistant', '【种子】18天前知识问答 #1', 'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 18 DAY) + INTERVAL 11 HOUR, DATE_SUB(CURDATE(), INTERVAL 18 DAY) + INTERVAL 11 HOUR, NULL),
    (3018, 1007, 1, 18, 'assistant', '【种子】17天前知识问答 #1', 'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 17 DAY) + INTERVAL 10 HOUR, DATE_SUB(CURDATE(), INTERVAL 17 DAY) + INTERVAL 10 HOUR, NULL),
    (3019, 1007, 1, 19, 'assistant', '【种子】17天前知识问答 #2', 'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 17 DAY) + INTERVAL 13 HOUR, DATE_SUB(CURDATE(), INTERVAL 17 DAY) + INTERVAL 13 HOUR, NULL),
    (3020, 1007, 1, 20, 'assistant', '【种子】17天前知识问答 #3', 'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 17 DAY) + INTERVAL 16 HOUR, DATE_SUB(CURDATE(), INTERVAL 17 DAY) + INTERVAL 16 HOUR, NULL),
    (3021, 1007, 1, 21, 'assistant', '【种子】15天前知识问答 #1', 'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 15 DAY) + INTERVAL 9 HOUR,  DATE_SUB(CURDATE(), INTERVAL 15 DAY) + INTERVAL 9 HOUR,  NULL),
    (3022, 1007, 1, 22, 'assistant', '【种子】15天前知识问答 #2', 'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 15 DAY) + INTERVAL 14 HOUR, DATE_SUB(CURDATE(), INTERVAL 15 DAY) + INTERVAL 14 HOUR, NULL),
    (3023, 1007, 1, 23, 'assistant', '【种子】14天前知识问答 #1', 'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 14 DAY) + INTERVAL 10 HOUR, DATE_SUB(CURDATE(), INTERVAL 14 DAY) + INTERVAL 10 HOUR, NULL),
    (3024, 1007, 1, 24, 'assistant', '【种子】13天前知识问答 #1', 'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 13 DAY) + INTERVAL 11 HOUR, DATE_SUB(CURDATE(), INTERVAL 13 DAY) + INTERVAL 11 HOUR, NULL),
    (3025, 1007, 1, 25, 'assistant', '【种子】13天前知识问答 #2', 'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 13 DAY) + INTERVAL 14 HOUR, DATE_SUB(CURDATE(), INTERVAL 13 DAY) + INTERVAL 14 HOUR, NULL),
    (3026, 1007, 1, 26, 'assistant', '【种子】13天前知识问答 #3', 'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 13 DAY) + INTERVAL 16 HOUR, DATE_SUB(CURDATE(), INTERVAL 13 DAY) + INTERVAL 16 HOUR, NULL),
    (3027, 1007, 1, 27, 'assistant', '【种子】11天前知识问答 #1', 'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 11 DAY) + INTERVAL 10 HOUR, DATE_SUB(CURDATE(), INTERVAL 11 DAY) + INTERVAL 10 HOUR, NULL),
    (3028, 1007, 1, 28, 'assistant', '【种子】11天前知识问答 #2', 'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 11 DAY) + INTERVAL 15 HOUR, DATE_SUB(CURDATE(), INTERVAL 11 DAY) + INTERVAL 15 HOUR, NULL),
    (3029, 1007, 1, 29, 'assistant', '【种子】10天前知识问答 #1', 'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 10 DAY) + INTERVAL 11 HOUR, DATE_SUB(CURDATE(), INTERVAL 10 DAY) + INTERVAL 11 HOUR, NULL),
    (3030, 1007, 1, 30, 'assistant', '【种子】9天前知识问答 #1',  'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 9 DAY)  + INTERVAL 10 HOUR, DATE_SUB(CURDATE(), INTERVAL 9 DAY)  + INTERVAL 10 HOUR, NULL),
    (3031, 1007, 1, 31, 'assistant', '【种子】9天前知识问答 #2',  'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 9 DAY)  + INTERVAL 13 HOUR, DATE_SUB(CURDATE(), INTERVAL 9 DAY)  + INTERVAL 13 HOUR, NULL),
    (3032, 1007, 1, 32, 'assistant', '【种子】9天前知识问答 #3',  'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 9 DAY)  + INTERVAL 16 HOUR, DATE_SUB(CURDATE(), INTERVAL 9 DAY)  + INTERVAL 16 HOUR, NULL),
    (3033, 1007, 1, 33, 'assistant', '【种子】9天前知识问答 #4',  'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 9 DAY)  + INTERVAL 18 HOUR, DATE_SUB(CURDATE(), INTERVAL 9 DAY)  + INTERVAL 18 HOUR, NULL),
    (3034, 1007, 1, 34, 'assistant', '【种子】7天前知识问答 #1',  'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 7 DAY)  + INTERVAL 10 HOUR, DATE_SUB(CURDATE(), INTERVAL 7 DAY)  + INTERVAL 10 HOUR, NULL),
    (3035, 1007, 1, 35, 'assistant', '【种子】7天前知识问答 #2',  'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 7 DAY)  + INTERVAL 14 HOUR, DATE_SUB(CURDATE(), INTERVAL 7 DAY)  + INTERVAL 14 HOUR, NULL),
    (3036, 1007, 1, 36, 'assistant', '【种子】6天前知识问答 #1',  'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 6 DAY)  + INTERVAL 11 HOUR, DATE_SUB(CURDATE(), INTERVAL 6 DAY)  + INTERVAL 11 HOUR, NULL),
    (3037, 1007, 1, 37, 'assistant', '【种子】5天前知识问答 #1',  'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 5 DAY)  + INTERVAL 9 HOUR,  DATE_SUB(CURDATE(), INTERVAL 5 DAY)  + INTERVAL 9 HOUR,  NULL),
    (3038, 1007, 1, 38, 'assistant', '【种子】5天前知识问答 #2',  'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 5 DAY)  + INTERVAL 12 HOUR, DATE_SUB(CURDATE(), INTERVAL 5 DAY)  + INTERVAL 12 HOUR, NULL),
    (3039, 1007, 1, 39, 'assistant', '【种子】5天前知识问答 #3',  'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 5 DAY)  + INTERVAL 15 HOUR, DATE_SUB(CURDATE(), INTERVAL 5 DAY)  + INTERVAL 15 HOUR, NULL),
    (3040, 1007, 1, 40, 'assistant', '【种子】3天前知识问答 #1',  'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 3 DAY)  + INTERVAL 10 HOUR, DATE_SUB(CURDATE(), INTERVAL 3 DAY)  + INTERVAL 10 HOUR, NULL),
    (3041, 1007, 1, 41, 'assistant', '【种子】3天前知识问答 #2',  'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 3 DAY)  + INTERVAL 14 HOUR, DATE_SUB(CURDATE(), INTERVAL 3 DAY)  + INTERVAL 14 HOUR, NULL),
    (3042, 1007, 1, 42, 'assistant', '【种子】2天前知识问答 #1',  'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 2 DAY)  + INTERVAL 11 HOUR, DATE_SUB(CURDATE(), INTERVAL 2 DAY)  + INTERVAL 11 HOUR, NULL),
    (3043, 1007, 1, 43, 'assistant', '【种子】1天前知识问答 #1',  'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 1 DAY)  + INTERVAL 10 HOUR, DATE_SUB(CURDATE(), INTERVAL 1 DAY)  + INTERVAL 10 HOUR, NULL),
    (3044, 1007, 1, 44, 'assistant', '【种子】1天前知识问答 #2',  'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 1 DAY)  + INTERVAL 13 HOUR, DATE_SUB(CURDATE(), INTERVAL 1 DAY)  + INTERVAL 13 HOUR, NULL),
    (3045, 1007, 1, 45, 'assistant', '【种子】1天前知识问答 #3',  'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, DATE_SUB(CURDATE(), INTERVAL 1 DAY)  + INTERVAL 16 HOUR, DATE_SUB(CURDATE(), INTERVAL 1 DAY)  + INTERVAL 16 HOUR, NULL),
    (3046, 1007, 1, 46, 'assistant', '【种子】今天知识问答 #1',    'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, CURDATE() + INTERVAL 9 HOUR,  CURDATE() + INTERVAL 9 HOUR,  NULL),
    (3047, 1007, 1, 47, 'assistant', '【种子】今天知识问答 #2',    'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, CURDATE() + INTERVAL 11 HOUR, CURDATE() + INTERVAL 11 HOUR, NULL),
    (3048, 1007, 1, 48, 'assistant', '【种子】今天知识问答 #3',    'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, CURDATE() + INTERVAL 14 HOUR, CURDATE() + INTERVAL 14 HOUR, NULL),
    (3049, 1007, 1, 49, 'assistant', '【种子】今天知识问答 #4',    'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, CURDATE() + INTERVAL 16 HOUR, CURDATE() + INTERVAL 16 HOUR, NULL),
    (3050, 1007, 1, 50, 'assistant', '【种子】今天知识问答 #5',    'KNOWLEDGE_QA', NULL, NULL, 1, 400, 1, CURDATE() + INTERVAL 18 HOUR, CURDATE() + INTERVAL 18 HOUR, NULL);

-- ------------------------------------------------------------
-- 导入完成，快速验证：
--   SELECT COUNT(*) FROM qa_session WHERE status = 1;                    -- 期望 6
--   SELECT COUNT(*) FROM qa_message WHERE status = 1;                    -- 期望 64
--   SELECT COUNT(*) FROM qa_message
--     WHERE status=1 AND role='assistant' AND intent_type='KNOWLEDGE_QA'
--       AND generate_status=1;                                             -- 期望 55（统计总数）
-- ------------------------------------------------------------
