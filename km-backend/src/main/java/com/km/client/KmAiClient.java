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
        AiApiResponse<VectorSearchResponse> body = post("/internal/search", request);
        return body != null ? body.getData() : null;
    }

    public RerankResponse rerank(RerankRequest request) {
        AiApiResponse<RerankResponse> body = post("/internal/rerank", request);
        return body != null ? body.getData() : null;
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

    @SuppressWarnings("unchecked")
    private <T> AiApiResponse<T> post(String path, Object request) {
        String url = baseUrl + path;
        try {
            ResponseEntity<AiApiResponse<T>> resp = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(request),
                    new ParameterizedTypeReference<AiApiResponse<T>>() {});
            AiApiResponse<T> body = resp.getBody();
            if (body == null) {
                log.warn("AI service returned null body: {}", url);
                return null;
            }
            if (body.getCode() != 0) {
                log.warn("AI service error: code={}, msg={}, url={}",
                        body.getCode(), body.getMessage(), url);
                return null;
            }
            return body;
        } catch (Exception e) {
            log.warn("AI call failed: {}, url={}", e.getMessage(), url);
            throw e;
        }
    }
}
