package com.km.controller.internal;

import com.km.dto.request.DocumentStatusUpdateRequest;
import com.km.service.DocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * InternalDocumentController 单元测试。
 * 覆盖：POST /internal/documents/status 文档状态回调接口。
 */
@ExtendWith(MockitoExtension.class)
class InternalDocumentControllerTest {

    @Mock
    private DocumentService documentService;

    @InjectMocks
    private InternalDocumentController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // ====== POST /internal/documents/status ======

    @Test
    void shouldUpdateStatusSuccessfully() throws Exception {
        String requestBody = "{" +
                "\"documentId\": \"doc-123\"," +
                "\"status\": \"READY\"," +
                "\"errorMsg\": null," +
                "\"jobId\": \"job-uuid-001\"," +
                "\"attempt\": 1," +
                "\"chunkCount\": 15" +
                "}";

        mockMvc.perform(post("/internal/documents/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(documentService).updateStatus("doc-123", "READY", null);
    }

    @Test
    void shouldUpdateStatusWithErrorMessage() throws Exception {
        String requestBody = "{" +
                "\"documentId\": \"doc-456\"," +
                "\"status\": \"FAILED\"," +
                "\"errorMsg\": \"文件解析超时\"," +
                "\"jobId\": \"job-uuid-002\"," +
                "\"attempt\": 3," +
                "\"chunkCount\": 0" +
                "}";

        mockMvc.perform(post("/internal/documents/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(documentService).updateStatus("doc-456", "FAILED", "文件解析超时");
    }

    @Test
    void shouldHandleParsingStatus() throws Exception {
        String requestBody = "{" +
                "\"documentId\": \"doc-789\"," +
                "\"status\": \"PARSING\"," +
                "\"errorMsg\": null," +
                "\"jobId\": \"job-uuid-003\"," +
                "\"attempt\": 1," +
                "\"chunkCount\": 0" +
                "}";

        mockMvc.perform(post("/internal/documents/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        verify(documentService).updateStatus("doc-789", "PARSING", null);
    }

    @Test
    void shouldNotCallServiceWhenValidationFails() throws Exception {
        // 缺少必填字段 documentId
        String invalidBody = "{" +
                "\"status\": \"READY\"" +
                "}";

        mockMvc.perform(post("/internal/documents/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());

        verify(documentService, never()).updateStatus(anyString(), anyString(), any());
    }
}
