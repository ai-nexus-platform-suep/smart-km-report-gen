package com.km.dto.request;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class SearchRequest {

    private String query;
    private List<String> knowledgeBaseIds;
    private Integer topK = 10;
    private String searchMode = "vector_rerank";
    private Float similarityThreshold = 0.6f;
    private Float rerankThreshold = 0.5f;
    private Map<String, String> tagFilters;
}
