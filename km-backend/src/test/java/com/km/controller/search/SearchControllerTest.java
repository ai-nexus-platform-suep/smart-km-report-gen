package com.km.controller.search;

import com.km.dto.request.SearchRequest;
import com.km.dto.response.SearchResultItemVO;
import com.km.dto.response.SearchResultVO;
import com.km.service.SearchService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SearchControllerTest {

    @Mock
    private SearchService searchService;

    @InjectMocks
    private SearchController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldSearchWithDefaultUserId() throws Exception {
        SearchRequest request = new SearchRequest();
        request.setQuery("test query");

        SearchResultVO result = new SearchResultVO();
        result.setTotal(0);
        result.setResults(Arrays.asList());

        when(searchService.search(any(SearchRequest.class), eq(0L))).thenReturn(result);

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(searchService).search(any(SearchRequest.class), eq(0L));
    }

    @Test
    void shouldSearchWithCustomUserId() throws Exception {
        SearchRequest request = new SearchRequest();
        request.setQuery("test query");

        SearchResultVO result = new SearchResultVO();
        result.setTotal(0);
        result.setResults(Arrays.asList());

        when(searchService.search(any(SearchRequest.class), eq(123L))).thenReturn(result);

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-User-Id", "123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(searchService).search(any(SearchRequest.class), eq(123L));
    }

    @Test
    void shouldSearchWithInvalidUserIdHeader() throws Exception {
        SearchRequest request = new SearchRequest();
        request.setQuery("test query");

        SearchResultVO result = new SearchResultVO();
        result.setTotal(0);
        result.setResults(Arrays.asList());

        when(searchService.search(any(SearchRequest.class), eq(0L))).thenReturn(result);

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-User-Id", "abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(searchService).search(any(SearchRequest.class), eq(0L));
    }

    @Test
    void shouldReturnSearchResults() throws Exception {
        SearchRequest request = new SearchRequest();
        request.setQuery("变压器油温");

        SearchResultItemVO item = new SearchResultItemVO();
        item.setChunkId("chunk-1");
        item.setDocumentId("doc-1");
        item.setDocumentName("设备检修手册.pdf");
        item.setContent("变压器油温异常时应立即检查冷却系统。");
        item.setSimilarityScore(0.92f);
        item.setRerankScore(0.88f);

        SearchResultVO result = new SearchResultVO();
        result.setTotal(1);
        result.setResults(Arrays.asList(item));

        when(searchService.search(any(SearchRequest.class), eq(0L))).thenReturn(result);

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.results[0].chunkId").value("chunk-1"))
                .andExpect(jsonPath("$.data.results[0].documentName").value("设备检修手册.pdf"))
                .andExpect(jsonPath("$.data.results[0].similarityScore").value(0.92))
                .andExpect(jsonPath("$.data.results[0].rerankScore").value(0.88));
    }
}
