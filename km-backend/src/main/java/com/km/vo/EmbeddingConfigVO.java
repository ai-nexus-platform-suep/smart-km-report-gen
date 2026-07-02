package com.km.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EmbeddingConfigVO {

    private String modelName;
    private String apiUrl;
    private String apiKey;
    private Integer dimension;
    private LocalDateTime updatedAt;
}
