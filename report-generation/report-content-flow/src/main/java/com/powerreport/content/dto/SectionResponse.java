package com.powerreport.content.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class SectionResponse {

    private String sectionId;
    private String outlineNodeId;
    private String reportId;
    private String number;
    private String title;
    private String contentMarkdown;
    private String status;
    private String source;
    private Integer version;
    private String errorMessage;
    private LocalDateTime updatedAt;
}

