package com.powerreport.content.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SectionGenerateResponse {

    private String taskId;
    private String reportId;
    private String sectionId;
    private String status;
    private Integer totalSections;
    private Integer completedSections;
    private String message;
}

