package com.km.entity;

import lombok.Data;
import java.time.LocalDateTime;

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
    private String ownerName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
