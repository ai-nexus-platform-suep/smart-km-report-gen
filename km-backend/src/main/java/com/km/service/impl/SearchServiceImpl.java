package com.km.service.impl;

import com.km.client.AiServiceClient;
import com.km.common.dto.ApiResponse;
import com.km.dto.request.SearchRequest;
import com.km.dto.response.SearchResultItemVO;
import com.km.dto.response.SearchResultVO;
import com.km.service.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SearchServiceImpl implements SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchServiceImpl.class);

    private final AiServiceClient aiClient;

    public SearchServiceImpl(AiServiceClient aiClient) {
        this.aiClient = aiClient;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public SearchResultVO search(SearchRequest request) {
        try {
            List<String> kbIds = request.getKnowledgeBaseIds();
            if (kbIds == null) kbIds = Collections.emptyList();

            @SuppressWarnings("unchecked")
            ApiResponse searchResp = aiClient.vectorSearch(
                    request.getQuery(), kbIds,
                    request.getTopK() * 2,
                    request.getSimilarityThreshold()
            );

            Object dataObj = searchResp.getData();
            List<Map<String, Object>> hits = new ArrayList<>();
            if (dataObj instanceof List) {
                hits = (List<Map<String, Object>>) dataObj;
            } else if (dataObj instanceof Map) {
                Object h = ((Map<String, Object>) dataObj).get("hits");
                if (h instanceof List) {
                    hits = (List<Map<String, Object>>) h;
                }
            }
            if (hits == null || hits.isEmpty()) {
                return emptyResult();
            }

            boolean shouldRerank = "vector_rerank".equals(request.getSearchMode());
            Map<Integer, Float> rerankScores = new HashMap<>();

            if (shouldRerank && hits.size() > 1) {
                List<String> passages = hits.stream()
                        .map(h -> (String) h.get("content"))
                        .collect(Collectors.toList());

                ApiResponse rerankResp = aiClient.rerank(request.getQuery(), passages, request.getTopK());
                Object rerankObj = rerankResp.getData();
                List<Map<String, Object>> rerankItems = new ArrayList<>();
                if (rerankObj instanceof List) {
                    rerankItems = (List<Map<String, Object>>) rerankObj;
                } else if (rerankObj instanceof Map) {
                    Object items = ((Map<String, Object>) rerankObj).get("items");
                    if (items instanceof List) {
                        rerankItems = (List<Map<String, Object>>) items;
                    }
                }
                if (rerankItems != null) {
                    for (Map<String, Object> item : rerankItems) {
                        int idx = ((Number) item.get("index")).intValue();
                        float score = ((Number) item.get("score")).floatValue();
                        rerankScores.put(idx, score);
                    }
                }
            }

            List<SearchResultItemVO> results = new ArrayList<>();
            for (int i = 0; i < hits.size(); i++) {
                Map<String, Object> hit = hits.get(i);
                SearchResultItemVO item = new SearchResultItemVO();
                item.setChunkId((String) hit.getOrDefault("chunk_id", ""));
                item.setDocumentId((String) hit.getOrDefault("doc_id", ""));
                item.setContent((String) hit.getOrDefault("content", ""));
                item.setChapterPath((String) hit.getOrDefault("chapter_path", ""));
                item.setSimilarityScore(((Number) hit.getOrDefault("similarity_score", 0.0)).floatValue());

                if (rerankScores.containsKey(i)) {
                    item.setRerankScore(rerankScores.get(i));
                }

                if (shouldRerank && item.getRerankScore() != null
                        && item.getRerankScore() < request.getRerankThreshold()) {
                    continue;
                }
                results.add(item);
            }

            if (shouldRerank) {
                results.sort((a, b) -> {
                    float sa = a.getRerankScore() != null ? a.getRerankScore() : a.getSimilarityScore();
                    float sb = b.getRerankScore() != null ? b.getRerankScore() : b.getSimilarityScore();
                    return Float.compare(sb, sa);
                });
            } else {
                results.sort((a, b) -> Float.compare(b.getSimilarityScore(), a.getSimilarityScore()));
            }

            if (results.size() > request.getTopK()) {
                results = results.subList(0, request.getTopK());
            }

            SearchResultVO vo = new SearchResultVO();
            vo.setResults(results);
            vo.setTotal(results.size());
            return vo;

        } catch (Exception e) {
            log.error("Search failed", e);
            return emptyResult();
        }
    }

    private SearchResultVO emptyResult() {
        SearchResultVO vo = new SearchResultVO();
        vo.setResults(Collections.emptyList());
        vo.setTotal(0);
        return vo;
    }
}
