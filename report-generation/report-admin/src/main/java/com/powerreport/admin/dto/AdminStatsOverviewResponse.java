package com.powerreport.admin.dto;

import lombok.Data;

@Data
public class AdminStatsOverviewResponse {

    private Long templateCount;
    private Long reportCount;
    private Long userCount;
    private Long sectionCount;
}
