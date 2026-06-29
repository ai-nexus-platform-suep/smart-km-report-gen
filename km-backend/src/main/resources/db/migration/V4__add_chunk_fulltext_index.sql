-- V4: Add FULLTEXT index on chunk.content for LIKE fallback search
-- Required by feat-a's SearchServiceImpl.searchByKeyword() which uses MATCH/AGAINST
ALTER TABLE chunk ADD FULLTEXT INDEX t_chunk_content (content);
