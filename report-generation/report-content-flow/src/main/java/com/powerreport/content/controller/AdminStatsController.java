package com.powerreport.content.controller;

import com.myenglish.qacommon.dto.ApiResponse;
import com.powerreport.content.dto.AdminStatsOverviewResponse;
import com.powerreport.content.dto.AdminStatsTrendResponse;
import com.powerreport.content.service.AdminStatsService;
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
}
