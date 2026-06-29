package com.powerreport.content.service;

import com.powerreport.content.dto.ReportHistoryDetailResponse;
import com.powerreport.content.dto.ReportHistoryPageResponse;

public interface HistoryService {

    ReportHistoryPageResponse listReports(Integer page, Integer size);

    ReportHistoryDetailResponse getReportDetail(String reportId);

    void deleteReport(String reportId);
}

