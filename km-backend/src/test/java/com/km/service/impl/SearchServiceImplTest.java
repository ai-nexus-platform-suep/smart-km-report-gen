package com.km.service.impl;

import com.km.client.KmAiClient;
import com.km.common.exception.BusinessException;
import com.km.dto.ai.*;
import com.km.repository.ChunkMapper;
import com.km.repository.DocumentMapper;
import com.km.dto.request.SearchRequest;
import com.km.dto.response.SearchResultItemVO;
import com.km.dto.response.SearchResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SearchServiceImpl 单元测试。
 * 覆盖：参数校验、向量检索、重排序、降级、空结果。
 */
@ExtendWith(MockitoExtension.class)
class SearchServiceImplTest {

    @Mock
    private KmAiClient kmAiClient;

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private ChunkMapper chunkMapper;

    private SearchServiceImpl searchService;

    private final Long mockUserId = 1L;

    @BeforeEach
    void setUp() {
        searchService = new SearchServiceImpl(kmAiClient, documentMapper, chunkMapper);
    }

    @Test
    void shouldThrowExceptionWhenQueryIsEmpty() {
        SearchRequest request = new SearchRequest();
        request.setQuery("   ");

        assertThrows(BusinessException.class, () -> searchService.search(request, mockUserId));
    }

    @Test
    void shouldThrowExceptionWhenQueryIsNull() {
        SearchRequest request = new SearchRequest();
        request.setQuery(null);

        assertThrows(BusinessException.class, () -> searchService.search(request, mockUserId));
    }

    @Test
    void shouldReturnEmptyResultWhenAiServiceReturnsNull() {
        SearchRequest request = createDefaultRequest("变压器油温异常");
        when(kmAiClient.vectorSearch(any())).thenReturn(null);

        SearchResultVO result = searchService.search(request, mockUserId);

        assertNotNull(result);
        assertEquals(0, result.getTotal());
        assertTrue(result.getResults().isEmpty());
    }

    @Test
    void shouldReturnEmptyResultWhenAiServiceReturnsEmptyList() {
        SearchRequest request = createDefaultRequest("变压器油温异常");
        VectorSearchResponse response = new VectorSearchResponse();
        response.setHits(Collections.emptyList());
        when(kmAiClient.vectorSearch(any())).thenReturn(response);

        SearchResultVO result = searchService.search(request, mockUserId);

        assertNotNull(result);
        assertEquals(0, result.getTotal());
        assertTrue(result.getResults().isEmpty());
    }

    @Test
    void shouldFallbackToEmptyResultWhenAiServiceThrows() {
        SearchRequest request = createDefaultRequest("变压器油温异常");
        when(kmAiClient.vectorSearch(any())).thenThrow(new RuntimeException("Connection refused"));

        SearchResultVO result = searchService.search(request, mockUserId);

        assertNotNull(result);
        assertEquals(0, result.getTotal());
        assertTrue(result.getResults().isEmpty());
    }

    @Test
    void shouldReturnVectorSearchResultsInDefaultMode() {
        // 默认 searchMode = "vector_rerank"，但重排序服务异常时回退
        SearchRequest request = createDefaultRequest("变压器油温异常");
        request.setSearchMode("vector");

        VectorSearchResponse response = createVectorSearchResponse(3);
        when(kmAiClient.vectorSearch(any())).thenReturn(response);

        SearchResultVO result = searchService.search(request, mockUserId);

        assertNotNull(result);
        assertEquals(3, result.getTotal());
        assertEquals(3, result.getResults().size());

        SearchResultItemVO first = result.getResults().get(0);
        assertNotNull(first.getChunkId());
        assertNotNull(first.getContent());
        assertNotNull(first.getSimilarityScore());
        assertNull(first.getRerankScore()); // vector 模式不设置 rerankScore
    }

    @Test
    void shouldPerformRerankInVectorRerankMode() {
        SearchRequest request = createDefaultRequest("变压器油温异常");
        request.setSearchMode("vector_rerank");

        VectorSearchResponse vsResponse = createVectorSearchResponse(3);
        when(kmAiClient.vectorSearch(any())).thenReturn(vsResponse);

        // 重排序返回：第1条 0.92，第2条 0.55，第3条 0.30
        RerankResponse rerankResponse = createRerankResponse(
                Arrays.asList(0, 1, 2),
                Arrays.asList(0.92f, 0.55f, 0.30f));
        when(kmAiClient.rerank(any())).thenReturn(rerankResponse);

        SearchResultVO result = searchService.search(request, mockUserId);

        assertNotNull(result);
        // rerankThreshold=0.5，过滤掉第3条（0.30）
        assertEquals(2, result.getTotal());

        // 验证按 rerankScore 降序排列
        assertEquals(0.92f, result.getResults().get(0).getRerankScore(), 0.001f);
        assertEquals(0.55f, result.getResults().get(1).getRerankScore(), 0.001f);
    }

    @Test
    void shouldFallbackToVectorResultsWhenRerankFails() {
        SearchRequest request = createDefaultRequest("变压器油温异常");
        request.setSearchMode("vector_rerank");

        VectorSearchResponse vsResponse = createVectorSearchResponse(3);
        when(kmAiClient.vectorSearch(any())).thenReturn(vsResponse);
        when(kmAiClient.rerank(any())).thenThrow(new RuntimeException("Rerank timeout"));

        SearchResultVO result = searchService.search(request, mockUserId);

        assertNotNull(result);
        assertEquals(3, result.getTotal());
    }

    @Test
    void shouldHonorSimilarityThreshold() {
        SearchRequest request = createDefaultRequest("变压器油温异常");
        request.setSearchMode("vector");
        request.setSimilarityThreshold(0.7f);

        // AI 服务内部已做阈值过滤，这里验证 mock 传参
        VectorSearchResponse response = createVectorSearchResponse(2);
        when(kmAiClient.vectorSearch(any())).thenReturn(response);

        SearchResultVO result = searchService.search(request, mockUserId);

        assertNotNull(result);
        verify(kmAiClient).vectorSearch(argThat(req ->
                req.getSimilarityThreshold() != null && req.getSimilarityThreshold() == 0.7f));
    }

    // ====== 辅助方法 ======

    private SearchRequest createDefaultRequest(String query) {
        SearchRequest request = new SearchRequest();
        request.setQuery(query);
        request.setKnowledgeBaseIds(Arrays.asList("kb-1", "kb-2"));
        request.setTopK(10);
        request.setSearchMode("vector_rerank");
        request.setSimilarityThreshold(0.6f);
        request.setRerankThreshold(0.5f);
        return request;
    }

    private VectorSearchResponse createVectorSearchResponse(int count) {
        VectorSearchResponse response = new VectorSearchResponse();
        List<VectorSearchResponse.VectorSearchHit> hits = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            VectorSearchResponse.VectorSearchHit hit = new VectorSearchResponse.VectorSearchHit();
            hit.setChunkId("chunk-" + i);
            hit.setDocumentId("doc-" + i);
            hit.setContent("这是第" + (i + 1) + "条匹配结果的内容。" +
                    "变压器油温异常时应立即检查冷却系统运行状态。");
            hit.setChapterPath("第" + (i + 1) + "章 > 第" + (i + 1) + "节");
            hit.setSimilarityScore(0.9f - i * 0.1f);
            hits.add(hit);
        }
        response.setHits(hits);
        return response;
    }

    private RerankResponse createRerankResponse(List<Integer> indices, List<Float> scores) {
        RerankResponse response = new RerankResponse();
        List<RerankResponse.RerankItem> items = new ArrayList<>();
        for (int i = 0; i < indices.size(); i++) {
            RerankResponse.RerankItem item = new RerankResponse.RerankItem();
            item.setIndex(indices.get(i));
            item.setScore(scores.get(i));
            items.add(item);
        }
        response.setItems(items);
        return response;
    }
}
