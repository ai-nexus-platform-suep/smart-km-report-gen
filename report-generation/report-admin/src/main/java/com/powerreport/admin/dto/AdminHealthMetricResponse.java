package com.powerreport.admin.dto;

import lombok.Data;

@Data
public class AdminHealthMetricResponse {

    private String metric;
    private String label;
    private String status;
    private Long value;
    private String unit;
}
