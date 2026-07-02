package com.km.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VectorSearchRequest {
    private String query;
    private List<String> knowledgeBaseIds;
    private Integer topK;
    private Float similarityThreshold;
}
