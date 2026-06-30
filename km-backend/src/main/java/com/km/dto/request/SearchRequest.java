package com.km.dto.request;

import lombok.Data;

import javax.validation.constraints.*;
import java.util.List;
import java.util.Map;

/**
 * 检索请求 DTO
 * 对应 EPIC-05 搜索功能
 */
@Data
public class SearchRequest {

    @NotBlank(message = "检索关键词不能为空")
    private String query;

    private List<String> knowledgeBaseIds;

    @Min(value = 1, message = "topK 最小值为 1")
    @Max(value = 100, message = "topK 最大值为 100")
    private Integer topK = 10;

    @Pattern(regexp = "^(vector|vector_rerank|bm25)$", message = "searchMode 只能是 vector、vector_rerank 或 bm25")
    private String searchMode = "vector_rerank";

    @DecimalMin(value = "0.0", message = "similarityThreshold 最小值为 0.0")
    @DecimalMax(value = "1.0", message = "similarityThreshold 最大值为 1.0")
    private Float similarityThreshold = 0.6f;

    @DecimalMin(value = "0.0", message = "rerankThreshold 最小值为 0.0")
    @DecimalMax(value = "1.0", message = "rerankThreshold 最大值为 1.0")
    private Float rerankThreshold = 0.5f;

    private Map<String, String> tagFilters;
}