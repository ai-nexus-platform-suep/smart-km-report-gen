package com.powerreport.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("report_templates")
public class ReportTemplateEntity {

    @TableId
    private String id;

    private String name;
    private String reportType;
    private String version;
    private String filePath;
    private String configJson;
    private Boolean enabled;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
