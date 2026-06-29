package com.km.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 文档视图对象，返回给前端
 */
@Data
public class DocumentVO {

    private String id;
    private String kbId;
    private String filename;
    private Long fileSize;
    private String mimeType;
    private String status;
    private String errorMsg;
    private Map<String, String> tags;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
