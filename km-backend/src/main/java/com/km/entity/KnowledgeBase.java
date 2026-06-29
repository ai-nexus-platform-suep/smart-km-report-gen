package com.km.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库实体，对应表 knowledge_base
 */
@Data
public class KnowledgeBase {

    private String id;
    private String name;
    private String description;
    private String docType;
    private String chunkStrategyJson;
    private String searchStrategy;
    private Integer docCount;
    private Long ownerId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
