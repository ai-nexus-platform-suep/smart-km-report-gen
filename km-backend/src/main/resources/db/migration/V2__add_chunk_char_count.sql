-- V2: Add char_count to chunk table for chunk detail display
-- This is used by EPIC-04 pipeline to store character count per chunk

ALTER TABLE `chunk` ADD COLUMN `char_count` INT DEFAULT NULL COMMENT "Chunk character count" AFTER `chunk_type`;
