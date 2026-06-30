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

        List<String> kbIds = resolveAccessibleKbIds(request.getKnowledgeBaseIds(), userId);
        List<SearchResultItemVO> items;

        if ("bm25".equals(searchMode)) {
            log.info("[EPIC-05] Using BM25 fallback mode for query: {}", query);
            items = performBm25Search(query, topK, kbIds, request.getTagFilters());
        } else {
            items = performVectorSearchWithFallback(query, kbIds, topK,
                    request.getSimilarityThreshold(), searchMode, rerankThreshold, request.getTagFilters());
        }

        backfillDocumentNames(items);

        SearchResultVO result = new SearchResultVO();
        result.setResults(items);
        result.setTotal(items.size());
        return result;
    }

    private List<String> resolveAccessibleKbIds(List<String> requestedKbIds, Long userId) {
        if (requestedKbIds != null && !requestedKbIds.isEmpty()) {
            return requestedKbIds;
        }
        return null;
    }

    private List<SearchResultItemVO> performVectorSearchWithFallback(
            String query, List<String> kbIds, int topK, Float similarityThreshold,
            String searchMode, float rerankThreshold, Map<String, String> tagFilters) {

        List<VectorSearchResponse.VectorSearchHit> hits;
        try {
            hits = performVectorSearch(query, kbIds, topK, similarityThreshold, tagFilters);
        } catch (Exception e) {
            log.warn("[EPIC-05] AI vector search failed (fallback to BM25): {}, query={}", e.getMessage(), query);
            return performBm25Search(query, topK, kbIds, tagFilters);
        }

        if (hits == null || hits.isEmpty()) {
            log.info("[EPIC-05] No vector search results, query={}", query);
            return Collections.emptyList();
        }

        if ("vector_rerank".equals(searchMode)) {
            return performRerank(query, hits, topK, rerankThreshold);
        }
        return hitsToItems(hits, null);
    }

    private List<VectorSearchResponse.VectorSearchHit> performVectorSearch(
            String query, List<String> kbIds, int topK, Float threshold,
            Map<String, String> tagFilters) {
        VectorSearchRequest req = new VectorSearchRequest();
        req.setQuery(query);
        req.setKnowledgeBaseIds(kbIds);
        req.setTopK(topK);
        req.setSimilarityThreshold(threshold);
        req.setTagFilters(tagFilters);

        log.debug("[EPIC-05] Calling vector search: kbIds={}, topK={}, tagFilters={}", kbIds, topK, tagFilters);

        VectorSearchResponse resp = kmAiClient.vectorSearch(req);
        return resp != null ? resp.getHits() : Collections.emptyList();
    }

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
                log.warn("[EPIC-05] Rerank returned null, using raw hits");
                return hitsToItems(hits, null);
            }

            Map<Integer, Float> scoreMap = rerankResp.getItems().stream()
                    .collect(Collectors.toMap(
                            RerankResponse.RerankItem::getIndex,
                            RerankResponse.RerankItem::getScore));

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

            log.debug("[EPIC-05] Rerank completed: original={}, afterRerank={}", hits.size(), items.size());
            return items;
        } catch (Exception e) {
            log.warn("[EPIC-05] Rerank failed (falling back to raw hits): {}", e.getMessage());
            return hitsToItems(hits, null);
        }
    }

    private List<SearchResultItemVO> performBm25Search(
            String query, int topK, List<String> kbIds, Map<String, String> tagFilters) {
        log.info("[EPIC-05] BM25 search: query={}, kbIds={}, tagFilters={}", query, kbIds, tagFilters);

        List<String> readyDocIds = getReadyDocumentIds(kbIds, tagFilters);
        if (readyDocIds == null || readyDocIds.isEmpty()) {
            log.info("[EPIC-05] BM25 search: no ready documents found, returning empty");
            return Collections.emptyList();
        }

        List<Chunk> chunks = chunkMapper.searchByKeywordWithDocIds(query, topK * 2, readyDocIds);
        if (chunks == null || chunks.isEmpty()) {
            return Collections.emptyList();
        }

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

        if (items.size() > topK) {
            items = items.subList(0, topK);
        }

        log.info("[EPIC-05] BM25 search completed: query={}, chunksFound={}, returned={}",
                query, chunks.size(), items.size());
        return items;
    }

    private List<String> getReadyDocumentIds(List<String> kbIds, Map<String, String> tagFilters) {
        if (kbIds == null || kbIds.isEmpty()) {
            return documentMapper.listAllReadyDocIds();
        }
        return documentMapper.listReadyDocIdsByKbIds(kbIds);
    }

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
        item.setDocumentName(null);
        item.setChapterPath(hit.getChapterPath());
        item.setContent(hit.getContent());
        item.setSimilarityScore(hit.getSimilarityScore());
        item.setRerankScore(null);
        item.setChunkType("paragraph");
        return item;
    }

    private void backfillDocumentNames(List<SearchResultItemVO> items) {
        if (items == null || items.isEmpty()) return;

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
}