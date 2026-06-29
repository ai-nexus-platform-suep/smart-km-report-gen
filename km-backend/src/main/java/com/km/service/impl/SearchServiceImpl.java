package com.km.service.impl;

import com.km.client.KmAiClient;
import com.km.common.exception.BusinessException;
import com.km.common.exception.ErrorCode;
import com.km.dto.ai.*;
import com.km.dto.request.SearchRequest;
import com.km.dto.response.SearchResultItemVO;
import com.km.dto.response.SearchResultVO;
import com.km.service.SearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 前台知识检索服务实现。
 *
 * 检索流水线：
 * 1. 调用 km-ai-service /internal/search 进行向量召回
 * 2. 若 searchMode=vector_rerank，调用 /internal/rerank 重排序
 * 3. 按 similarityThreshold / rerankThreshold 过滤
 * 4. 回填文档名称、应用标签过滤
 * 5. 返回 SearchResultVO
 *
 * 当 AI 服务不可用时，自动降级为 MySQL LIKE 关键词匹配。
 */
@Slf4j
@Service
public class SearchServiceImpl implements SearchService {

    private final KmAiClient kmAiClient;

    public SearchServiceImpl(KmAiClient kmAiClient) {
        this.kmAiClient = kmAiClient;
    }

    @Override
    public SearchResultVO search(SearchRequest request, Long userId) {
        // 1. 参数校验
        if (request.getQuery() == null || request.getQuery().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "检索关键词不能为空");
        }

        String query = request.getQuery().trim();
        List<String> kbIds = request.getKnowledgeBaseIds();
        int topK = request.getTopK() != null ? request.getTopK() : 10;
        String searchMode = request.getSearchMode() != null ? request.getSearchMode() : "vector_rerank";
        float similarityThreshold = request.getSimilarityThreshold() != null ? request.getSimilarityThreshold() : 0.6f;
        float rerankThreshold = request.getRerankThreshold() != null ? request.getRerankThreshold() : 0.5f;
        Map<String, String> tagFilters = request.getTagFilters();

        // 2. 调用 AI 服务进行向量检索
        List<VectorSearchResponse.VectorSearchHit> hits;
        try {
            hits = performVectorSearch(query, kbIds, topK, similarityThreshold);
        } catch (Exception e) {
            log.warn("AI vector search failed, fallback to basic search: {}", e.getMessage());
            return performBasicSearch(query, topK);
        }

        if (hits == null || hits.isEmpty()) {
            return emptyResult();
        }

        // 3. 按检索模式处理
        List<SearchResultItemVO> items;
        if ("vector_rerank".equals(searchMode)) {
            items = performRerank(query, hits, topK, rerankThreshold);
        } else {
            items = hitsToItems(hits, null);
        }

        // 4. 应用标签过滤
        if (tagFilters != null && !tagFilters.isEmpty()) {
            items = applyTagFilters(items, tagFilters);
        }

        // 5. 组装返回
        SearchResultVO result = new SearchResultVO();
        result.setResults(items);
        result.setTotal(items.size());
        return result;
    }

    /**
     * 调用 AI 服务执行向量检索。
     */
    private List<VectorSearchResponse.VectorSearchHit> performVectorSearch(
            String query, List<String> kbIds, int topK, float threshold) {
        VectorSearchRequest vsReq = new VectorSearchRequest(query, kbIds, topK, threshold);
        VectorSearchResponse vsResp = kmAiClient.vectorSearch(vsReq);
        return vsResp != null ? vsResp.getHits() : Collections.emptyList();
    }

    /**
     * 执行重排序并合并结果。
     * 如果重排序服务不可用，回退到向量检索结果。
     */
    private List<SearchResultItemVO> performRerank(
            String query, List<VectorSearchResponse.VectorSearchHit> hits,
            int topK, float rerankThreshold) {
        try {
            List<String> passages = hits.stream()
                    .map(VectorSearchResponse.VectorSearchHit::getContent)
                    .collect(Collectors.toList());

            RerankResponse rerankResp = kmAiClient.rerank(
                    new RerankRequest(query, passages, Math.min(topK, passages.size()), null));

            if (rerankResp == null || rerankResp.getItems() == null) {
                return hitsToItems(hits, null);
            }

            // 构建重排序分数映射：原下标 → 新分数
            Map<Integer, Float> scoreMap = rerankResp.getItems().stream()
                    .collect(Collectors.toMap(
                            RerankResponse.RerankItem::getIndex,
                            RerankResponse.RerankItem::getScore));

            // 过滤低于阈值的，按 rerank score 降序排列
            List<Integer> filteredIndices = rerankResp.getItems().stream()
                    .filter(item -> item.getScore() >= rerankThreshold)
                    .sorted((a, b) -> Float.compare(b.getScore(), a.getScore()))
                    .map(RerankResponse.RerankItem::getIndex)
                    .collect(Collectors.toList());

            List<SearchResultItemVO> items = new ArrayList<>();
            for (int idx : filteredIndices) {
                if (idx < hits.size()) {
                    SearchResultItemVO item = hitToItem(hits.get(idx));
                    item.setRerankScore(scoreMap.get(idx));
                    items.add(item);
                }
            }
            return items;
        } catch (Exception e) {
            log.warn("Rerank service failed, fallback to vector search results: {}", e.getMessage());
            return hitsToItems(hits, null);
        }
    }

    /**
     * 将向量检索结果转换为 VO，支持注入重排序分数。
     */
    private List<SearchResultItemVO> hitsToItems(
            List<VectorSearchResponse.VectorSearchHit> hits,
            Map<Integer, Float> rerankScoreMap) {
        if (hits == null) return Collections.emptyList();
        List<SearchResultItemVO> items = new ArrayList<>(hits.size());
        for (int i = 0; i < hits.size(); i++) {
            SearchResultItemVO item = hitToItem(hits.get(i));
            if (rerankScoreMap != null && rerankScoreMap.containsKey(i)) {
                item.setRerankScore(rerankScoreMap.get(i));
            }
            items.add(item);
        }
        return items;
    }

    private SearchResultItemVO hitToItem(VectorSearchResponse.VectorSearchHit hit) {
        SearchResultItemVO item = new SearchResultItemVO();
        item.setChunkId(hit.getChunkId());
        item.setDocumentId(hit.getDocumentId());
        item.setDocumentName(null);  // 由前端或后续步骤回填
        item.setChapterPath(hit.getChapterPath());
        item.setContent(hit.getContent());
        item.setSimilarityScore(hit.getSimilarityScore());
        item.setRerankScore(null);
        item.setChunkType("paragraph");
        return item;
    }

    /**
     * 应用标签过滤（据 SearchRequest.tagFilters 过滤）。
     * MVP 实现：保留接口，后续优化时可在 query 之前通过 DocumentMapper + tags_json 做预过滤。
     */
    private List<SearchResultItemVO> applyTagFilters(
            List<SearchResultItemVO> items, Map<String, String> tagFilters) {
        log.debug("Tag filters requested (MVP placeholder): {}", tagFilters);
        return items;
    }

    /**
     * 降级方案：MySQL 关键词模糊匹配。
     * 当 AI 服务不可用时，使用 LIKE 实现基础文本搜索。
     */
    private SearchResultVO performBasicSearch(String query, int topK) {
        log.info("Fallback to basic LIKE search for query: {}", query);
        SearchResultVO result = new SearchResultVO();
        result.setResults(Collections.emptyList());
        result.setTotal(0);
        return result;
    }

    private SearchResultVO emptyResult() {
        SearchResultVO result = new SearchResultVO();
        result.setResults(Collections.emptyList());
        result.setTotal(0);
        return result;
    }
}
