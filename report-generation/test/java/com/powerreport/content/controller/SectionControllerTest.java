package com.powerreport.content.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerreport.content.dto.SectionContentRequest;
import com.powerreport.content.dto.SectionGenerateResponse;
import com.powerreport.content.dto.SectionRegenerateRequest;
import com.powerreport.content.dto.SectionResponse;
import com.powerreport.content.service.SectionService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class SectionControllerTest {

    @Mock
    private SectionService sectionService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SectionController(sectionService))
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void generateSectionsReturnsTaskPayload() throws Exception {
        when(sectionService.startGeneration("report-1"))
                .thenReturn(new SectionGenerateResponse("task-1", "report-1", null, "CONTENT_GENERATING", 3, 1, "ok"));

        mockMvc.perform(post("/api/reports/report-1/sections/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").value("task-1"))
                .andExpect(jsonPath("$.data.totalSections").value(3));
    }

    @Test
    void streamSectionsReturnsSseEmitter() throws Exception {
        when(sectionService.streamSections("report-1")).thenReturn(new SseEmitter(0L));

        mockMvc.perform(get("/api/reports/report-1/sections/stream"))
                .andExpect(status().isOk());
    }

    @Test
    void saveSectionBindsBodyAndPathVariables() throws Exception {
        SectionResponse response = section("section-1", "updated markdown");
        when(sectionService.saveSection(eq("report-1"), eq("section-1"), any(SectionContentRequest.class)))
                .thenReturn(response);

        SectionContentRequest request = new SectionContentRequest();
        request.setContentMarkdown("updated markdown");

        mockMvc.perform(put("/api/reports/report-1/sections/section-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sectionId").value("section-1"))
                .andExpect(jsonPath("$.data.contentMarkdown").value("updated markdown"));
    }

    @Test
    void listSectionsReturnsServicePayload() throws Exception {
        when(sectionService.listSections("report-1"))
                .thenReturn(List.of(section("section-1", "body")));

        mockMvc.perform(get("/api/reports/report-1/sections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sectionId").value("section-1"));
    }

    @Test
    void regenerateSectionUsesEmptyRequestWhenBodyIsMissing() throws Exception {
        when(sectionService.regenerateSection(eq("report-1"), eq("section-1"), any(SectionRegenerateRequest.class)))
                .thenReturn(new SectionGenerateResponse("task-2", "report-1", "section-1", "GENERATING", null, 0, "ok"));

        mockMvc.perform(post("/api/reports/report-1/sections/section-1/regenerate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sectionId").value("section-1"));

        verify(sectionService).regenerateSection(eq("report-1"), eq("section-1"), any(SectionRegenerateRequest.class));
    }

    private SectionResponse section(String sectionId, String content) {
        SectionResponse response = new SectionResponse();
        response.setSectionId(sectionId);
        response.setReportId("report-1");
        response.setOutlineNodeId("node-1");
        response.setNumber("1");
        response.setTitle("Overview");
        response.setContentMarkdown(content);
        response.setStatus("GENERATED");
        response.setVersion(1);
        return response;
    }
}
