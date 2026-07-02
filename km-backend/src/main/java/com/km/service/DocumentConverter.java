package com.km.service;

import com.km.entity.Chunk;
import com.km.entity.Document;
import com.km.vo.ChunkVO;
import com.km.vo.DocumentVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.Map;

/**
 * 文档相关对象转换工具（替代 MapStruct，MVP 阶段手动转换）
 */
public final class DocumentConverter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DocumentConverter() {
    }

    /**
     * Document 实体 → DocumentVO
     */
    public static DocumentVO toVO(Document entity) {
        if (entity == null) {
            return null;
        }
        DocumentVO vo = new DocumentVO();
        vo.setId(entity.getId());
        vo.setKbId(entity.getKbId());
        vo.setFilename(entity.getFilename());
        vo.setFileSize(entity.getFileSize());
        vo.setMimeType(entity.getMimeType());
        vo.setStatus(entity.getStatus());
        vo.setErrorMsg(entity.getErrorMsg());
        vo.setCreatedBy(entity.getCreatedBy());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        // 解析 tags_json 到 Map
        if (entity.getTagsJson() != null && !entity.getTagsJson().isEmpty()) {
            try {
                Map<String, String> tags = MAPPER.readValue(
                        entity.getTagsJson(), new TypeReference<Map<String, String>>() {});
                vo.setTags(tags);
            } catch (Exception e) {
                vo.setTags(Collections.emptyMap());
            }
        } else {
            vo.setTags(Collections.emptyMap());
        }
        return vo;
    }

    /**
     * Chunk 实体 → ChunkVO
     */
    public static ChunkVO toChunkVO(Chunk entity) {
        if (entity == null) {
            return null;
        }
        ChunkVO vo = new ChunkVO();
        vo.setId(entity.getId());
        vo.setDocId(entity.getDocId());
        vo.setContent(entity.getContent());
        vo.setChapterPath(entity.getChapterPath());
        vo.setChunkIndex(entity.getChunkIndex());
        vo.setChunkType(entity.getChunkType());
        vo.setCharCount(entity.getContent() != null ? entity.getContent().length() : 0);
        return vo;
    }
}
