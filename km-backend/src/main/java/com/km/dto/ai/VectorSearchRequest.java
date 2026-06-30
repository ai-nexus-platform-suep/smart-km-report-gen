package com.km.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 向量检索请求
 * 对应 EPIC-05 / ai-service-contract.yaml
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VectorSearchRequest {
    private String query;
    private List<String> knowledgeBaseIds;
    private Integer topK;
    private Float similarityThreshold;
    
    /**
     * 标签过滤器
     * key: 标签键
     * value: 标签值
     * 对应 EPIC-05 05.1
     */
    private Map<String, String> tagFilters;
}