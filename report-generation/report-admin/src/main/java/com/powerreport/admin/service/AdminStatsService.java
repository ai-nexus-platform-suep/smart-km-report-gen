package com.powerreport.admin.service;

import com.powerreport.admin.dto.AdminAlertResponse;
import com.powerreport.admin.dto.AdminHealthMetricResponse;
import com.powerreport.admin.dto.AdminRecentTaskResponse;
import com.powerreport.admin.dto.AdminStatsDashboardResponse;
import com.powerreport.admin.dto.AdminStatsDistributionResponse;
import com.powerreport.admin.dto.AdminStatsOverviewResponse;
import com.powerreport.admin.dto.AdminStatsTrendResponse;
import java.util.List;

public interface AdminStatsService {

    AdminStatsOverviewResponse overview();

    List<AdminStatsTrendResponse> trend(Integer days);

    AdminStatsDistributionResponse distribution();

    List<AdminRecentTaskResponse> recentTasks(Integer limit);

    List<AdminHealthMetricResponse> health();

    List<AdminAlertResponse> alerts();

    AdminStatsDashboardResponse dashboard(Integer days, Integer recentLimit);
}
