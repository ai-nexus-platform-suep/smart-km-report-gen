package com.powerreport.admin.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
 
import com.powerreport.admin.dto.AdminStatsDashboardResponse;
import com.powerreport.admin.dto.AdminStatsOverviewResponse;
import com.powerreport.admin.dto.AdminStatsTrendResponse;
import com.powerreport.admin.service.AdminStatsService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AdminStatsControllerTest {

    @Mock
    private AdminStatsService adminStatsService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminStatsController(adminStatsService))
                .build();
    }

    @Test
    void overviewReturnsServicePayload() throws Exception {
        AdminStatsOverviewResponse response = new AdminStatsOverviewResponse();
        response.setTemplateCount(2L);
        response.setReportCount(3L);
        response.setUserCount(1L);
        response.setSectionCount(8L);
        when(adminStatsService.overview()).thenReturn(response);

        mockMvc.perform(get("/api/admin/stats/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.templateCount").value(2))
                .andExpect(jsonPath("$.data.reportCount").value(3));
    }

    @Test
    void trendBindsDaysParameter() throws Exception {
        AdminStatsTrendResponse item = new AdminStatsTrendResponse();
        item.setDate("2026-07-01");
        item.setCount(5L);
        when(adminStatsService.trend(7)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/admin/stats/trend").param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].date").value("2026-07-01"))
                .andExpect(jsonPath("$.data[0].count").value(5));
    }

    @Test
    void dashboardBindsBothQueryParameters() throws Exception {
        AdminStatsDashboardResponse response = new AdminStatsDashboardResponse();
        response.setTrends(List.of());
        response.setRecentTasks(List.of());
        response.setHealth(List.of());
        response.setAlerts(List.of());
        when(adminStatsService.dashboard(eq(14), eq(3))).thenReturn(response);

        mockMvc.perform(get("/api/admin/stats/dashboard")
                        .param("days", "14")
                        .param("recentLimit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
