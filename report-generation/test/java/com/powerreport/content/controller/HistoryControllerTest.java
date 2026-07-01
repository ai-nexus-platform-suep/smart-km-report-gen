package com.powerreport.content.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.powerreport.content.dto.OptionResponse;
import com.powerreport.content.dto.ReportHistoryDetailResponse;
import com.powerreport.content.dto.ReportHistoryItemResponse;
import com.powerreport.content.dto.ReportHistoryPageResponse;
import com.powerreport.content.dto.ReportHistoryQueryRequest;
import com.powerreport.content.service.HistoryService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class HistoryControllerTest {

    private static final String UUID = "11111111-1111-1111-1111-111111111111";

    @Mock
    private HistoryService historyService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new HistoryController(historyService))
                .build();
    }

    @Test
    void listReportsBindsQueryParameters() throws Exception {
        ReportHistoryItemResponse item = new ReportHistoryItemResponse();
        item.setReportId(UUID);
        item.setName("report");
        when(historyService.listReports(any(ReportHistoryQueryRequest.class)))
                .thenReturn(new ReportHistoryPageResponse(List.of(item), 1L, 2, 20));

        mockMvc.perform(get("/api/reports/history")
                        .param("page", "2")
                        .param("size", "20")
                        .param("status", "EXPORTED")
                        .param("reportType", "SUMMER_PEAK_CHECK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].reportId").value(UUID))
                .andExpect(jsonPath("$.data.page").value(2));

        ArgumentCaptor<ReportHistoryQueryRequest> captor = ArgumentCaptor.forClass(ReportHistoryQueryRequest.class);
        verify(historyService).listReports(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getReportType()).isEqualTo("SUMMER_PEAK_CHECK");
    }

    @Test
    void listStatusOptionsReturnsOptions() throws Exception {
        when(historyService.listStatusOptions())
                .thenReturn(List.of(new OptionResponse("EXPORTED", "exported")));

        mockMvc.perform(get("/api/reports/history/status-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("EXPORTED"));
    }

    @Test
    void getReportDetailRequiresUuidPathAndReturnsDetail() throws Exception {
        ReportHistoryDetailResponse detail = new ReportHistoryDetailResponse();
        detail.setReportId(UUID);
        detail.setName("report");
        when(historyService.getReportDetail(UUID)).thenReturn(detail);

        mockMvc.perform(get("/api/reports/history/{reportId}", UUID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportId").value(UUID))
                .andExpect(jsonPath("$.data.name").value("report"));
    }

    @Test
    void deleteReportDelegatesToService() throws Exception {
        mockMvc.perform(delete("/api/reports/history/{reportId}", UUID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(historyService).deleteReport(UUID);
    }
}
