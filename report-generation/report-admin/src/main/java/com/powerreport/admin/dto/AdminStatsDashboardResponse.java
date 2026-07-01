package com.powerreport.admin.dto;

import java.util.List;
import lombok.Data;

@Data
public class AdminStatsDashboardResponse {

    private AdminStatsOverviewResponse overview;
    private List<AdminStatsTrendResponse> trends;
    private AdminStatsDistributionResponse distributions;
    private List<AdminRecentTaskResponse> recentTasks;
    private List<AdminHealthMetricResponse> health;
    private List<AdminAlertResponse> alerts;
}
