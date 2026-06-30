package com.powerreport.content.dto;

import lombok.Data;

@Data
public class LlmConfigResponse {

    private String apiUrl;
    private String apiKey;
    private String modelName;
    private Integer timeoutSeconds;
}
