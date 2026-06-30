package com.powerreport.content.service;

import com.powerreport.content.dto.OptionResponse;
import com.powerreport.content.dto.ReportHistoryDetailResponse;
import com.powerreport.content.dto.ReportHistoryPageResponse;
import com.powerreport.content.dto.ReportHistoryQueryRequest;
import java.util.List;

public interface HistoryService {

    ReportHistoryPageResponse listReports(ReportHistoryQueryRequest query);

    List<OptionResponse> listStatusOptions();

    List<OptionResponse> listReportTypeOptions();

    ReportHistoryDetailResponse getReportDetail(String reportId);

    void deleteReport(String reportId);
}

