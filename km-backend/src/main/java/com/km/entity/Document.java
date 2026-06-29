package com.km.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档实体，对应表 document
 */
@Data
public class Document {

    private String id;
    private String kbId;
    private String filename;
    private String filePath;
    private Long fileSize;
    private String mimeType;
    private String status;
    private String errorMsg;
    private String tagsJson;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
