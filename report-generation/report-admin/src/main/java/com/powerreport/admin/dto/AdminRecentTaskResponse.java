package com.powerreport.admin.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AdminRecentTaskResponse {

    private String reportId;
    private String name;
    private String type;
    private String subject;
    private String powerPlant;
    private Integer reportYear;
    private String status;
    private Integer totalSections;
    private Integer completedSections;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
