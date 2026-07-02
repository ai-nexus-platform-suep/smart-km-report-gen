package com.km.dto.ai;

import lombok.Data;

import java.util.List;

@Data
public class HybridSearchResponse {
    private List<HybridSearchHit> hits;

    @Data
    public static class HybridSearchHit {
        private String chunkId;
        private String documentId;
        private String content;
        private String chapterPath;
        private String chunkType;
        private Float similarityScore;
        private Float bm25Score;
        private Float hybridScore;
    }
}
