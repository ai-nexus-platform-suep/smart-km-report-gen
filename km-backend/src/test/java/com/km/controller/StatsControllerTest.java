package com.km.controller;

import com.km.dto.response.StatsSummaryVO;
import com.km.service.StatsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StatsControllerTest {

    @Mock
    private StatsService statsService;

    @InjectMocks
    private StatsController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldReturnSummary() throws Exception {
        StatsSummaryVO summary = new StatsSummaryVO();
        summary.setKnowledgeBaseCount(5);
        summary.setDocumentCount(100);
        summary.setChunkCount(2000);
        summary.setReadyDocumentCount(95);
        summary.setDailyUploadTrend(Collections.emptyList());

        when(statsService.getSummary()).thenReturn(summary);

        mockMvc.perform(get("/api/stats/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.knowledgeBaseCount").value(5))
                .andExpect(jsonPath("$.data.documentCount").value(100))
                .andExpect(jsonPath("$.data.chunkCount").value(2000))
                .andExpect(jsonPath("$.data.readyDocumentCount").value(95));
    }

    @Test
    void shouldWrapInApiResponse() throws Exception {
        StatsSummaryVO summary = new StatsSummaryVO();
        summary.setKnowledgeBaseCount(1);
        summary.setDocumentCount(10);
        summary.setChunkCount(50);
        summary.setReadyDocumentCount(10);
        summary.setDailyUploadTrend(Collections.emptyList());

        when(statsService.getSummary()).thenReturn(summary);

        mockMvc.perform(get("/api/stats/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data").exists());
    }
}
