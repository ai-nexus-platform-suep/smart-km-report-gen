package com.powerreport.content.controller;

import com.myenglish.qacommon.dto.ApiResponse;
import com.powerreport.content.dto.ReportHistoryDetailResponse;
import com.powerreport.content.dto.ReportHistoryPageResponse;
import com.powerreport.content.service.HistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reports/history")
public class HistoryController {

    private final HistoryService historyService;

    @GetMapping
    public ApiResponse<ReportHistoryPageResponse> listReports(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size
    ) {
        return ApiResponse.success(historyService.listReports(page, size));
    }

    @GetMapping("/{reportId}")
    public ApiResponse<ReportHistoryDetailResponse> getReportDetail(@PathVariable String reportId) {
        return ApiResponse.success(historyService.getReportDetail(reportId));
    }

    @DeleteMapping("/{reportId}")
    public ApiResponse<Void> deleteReport(@PathVariable String reportId) {
        historyService.deleteReport(reportId);
        return ApiResponse.success();
    }
}

