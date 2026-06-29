package com.km.dto.request;

import lombok.Data;

@Data
public class RerankConfigRequest {

    private String modelName;
    private String apiUrl;
    private String apiKey;
    private Integer topN;
}
