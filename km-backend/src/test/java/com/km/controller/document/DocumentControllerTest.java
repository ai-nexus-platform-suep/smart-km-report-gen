package com.km.controller.document;

import com.km.common.dto.PageResult;
import com.km.common.exception.BusinessException;
import com.km.common.exception.ErrorCode;
import com.km.controller.support.RequestUserResolver;
import com.km.dto.request.BatchDeleteRequest;
import com.km.dto.request.UpdateDocumentTagsRequest;
import com.km.dto.response.DocumentBatchDeleteResponse;
import com.km.dto.response.DocumentDeleteResponse;
import com.km.dto.response.DocumentUploadResponse;
import com.km.exception.GlobalExceptionHandler;
import com.km.service.DocumentService;
import com.km.vo.ChunkVO;
import com.km.vo.DocumentVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DocumentControllerTest {

    @Mock
    private DocumentService documentService;

    @Mock
    private RequestUserResolver requestUserResolver;

    @InjectMocks
    private DocumentController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldUploadDocument() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "dummy content".getBytes());

        DocumentVO docVO = new DocumentVO();
        docVO.setId("doc-1");
        docVO.setKbId("kb-1");
        docVO.setFilename("test.pdf");

        DocumentUploadResponse uploadResponse = new DocumentUploadResponse(docVO, 5);

        when(requestUserResolver.requireUserId("1")).thenReturn(1L);
        when(documentService.uploadDocument(eq("kb-1"), any(), isNull(), eq(1L)))
                .thenReturn(uploadResponse);

        mockMvc.perform(multipart("/api/knowledge-bases/kb-1/documents")
                        .file(file)
                        .header("userid", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.document.id").value("doc-1"))
                .andExpect(jsonPath("$.data.kbDocCount").value(5));

        verify(documentService).uploadDocument(eq("kb-1"), any(), isNull(), eq(1L));
    }

    @Test
    void shouldUploadDocumentWithDefaultUserId() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "dummy content".getBytes());

        DocumentVO docVO = new DocumentVO();
        docVO.setId("doc-2");
        docVO.setKbId("kb-1");
        docVO.setFilename("test.pdf");

        DocumentUploadResponse uploadResponse = new DocumentUploadResponse(docVO, 1);

        when(requestUserResolver.requireUserId("2")).thenReturn(2L);
        when(documentService.uploadDocument(eq("kb-1"), any(), isNull(), eq(2L)))
                .thenReturn(uploadResponse);

        mockMvc.perform(multipart("/api/knowledge-bases/kb-1/documents")
                        .file(file)
                        .header("userid", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.document.id").value("doc-2"));

        verify(documentService).uploadDocument(eq("kb-1"), any(), isNull(), eq(2L));
    }

    @Test
    void shouldListDocuments() throws Exception {
        DocumentVO doc = new DocumentVO();
        doc.setId("doc-1");
        doc.setStatus("READY");

        PageResult<DocumentVO> pageResult = new PageResult<>(
                Collections.singletonList(doc), 1, 1, 20);

        when(documentService.listDocuments("kb-1", null, 1, 20)).thenReturn(pageResult);

        mockMvc.perform(get("/api/knowledge-bases/kb-1/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value("doc-1"));
    }

    @Test
    void shouldDeleteDocument() throws Exception {
        DocumentDeleteResponse deleteResponse = new DocumentDeleteResponse("doc-1", 3);

        when(documentService.deleteDocument("kb-1", "doc-1")).thenReturn(deleteResponse);

        mockMvc.perform(delete("/api/knowledge-bases/kb-1/documents/doc-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.deletedDocumentId").value("doc-1"))
                .andExpect(jsonPath("$.data.kbDocCount").value(3));

        verify(documentService).deleteDocument("kb-1", "doc-1");
    }

    @Test
    void shouldBatchDeleteDocuments() throws Exception {
        BatchDeleteRequest request = new BatchDeleteRequest();
        request.setIds(Arrays.asList("doc-1", "doc-2"));

        DocumentBatchDeleteResponse batchResponse = new DocumentBatchDeleteResponse(
                Arrays.asList("doc-1", "doc-2"), 5);

        when(documentService.batchDeleteDocuments(eq("kb-1"), anyList())).thenReturn(batchResponse);

        mockMvc.perform(post("/api/knowledge-bases/kb-1/documents/batch-delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.deletedIds[0]").value("doc-1"))
                .andExpect(jsonPath("$.data.deletedIds[1]").value("doc-2"))
                .andExpect(jsonPath("$.data.kbDocCount").value(5));

        verify(documentService).batchDeleteDocuments(eq("kb-1"), anyList());
    }

    @Test
    void shouldGetDocument() throws Exception {
        DocumentVO doc = new DocumentVO();
        doc.setId("doc-1");
        doc.setFilename("report.pdf");
        doc.setStatus("READY");

        when(documentService.getDocument("doc-1")).thenReturn(doc);

        mockMvc.perform(get("/api/documents/doc-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value("doc-1"))
                .andExpect(jsonPath("$.data.filename").value("report.pdf"));
    }

    @Test
    void shouldListChunks() throws Exception {
        ChunkVO chunk = new ChunkVO();
        chunk.setId("chunk-1");
        chunk.setDocId("doc-1");
        chunk.setContent("This is a test chunk.");
        chunk.setChunkIndex(0);

        PageResult<ChunkVO> pageResult = new PageResult<>(
                Collections.singletonList(chunk), 1, 1, 50);

        when(documentService.listChunks("doc-1", 1, 50)).thenReturn(pageResult);

        mockMvc.perform(get("/api/documents/doc-1/chunks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list[0].id").value("chunk-1"))
                .andExpect(jsonPath("$.data.list[0].docId").value("doc-1"));
    }

    @Test
    void shouldDownloadDocument() throws Exception {
        doNothing().when(documentService).downloadDocument(eq("doc-1"), any());

        mockMvc.perform(get("/api/documents/doc-1/download"))
                .andExpect(status().isOk());

        verify(documentService).downloadDocument(eq("doc-1"), any());
    }

    @Test
    void shouldRetryProcess() throws Exception {
        DocumentVO doc = new DocumentVO();
        doc.setId("doc-1");
        doc.setStatus("PROCESSING");

        when(documentService.retryProcess("doc-1")).thenReturn(doc);

        mockMvc.perform(post("/api/documents/doc-1/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value("doc-1"))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));
    }

    @Test
    void shouldUpdateTags() throws Exception {
        Map<String, String> tags = new HashMap<>();
        tags.put("category", "technical");
        tags.put("priority", "high");

        UpdateDocumentTagsRequest request = new UpdateDocumentTagsRequest();
        request.setTags(tags);

        DocumentVO doc = new DocumentVO();
        doc.setId("doc-1");
        doc.setTags(tags);

        when(documentService.updateTags(eq("doc-1"), eq(tags))).thenReturn(doc);

        mockMvc.perform(put("/api/documents/doc-1/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value("doc-1"));

        verify(documentService).updateTags(eq("doc-1"), eq(tags));
    }

    @Test
    void shouldReturnErrorForNonExistentDoc() throws Exception {
        when(documentService.getDocument("non-existent"))
                .thenThrow(new BusinessException(ErrorCode.KM_DOC_001));

        mockMvc.perform(get("/api/documents/non-existent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1003001))
                .andExpect(jsonPath("$.message").value("文档不存在"));
    }
}
