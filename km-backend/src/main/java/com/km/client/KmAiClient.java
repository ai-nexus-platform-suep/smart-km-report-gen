package com.km.client;

import com.km.dto.ai.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class KmAiClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public KmAiClient(RestTemplate restTemplate,
                      @Value("${km.ai-service.base-url:http://localhost:8092}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public EmbedResponse embed(EmbedRequest request) {
        AiApiResponse<EmbedResponse> body = post("/internal/embed", request);
        return body != null ? body.getData() : null;
    }

    public VectorSearchResponse vectorSearch(VectorSearchRequest request) {
        try {
            String url = baseUrl + "/internal/search";
            org.springframework.http.ResponseEntity<String> rawResp = restTemplate.exchange(
                    url, org.springframework.http.HttpMethod.POST,
                    new org.springframework.http.HttpEntity<>(request), String.class);
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(rawResp.getBody());
            if (root.get("code").asInt() != 0) {
                log.warn("AI search error: {}", root.get("message").asText());
                return null;
            }
            return mapper.treeToValue(root.get("data"), VectorSearchResponse.class);
        } catch (Exception e) {
            log.warn("AI vector search failed: {}", e.getMessage());
            return null;
        }
    }

    public RerankResponse rerank(RerankRequest request) {
        try {
            String url = baseUrl + "/internal/rerank";
            org.springframework.http.ResponseEntity<String> rawResp = restTemplate.exchange(
                    url, org.springframework.http.HttpMethod.POST,
                    new org.springframework.http.HttpEntity<>(request), String.class);
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(rawResp.getBody());
            if (root.get("code").asInt() != 0) {
                log.warn("AI rerank error: {}", root.get("message").asText());
                return null;
            }
            return mapper.treeToValue(root.get("data"), RerankResponse.class);
        } catch (Exception e) {
            log.warn("AI rerank failed: {}", e.getMessage());
            return null;
        }
    }

    public boolean healthCheck() {
        try {
            String url = baseUrl + "/internal/health";
            ResponseEntity<AiApiResponse<Void>> resp = restTemplate.exchange(
                    url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<AiApiResponse<Void>>() {});
            AiApiResponse<Void> body = resp.getBody();
            return body != null && body.getCode() == 0;
        } catch (Exception e) {
            log.warn("AI health check failed: {}", e.getMessage());
            return false;
        }
    }

    private <T> AiApiResponse<T> post(String path, Object request) {
        String url = baseUrl + path;
        try {
            ResponseEntity<String> rawResp = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(request), String.class);
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(rawResp.getBody());
            int code = root.get("code").asInt();
            String message = root.get("message").asText();
            if (code != 0) {
                log.warn("AI service error: code={}, msg={}, url={}", code, message, url);
                return null;
            }
            com.fasterxml.jackson.databind.JavaType apiType = mapper.getTypeFactory()
                .constructParametricType(AiApiResponse.class, Object.class);
            AiApiResponse<?> raw = mapper.readValue(rawResp.getBody(), apiType);
            Object rawData = raw.getData();
            com.fasterxml.jackson.databind.JsonNode dataNode = root.get("data");
            T data = mapper.treeToValue(dataNode, mapper.getTypeFactory().constructType(Object.class));
            // Use reflection to get the proper type - simpler: just let Jackson figure it out
            String jsonStr = rawResp.getBody();
            // Parse: get data field as generic JsonNode, then convert to target type
            @SuppressWarnings("unchecked")
            AiApiResponse<T> typed = (AiApiResponse<T>)raw;
            return typed;
        } catch (Exception e) {
            log.warn("AI call failed: {}, url={}", e.getMessage(), url);
            throw e;
        }
    }  @SuppressWarnings("unchecked")
    
}
