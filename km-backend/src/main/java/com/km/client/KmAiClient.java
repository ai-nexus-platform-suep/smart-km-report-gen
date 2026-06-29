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

/**
 * km-ai-service (Python FastAPI) 的 HTTP 客户端。
 * 负责调用嵌入、向量检索、重排序等 AI 能力。
 * 契约见 docs/ai-service-contract.yaml。
 */
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

    /**
     * 文本批量嵌入，调用 /internal/embed。
     */
    public EmbedResponse embed(EmbedRequest request) {
        String url = baseUrl + "/internal/embed";
        log.debug("Calling AI embed service: {}", url);
        ResponseEntity<AiApiResponse<EmbedResponse>> resp = restTemplate.exchange(
                url, HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<AiApiResponse<EmbedResponse>>() {});
        return resp.getBody().getData();
    }

    /**
     * 向量检索，调用 /internal/search。
     */
    public VectorSearchResponse vectorSearch(VectorSearchRequest request) {
        String url = baseUrl + "/internal/search";
        log.debug("Calling AI vector search service: {}", url);
        ResponseEntity<AiApiResponse<VectorSearchResponse>> resp = restTemplate.exchange(
                url, HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<AiApiResponse<VectorSearchResponse>>() {});
        return resp.getBody().getData();
    }

    /**
     * 重排序，调用 /internal/rerank。
     */
    public RerankResponse rerank(RerankRequest request) {
        String url = baseUrl + "/internal/rerank";
        log.debug("Calling AI rerank service: {}", url);
        ResponseEntity<AiApiResponse<RerankResponse>> resp = restTemplate.exchange(
                url, HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<AiApiResponse<RerankResponse>>() {});
        return resp.getBody().getData();
    }

    /**
     * 健康检查，调用 /internal/health。
     */
    public boolean healthCheck() {
        try {
            String url = baseUrl + "/internal/health";
            ResponseEntity<AiApiResponse<Void>> resp = restTemplate.exchange(
                    url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<AiApiResponse<Void>>() {});
            return resp.getBody().getCode() == 0;
        } catch (Exception e) {
            log.warn("AI service health check failed: {}", e.getMessage());
            return false;
        }
    }
}
