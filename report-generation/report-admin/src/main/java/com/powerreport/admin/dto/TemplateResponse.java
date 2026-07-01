package com.powerreport.admin.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class TemplateResponse {

    private String id;
    private String name;
    private String reportType;
    private String version;
    private String storageType;
    private String filePath;
    private String bucketName;
    private String objectName;
    private String originalFileName;
    private String contentType;
    private Long fileSize;
    private String configJson;
    private Boolean enabled;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
