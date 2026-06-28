package com.powerreport.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("report_sections")
public class ReportSectionEntity {

    @TableId
    private String id;

    private String reportId;
    private String outlineNodeId;
    private String number;
    private String title;
    private String contentMarkdown;
    private String tableJson;
    private String status;
    private String source;
    private Integer version;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
