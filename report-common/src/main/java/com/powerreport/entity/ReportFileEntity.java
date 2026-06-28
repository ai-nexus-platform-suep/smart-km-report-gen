package com.powerreport.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("report_files")
public class ReportFileEntity {

    @TableId
    private String id;

    private String reportId;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private String sha256;
    private String createdBy;
    private LocalDateTime createdAt;
}
