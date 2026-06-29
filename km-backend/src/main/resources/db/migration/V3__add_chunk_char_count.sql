-- V3: Add char_count to chunk table (moved from V2 to avoid conflict with feat-a)
ALTER TABLE chunk ADD COLUMN char_count INT DEFAULT NULL COMMENT "Chunk character count" AFTER chunk_type;
