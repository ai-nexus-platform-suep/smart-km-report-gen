package com.km.controller.knowledge;

import com.km.common.dto.PageResult;
import com.km.dto.request.BatchDeleteRequest;
import com.km.dto.request.CreateKnowledgeBaseRequest;
import com.km.dto.request.UpdateKnowledgeBaseRequest;
import com.km.dto.response.KnowledgeBaseVO;
import com.km.controller.support.RequestUserResolver;
import com.km.service.KnowledgeBaseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseControllerTest {

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    @Mock
    private RequestUserResolver requestUserResolver;

    @InjectMocks
    private KnowledgeBaseController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldListKnowledgeBases() throws Exception {
        KnowledgeBaseVO kb = new KnowledgeBaseVO();
        kb.setId("kb-1");
        kb.setName("Test KB");

        PageResult<KnowledgeBaseVO> pageResult = new PageResult<>(
                Collections.singletonList(kb), 1, 1, 20);

        when(knowledgeBaseService.list(null, null, 1, 20)).thenReturn(pageResult);

        mockMvc.perform(get("/api/knowledge-bases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value("kb-1"))
                .andExpect(jsonPath("$.data.list[0].name").value("Test KB"));
    }

    @Test
    void shouldListWithFilter() throws Exception {
        PageResult<KnowledgeBaseVO> pageResult = new PageResult<>(
                Collections.emptyList(), 0, 1, 20);

        when(knowledgeBaseService.list("pdf", "keyword", 1, 20)).thenReturn(pageResult);

        mockMvc.perform(get("/api/knowledge-bases")
                        .param("docType", "pdf")
                        .param("keyword", "keyword"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0));

        verify(knowledgeBaseService).list("pdf", "keyword", 1, 20);
    }

    @Test
    void shouldCreateKnowledgeBase() throws Exception {
        CreateKnowledgeBaseRequest request = new CreateKnowledgeBaseRequest();
        request.setName("New KB");
        request.setDescription("A test knowledge base");
        request.setDocType("通用文档");
        request.setChunkStrategy(java.util.Collections.singletonMap("type", "heading"));
        request.setSearchStrategy("vector_rerank");

        KnowledgeBaseVO kb = new KnowledgeBaseVO();
        kb.setId("kb-new");
        kb.setName("New KB");
        kb.setDescription("A test knowledge base");
        kb.setDocType("通用文档");

        when(requestUserResolver.requireUserId("1")).thenReturn(1L);
        when(knowledgeBaseService.create(any(CreateKnowledgeBaseRequest.class), eq(1L))).thenReturn(kb);

        mockMvc.perform(post("/api/knowledge-bases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("userid", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value("kb-new"))
                .andExpect(jsonPath("$.data.name").value("New KB"));

        verify(knowledgeBaseService).create(any(CreateKnowledgeBaseRequest.class), eq(1L));
    }

    @Test
    void shouldBatchDelete() throws Exception {
        BatchDeleteRequest request = new BatchDeleteRequest();
        request.setIds(Arrays.asList("kb-1", "kb-2"));

        doNothing().when(knowledgeBaseService).batchDelete(anyList());

        mockMvc.perform(delete("/api/knowledge-bases/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(knowledgeBaseService).batchDelete(anyList());
    }

    @Test
    void shouldGetKnowledgeBase() throws Exception {
        KnowledgeBaseVO kb = new KnowledgeBaseVO();
        kb.setId("kb-1");
        kb.setName("Test KB");
        kb.setDocType("pdf");

        when(knowledgeBaseService.getById("kb-1")).thenReturn(kb);

        mockMvc.perform(get("/api/knowledge-bases/kb-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value("kb-1"))
                .andExpect(jsonPath("$.data.name").value("Test KB"));
    }

    @Test
    void shouldUpdateKnowledgeBase() throws Exception {
        UpdateKnowledgeBaseRequest request = new UpdateKnowledgeBaseRequest();
        request.setName("Updated KB");
        request.setDescription("Updated description");

        KnowledgeBaseVO kb = new KnowledgeBaseVO();
        kb.setId("kb-1");
        kb.setName("Updated KB");
        kb.setDescription("Updated description");

        when(knowledgeBaseService.update(eq("kb-1"), any(UpdateKnowledgeBaseRequest.class))).thenReturn(kb);

        mockMvc.perform(put("/api/knowledge-bases/kb-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value("kb-1"))
                .andExpect(jsonPath("$.data.name").value("Updated KB"));

        verify(knowledgeBaseService).update(eq("kb-1"), any(UpdateKnowledgeBaseRequest.class));
    }

    @Test
    void shouldDeleteKnowledgeBase() throws Exception {
        doNothing().when(knowledgeBaseService).deleteById("kb-1");

        mockMvc.perform(delete("/api/knowledge-bases/kb-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(knowledgeBaseService).deleteById("kb-1");
    }
}
