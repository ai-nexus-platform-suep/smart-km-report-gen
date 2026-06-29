package com.km.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库视图对象（返回前端）。
 * 不暴露 chunk_strategy_json 等内部字段。
 */
@Data
public class KnowledgeBaseVO {

    private String id;
    private String name;
    private String description;
    private String docType;
    private String searchStrategy;
    private Integer docCount;
    private Long ownerId;
    private String ownerName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
