package com.powerreport.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("reports")
public class ReportEntity {

    @TableId
    private String id;

    private String name;

    @TableField("type")
    private String reportType;

    private String subject;
    private String specialty;
    private String powerPlant;
    private Integer reportYear;
    private String status;
    private String ownerName;
    private Integer totalSections;
    private Integer completedSections;
    private LocalDateTime generatedAt;
    private Boolean deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
