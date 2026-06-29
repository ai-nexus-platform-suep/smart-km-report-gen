package com.km.dto.ai;

import lombok.Data;

import java.util.List;

@Data
public class VectorSearchResponse {
    private List<VectorSearchHit> hits;

    @Data
    public static class VectorSearchHit {
        private String chunkId;
        private String documentId;
        private String content;
        private String chapterPath;
        private Float similarityScore;
    }
}
