package com.powerreport.admin.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AssetResponse {

    private String id;
    private String name;
    private String category;
    private String categoryLabel;
    private String fileType;
    private String storageType;
    private String filePath;
    private String bucketName;
    private String objectName;
    private String originalFileName;
    private String contentType;
    private Long fileSize;
    private String sha256;
    private String description;
    private String tags;
    private Boolean enabled;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
