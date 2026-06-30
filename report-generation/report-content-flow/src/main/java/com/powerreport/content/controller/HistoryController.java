package com.powerreport.content.controller;

import com.myenglish.qacommon.dto.ApiResponse;
import com.powerreport.content.dto.OptionResponse;
import com.powerreport.content.dto.ReportHistoryDetailResponse;
import com.powerreport.content.dto.ReportHistoryPageResponse;
import com.powerreport.content.dto.ReportHistoryQueryRequest;
import com.powerreport.content.service.HistoryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reports/history")
public class HistoryController {

    private final HistoryService historyService;

    @GetMapping
    public ApiResponse<ReportHistoryPageResponse> listReports(@ModelAttribute ReportHistoryQueryRequest query) {
        return ApiResponse.success(historyService.listReports(query));
    }

    @GetMapping("/search")
    public ApiResponse<ReportHistoryPageResponse> searchReports(@ModelAttribute ReportHistoryQueryRequest query) {
        return ApiResponse.success(historyService.listReports(query));
    }

    @GetMapping("/status-options")
    public ApiResponse<List<OptionResponse>> listStatusOptions() {
        return ApiResponse.success(historyService.listStatusOptions());
    }

    @GetMapping("/report-type-options")
    public ApiResponse<List<OptionResponse>> listReportTypeOptions() {
        return ApiResponse.success(historyService.listReportTypeOptions());
    }

    @GetMapping("/{reportId:[0-9a-fA-F\\-]{36}}")
    public ApiResponse<ReportHistoryDetailResponse> getReportDetail(@PathVariable String reportId) {
        return ApiResponse.success(historyService.getReportDetail(reportId));
    }

    @DeleteMapping("/{reportId:[0-9a-fA-F\\-]{36}}")
    public ApiResponse<Void> deleteReport(@PathVariable String reportId) {
        historyService.deleteReport(reportId);
        return ApiResponse.success();
    }
}

