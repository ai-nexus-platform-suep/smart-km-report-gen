package com.powerreport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
 
import com.powerreport.common.GlobalExceptionHandler;
import com.powerreport.dto.OutlineConfirmRequest;
import com.powerreport.dto.OutlineGenerateRequest;
import com.powerreport.dto.OutlineNodeResponse;
import com.powerreport.dto.ReportDocxExportRequest;
import com.powerreport.dto.ReportFileResponse;
import com.powerreport.dto.StoredReportFile;
import com.powerreport.enums.CaptionNumberingMode;
import com.powerreport.enums.ReportType;
import com.powerreport.service.DocxExportService;
import com.powerreport.service.OutlineService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    @Mock
    private OutlineService outlineService;

    @Mock
    private DocxExportService docxExportService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ReportController(outlineService, docxExportService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void healthReturnsServiceScope() throws Exception {
        MockMvc healthMvc = MockMvcBuilders
                .standaloneSetup(new HealthController())
                .build();

        healthMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("ok"))
                .andExpect(jsonPath("$.data.scope").value("outline-and-docx"));
    }

    @Test
    void outlineReturnsFixedTemplate() throws Exception {
        OutlineNodeResponse node = new OutlineNodeResponse();
        node.setId("node-1");
        node.setNumber("1");
        node.setTitle("检查概况");
        node.setLevel(1);
        when(outlineService.buildOutline(ReportType.SUMMER_PEAK_CHECK)).thenReturn(List.of(node));

        mockMvc.perform(post("/api/reports/outline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reportType":"SUMMER_PEAK_CHECK"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].number").value("1"))
                .andExpect(jsonPath("$.data[0].title").value("检查概况"));
    }

    @Test
    void outlineRejectsMissingReportType() throws Exception {
        mockMvc.perform(post("/api/reports/outline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", containsString("reportType")));
    }

    @Test
    void generateOutlineRejectsBlankSubject() throws Exception {
        mockMvc.perform(post("/api/reports/outline/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reportType":"SUMMER_PEAK_CHECK",
                                  "subject":" ",
                                  "reportYear":2026
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", containsString("subject")));
    }

    @Test
    void confirmOutlineMapsIllegalArgumentToBadRequest() throws Exception {
        when(outlineService.confirmOutline(any(OutlineConfirmRequest.class)))
                .thenThrow(new IllegalArgumentException("outline 为空时必须传 tempId"));

        mockMvc.perform(post("/api/reports/outline/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reportType":"SUMMER_PEAK_CHECK",
                                  "subject":"2026 年迎峰度夏专项检查",
                                  "specialty":"电气",
                                  "powerPlant":"示例电厂",
                                  "reportYear":2026,
                                  "outline":[]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("outline 为空时必须传 tempId"));
    }

    @Test
    void exportDocxUsesDefaultRequestWhenBodyIsMissing() throws Exception {
        when(docxExportService.exportReport(any(String.class), any(ReportDocxExportRequest.class)))
                .thenReturn(new ReportFileResponse(
                        "file-1",
                        "report-1",
                        "report.docx",
                        128L,
                        "0".repeat(64),
                        "/api/reports/files/file-1/download"
                ));

        mockMvc.perform(post("/api/reports/report-1/export/docx"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.fileId").value("file-1"))
                .andExpect(jsonPath("$.data.downloadUrl").value("/api/reports/files/file-1/download"));

        ArgumentCaptor<ReportDocxExportRequest> requestCaptor =
                ArgumentCaptor.forClass(ReportDocxExportRequest.class);
        verify(docxExportService).exportReport(any(String.class), requestCaptor.capture());
        assertThat(requestCaptor.getValue().getFigureNumberingMode()).isEqualTo(CaptionNumberingMode.GLOBAL);
        assertThat(requestCaptor.getValue().getTableNumberingMode()).isEqualTo(CaptionNumberingMode.GLOBAL);
        assertThat(requestCaptor.getValue().getIncludeEmptySections()).isTrue();
    }

    @Test
    void downloadFileReturnsDocxStream(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("示例报告.docx");
        Files.writeString(file, "fake-docx-content");
        when(docxExportService.getFileForDownload("file-1"))
                .thenReturn(new StoredReportFile("file-1", "示例报告.docx", file, Files.size(file)));

        mockMvc.perform(get("/api/reports/files/file-1/download"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("filename*=UTF-8''")))
                .andExpect(content().bytes(Files.readAllBytes(file)));
    }
}
