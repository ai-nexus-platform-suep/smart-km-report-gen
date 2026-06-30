package com.powerreport.admin.controller;

import com.myenglish.qacommon.dto.ApiResponse;
import com.powerreport.admin.dto.AdminAlertResponse;
import com.powerreport.admin.dto.AdminHealthMetricResponse;
import com.powerreport.admin.dto.AdminRecentTaskResponse;
import com.powerreport.admin.dto.AdminStatsDashboardResponse;
import com.powerreport.admin.dto.AdminStatsDistributionResponse;
import com.powerreport.admin.dto.AdminStatsOverviewResponse;
import com.powerreport.admin.dto.AdminStatsTrendResponse;
import com.powerreport.admin.service.AdminStatsService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/stats")
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    @GetMapping("/overview")
    public ApiResponse<AdminStatsOverviewResponse> overview() {
        return ApiResponse.success(adminStatsService.overview());
    }

    @GetMapping("/trend")
    public ApiResponse<List<AdminStatsTrendResponse>> trend(
            @RequestParam(defaultValue = "30") Integer days
    ) {
        return ApiResponse.success(adminStatsService.trend(days));
    }

    @GetMapping("/distribution")
    public ApiResponse<AdminStatsDistributionResponse> distribution() {
        return ApiResponse.success(adminStatsService.distribution());
    }

    @GetMapping("/recent-tasks")
    public ApiResponse<List<AdminRecentTaskResponse>> recentTasks(
            @RequestParam(defaultValue = "10") Integer limit
    ) {
        return ApiResponse.success(adminStatsService.recentTasks(limit));
    }

    @GetMapping("/health")
    public ApiResponse<List<AdminHealthMetricResponse>> health() {
        return ApiResponse.success(adminStatsService.health());
    }

    @GetMapping("/alerts")
    public ApiResponse<List<AdminAlertResponse>> alerts() {
        return ApiResponse.success(adminStatsService.alerts());
    }

    @GetMapping("/dashboard")
    public ApiResponse<AdminStatsDashboardResponse> dashboard(
            @RequestParam(defaultValue = "30") Integer days,
            @RequestParam(defaultValue = "10") Integer recentLimit
    ) {
        return ApiResponse.success(adminStatsService.dashboard(days, recentLimit));
    }
}
