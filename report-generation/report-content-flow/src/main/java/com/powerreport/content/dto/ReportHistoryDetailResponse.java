package com.powerreport.content.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class ReportHistoryDetailResponse {

    private String reportId;
    private String name;
    private String type;
    private String subject;
    private String specialty;
    private String powerPlant;
    private Integer reportYear;
    private String status;
    private Integer totalSections;
    private Integer completedSections;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<OutlineNodeResponse> outline;
    private List<SectionResponse> sections;
}

