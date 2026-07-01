package com.powerreport.admin.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
 
import com.powerreport.admin.dto.TemplateFileResource;
import com.powerreport.admin.dto.TemplatePageResponse;
import com.powerreport.admin.dto.TemplateResponse;
import com.powerreport.admin.dto.TemplateUpdateRequest;
import com.powerreport.admin.service.TemplateService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ReportTemplateControllerTest {

    @Mock
    private TemplateService templateService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ReportTemplateController(templateService))
                .build();
    }

    @Test
    void listBindsFilters() throws Exception {
        TemplatePageResponse page = new TemplatePageResponse();
        page.setPage(2);
        page.setSize(20);
        page.setTotal(1L);
        page.setRecords(List.of(template("template-1")));
        when(templateService.list(2, 20, "SUMMER_PEAK_CHECK", true, "daily")).thenReturn(page);

        mockMvc.perform(get("/api/admin/templates")
                        .param("page", "2")
                        .param("size", "20")
                        .param("reportType", "SUMMER_PEAK_CHECK")
                        .param("enabled", "true")
                        .param("keyword", "daily"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").value("template-1"));
    }

    @Test
    void uploadPassesMultipartAndUsernameToService() throws Exception {
        when(templateService.upload(any(), eq("Daily"), eq("SUMMER_PEAK_CHECK"), eq("1.0.0"),
                eq("{}"), eq(true), eq("alice"))).thenReturn(template("template-1"));

        mockMvc.perform(multipart("/api/admin/templates")
                        .file(new MockMultipartFile("file", "template.docx",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "docx".getBytes(StandardCharsets.UTF_8)))
                        .param("name", "Daily")
                        .param("reportType", "SUMMER_PEAK_CHECK")
                        .param("version", "1.0.0")
                        .param("configJson", "{}")
                        .param("enabled", "true")
                        .header("X-Username", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("template-1"));
    }

    @Test
    void updateConfigAndDeleteDelegateToService() throws Exception {
        when(templateService.update(eq("template-1"), any(TemplateUpdateRequest.class))).thenReturn(template("template-1"));
        when(templateService.updateConfig(eq("template-1"), any())).thenReturn(template("template-1"));

        mockMvc.perform(put("/api/admin/templates/template-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Updated",
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("template-1"));

        mockMvc.perform(put("/api/admin/templates/template-1/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "configJson": "{\\"title\\":\\"x\\"}"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("template-1"));

        mockMvc.perform(delete("/api/admin/templates/template-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(templateService).delete("template-1");
    }

    @Test
    void downloadReturnsAttachment() throws Exception {
        byte[] bytes = "template-bytes".getBytes(StandardCharsets.UTF_8);
        when(templateService.loadFile("template-1"))
                .thenReturn(new TemplateFileResource(
                        "template.docx",
                        new ByteArrayResource(bytes),
                        bytes.length
                ));

        mockMvc.perform(get("/api/admin/templates/template-1/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("template.docx")))
                .andExpect(content().bytes(bytes));
    }

    private TemplateResponse template(String id) {
        TemplateResponse response = new TemplateResponse();
        response.setId(id);
        response.setName("Daily");
        response.setReportType("SUMMER_PEAK_CHECK");
        response.setVersion("1.0.0");
        response.setEnabled(true);
        return response;
    }
}
