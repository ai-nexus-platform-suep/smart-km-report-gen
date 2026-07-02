package com.powerreport.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("project_assets")
public class ProjectAssetEntity {

    @TableId
    private String id;

    private String name;
    private String category;
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
