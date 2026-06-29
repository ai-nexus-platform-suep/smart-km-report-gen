package com.km.client;

import com.km.common.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * HTTP client for calling the Python km-ai-service internal APIs.
 */
@Component
public class AiServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AiServiceClient.class);

    private final RestTemplate restTemplate;

    @Value("${km.ai-service.base-url:http://localhost:8092}")
    private String baseUrl;

    public AiServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public ApiResponse<List<Map<String, Object>>> embedTexts(List<String> texts) {
        Map<String, Object> body = new java.util.HashMap<String, Object>() {{ put("texts", texts); }};
        return restTemplate.postForObject(
                baseUrl + "/internal/embed",
                body,
                ApiResponse.class
        );
    }

    public ApiResponse<List<Map<String, Object>>> rerank(String query, List<String> passages, int topK) {
        Map<String, Object> body = new java.util.HashMap<String, Object>() {{ put("query", query); put("passages", passages); put("top_k", topK); }};
        return restTemplate.postForObject(
                baseUrl + "/internal/rerank",
                body,
                ApiResponse.class
        );
    }

    public ApiResponse<List<Map<String, Object>>> vectorSearch(String query, List<String> kbIds, int topK, float threshold) {
        Map<String, Object> body = new java.util.HashMap<String, Object>() {{ put("query", query); put("knowledge_base_ids", kbIds); put("top_k", topK); put("similarity_threshold", threshold); }};
        return restTemplate.postForObject(
                baseUrl + "/internal/search",
                body,
                ApiResponse.class
        );
    }

    public ApiResponse<Map<String, Object>> health() {
        return restTemplate.getForObject(baseUrl + "/internal/health", ApiResponse.class);
    }
}
