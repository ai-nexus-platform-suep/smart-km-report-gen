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

    @Test
    void shouldDelegateHybridSearchToAiService() {
        SearchRequest request = createDefaultRequest("变压器油温异常");
        request.setSearchMode("hybrid");
        request.setTopK(3);
        request.setBm25Weight(0.5f);
        request.setVectorWeight(0.5f);
        HybridSearchResponse response = new HybridSearchResponse();
        response.setHits(Collections.singletonList(createHybridHit("chunk-a", "doc-a", 0.8f, 12f, 0.9f)));
        when(kmAiClient.hybridSearch(any())).thenReturn(response);

        SearchResultVO result = searchService.search(request, mockUserId);

        assertEquals(1, result.getTotal());
        assertEquals("chunk-a", result.getResults().get(0).getChunkId());
        assertEquals(12f, result.getResults().get(0).getBm25Score(), 0.001f);
        assertEquals(0.8f, result.getResults().get(0).getSimilarityScore(), 0.001f);
        assertEquals(0.9f, result.getResults().get(0).getHybridScore(), 0.001f);
        verify(kmAiClient).hybridSearch(argThat(req ->
                req.getTopK() == 3
                        && req.getBm25Weight() == 0.5f
                        && req.getVectorWeight() == 0.5f
                        && req.getSimilarityThreshold() == 0.6f));
        verify(kmAiClient, never()).vectorSearch(any());
    }

    @Test
    void shouldRejectInvalidHybridWeights() {
        SearchRequest request = createDefaultRequest("变压器油温异常");
        request.setSearchMode("hybrid");
        request.setBm25Weight(0f);
        request.setVectorWeight(0f);

        assertThrows(BusinessException.class, () -> searchService.search(request, mockUserId));
        verify(kmAiClient, never()).hybridSearch(any());
    }

    @Test
    void shouldRejectNanHybridWeights() {
        SearchRequest request = createDefaultRequest("变压器油温异常");
        request.setSearchMode("hybrid");
        request.setBm25Weight(Float.NaN);
        request.setVectorWeight(1f);

        assertThrows(BusinessException.class, () -> searchService.search(request, mockUserId));
        verify(kmAiClient, never()).hybridSearch(any());
    }

    @Test
    void shouldFallbackToBasicSearchWhenAiHybridSearchFails() {
        SearchRequest request = createDefaultRequest("变压器油温异常");
        request.setSearchMode("hybrid");
        when(kmAiClient.hybridSearch(any())).thenThrow(new RuntimeException("timeout"));
        when(chunkMapper.searchByKeyword(anyString(), anyList(), anyInt())).thenReturn(Collections.emptyList());

        SearchResultVO result = searchService.search(request, mockUserId);

        assertEquals(0, result.getTotal());
        verify(kmAiClient).hybridSearch(any());
        verify(chunkMapper).searchByKeyword(anyString(), anyList(), anyInt());
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

    private VectorSearchResponse.VectorSearchHit createVectorHit(String chunkId, String docId, float score) {
        VectorSearchResponse.VectorSearchHit hit = new VectorSearchResponse.VectorSearchHit();
        hit.setChunkId(chunkId);
        hit.setDocumentId(docId);
        hit.setContent("vector content " + chunkId);
        hit.setChapterPath("vector path");
        hit.setSimilarityScore(score);
        return hit;
    }

    private HybridSearchResponse.HybridSearchHit createHybridHit(String chunkId, String docId, float similarityScore,
                                                                 float bm25Score, float hybridScore) {
        HybridSearchResponse.HybridSearchHit hit = new HybridSearchResponse.HybridSearchHit();
        hit.setChunkId(chunkId);
        hit.setDocumentId(docId);
        hit.setContent("hybrid content " + chunkId);
        hit.setChapterPath("hybrid path");
        hit.setChunkType("paragraph");
        hit.setSimilarityScore(similarityScore);
        hit.setBm25Score(bm25Score);
        hit.setHybridScore(hybridScore);
        return hit;
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
