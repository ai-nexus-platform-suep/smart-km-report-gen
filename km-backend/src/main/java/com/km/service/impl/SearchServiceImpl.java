package com.km.service.impl;

import com.km.client.KmAiClient;
import com.km.common.exception.BusinessException;
import com.km.common.exception.ErrorCode;
import com.km.dto.ai.*;
import com.km.dto.request.SearchRequest;
import com.km.dto.response.SearchResultItemVO;
import com.km.dto.response.SearchResultVO;
import com.km.entity.Chunk;
import com.km.entity.Document;
import com.km.repository.ChunkMapper;
import com.km.repository.DocumentMapper;
import com.km.service.SearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 检索流水线编排实现。
 *
 * 主流程：向量检索 → 重排序 → 回填文档名 → 截断 topK → 返回
 * 降级：AI 服务不可用时，走 MySQL LIKE 关键词匹配
 */
@Slf4j
@Service
public class SearchServiceImpl implements SearchService {

    private final KmAiClient kmAiClient;
    private final DocumentMapper documentMapper;
    private final ChunkMapper chunkMapper;

    public SearchServiceImpl(KmAiClient kmAiClient,
                             DocumentMapper documentMapper,
                             ChunkMapper chunkMapper) {
        this.kmAiClient = kmAiClient;
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
    }

    @Override
    public SearchResultVO search(SearchRequest request, Long userId) {
        String query = request.getQuery();
        if (query == null || query.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "检索关键词不能为空");
        }
        query = query.trim();

        int topK = request.getTopK() != null ? request.getTopK() : 10;
        String searchMode = request.getSearchMode() != null ? request.getSearchMode() : "vector_rerank";
        float rerankThreshold = request.getRerankThreshold() != null ? request.getRerankThreshold() : 0.5f;

        List<String> kbIds = request.getKnowledgeBaseIds();
        float similarityThreshold = request.getSimilarityThreshold() != null ? request.getSimilarityThreshold() : 0.6f;

        // 调用向量检索（AI 服务）
        List<VectorSearchResponse.VectorSearchHit> hits;
        boolean aiAvailable = true;
        try {
            hits = performVectorSearch(query, kbIds, topK, similarityThreshold);
        } catch (Exception e) {
            log.warn("AI vector search failed: {}, falling back to LIKE search", e.getMessage());
            aiAvailable = false;
            hits = null;
        }

        List<SearchResultItemVO> items;

        if (hits == null || hits.isEmpty()) {
            if (!aiAvailable) {
                // AI 不可用，走 MySQL LIKE 降级
                items = performBasicSearch(query, topK);
            } else {
                items = Collections.emptyList();
            }
        } else {
            // 按检索模式处理
            if ("vector_rerank".equals(searchMode)) {
                items = performRerank(query, hits, topK, rerankThreshold);
            } else {
                items = hitsToItems(hits, null);
            }
            // 回填文档名称
            backfillDocumentNames(items);
        }

        SearchResultVO result = new SearchResultVO();
        result.setResults(items);
        result.setTotal(items.size());
        return result;
    }

    // ========== 向量检索 ==========

    private List<VectorSearchResponse.VectorSearchHit> performVectorSearch(
            String query, List<String> kbIds, int topK, float threshold) {
        VectorSearchRequest req = new VectorSearchRequest(query, kbIds, topK, threshold);
        VectorSearchResponse resp = kmAiClient.vectorSearch(req);
        return resp != null ? resp.getHits() : Collections.emptyList();
    }

    // ========== 重排序 ==========

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

            Map<Integer, Float> scoreMap = rerankResp.getItems().stream()
                    .collect(Collectors.toMap(
                            RerankResponse.RerankItem::getIndex,
                            RerankResponse.RerankItem::getScore));

            // 过滤低于阈值的，按 rerankScore 降序，截断到 topK
            List<SearchResultItemVO> items = rerankResp.getItems().stream()
                    .filter(item -> item.getScore() >= rerankThreshold)
                    .sorted((a, b) -> Float.compare(b.getScore(), a.getScore()))
                    .limit(topK)
                    .map(item -> {
                        int idx = item.getIndex();
                        SearchResultItemVO vo = hitToItem(hits.get(idx));
                        vo.setRerankScore(scoreMap.get(idx));
                        return vo;
                    })
                    .collect(Collectors.toList());

            return items;
        } catch (Exception e) {
            log.warn("Rerank failed: {}, falling back to vector results", e.getMessage());
            return hitsToItems(hits, null);
        }
    }

    // ========== 结果转换 ==========

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
        item.setDocumentName(null);   // 由 backfillDocumentNames 回填
        item.setChapterPath(hit.getChapterPath());
        item.setContent(hit.getContent());
        item.setSimilarityScore(hit.getSimilarityScore());
        item.setRerankScore(null);
        item.setChunkType("paragraph");
        return item;
    }

    // ========== 回填文档名称 ==========

    private void backfillDocumentNames(List<SearchResultItemVO> items) {
        Set<String> docIds = items.stream()
                .map(SearchResultItemVO::getDocumentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (docIds.isEmpty()) return;

        List<Document> docs = documentMapper.listByIds(new ArrayList<>(docIds));
        Map<String, String> nameMap = docs.stream()
                .collect(Collectors.toMap(Document::getId, Document::getFilename, (a, b) -> a));

        for (SearchResultItemVO item : items) {
            String name = nameMap.get(item.getDocumentId());
            if (name != null) {
                item.setDocumentName(name);
            }
        }
    }

    // ========== 降级方案：MySQL LIKE 关键词匹配 ==========

    private List<SearchResultItemVO> performBasicSearch(String query, int topK) {
        log.info("Fallback to MySQL LIKE search: query={}", query);
        List<Chunk> chunks = chunkMapper.searchByKeyword(query, topK);
        if (chunks == null || chunks.isEmpty()) return Collections.emptyList();

        List<SearchResultItemVO> items = new ArrayList<>(chunks.size());
        for (Chunk chunk : chunks) {
            SearchResultItemVO item = new SearchResultItemVO();
            item.setChunkId(chunk.getId());
            item.setDocumentId(chunk.getDocId());
            item.setDocumentName(null);
            item.setChapterPath(chunk.getChapterPath());
            item.setContent(chunk.getContent());
            item.setSimilarityScore(null);
            item.setRerankScore(null);
            item.setChunkType(chunk.getChunkType());
            items.add(item);
        }
        backfillDocumentNames(items);
        return items;
    }
}
