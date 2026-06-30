package com.powerreport.content.dto;

import lombok.Data;

@Data
public class ReportHistoryQueryRequest {

    private Integer page = 1;

    private Integer size = 10;

    /**
     * Report lifecycle status, such as OUTLINE_READY or EXPORTED.
     */
    private String status;

    /**
     * Preferred query parameter for report type.
     */
    private String reportType;

    /**
     * Compatibility alias for reportType.
     */
    private String type;

    private String powerPlant;

    private String specialty;

    private Integer reportYear;

    private String subject;
}
