package com.km.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RerankConfigVO {

    private String modelName;
    private String apiUrl;
    private String apiKey;
    private Integer topN;
    private LocalDateTime updatedAt;
}
