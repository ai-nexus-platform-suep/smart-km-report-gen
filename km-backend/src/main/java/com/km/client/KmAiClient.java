package com.km.client;

import com.km.dto.ai.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;

/**
 * AI 服务客户端
 * 调用 km-ai-service 的向量检索、重排等服务
 * 
 * 对应 EPIC-05:
 * - 05.5 检索降级策略统一
 * - 05.7 超时与重试配置
 */
@Slf4j
@Component
public class KmAiClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final int maxRetryAttempts;

    public KmAiClient(RestTemplate restTemplate,
                      @Value("${km.ai-service.base-url:http://localhost:8092}") String baseUrl,
                      @Value("${km.ai-service.retry.max-attempts:2}") int maxRetryAttempts) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.maxRetryAttempts = maxRetryAttempts;
    }

    /**
     * 向量嵌入
     */
    public EmbedResponse embed(EmbedRequest request) {
        AiApiResponse<EmbedResponse> body = postWithRetry("/internal/embed", request);
        return body != null ? body.getData() : null;
    }

    /**
     * 向量检索
     * 支持重试，网络超时时自动重试
     */
    public VectorSearchResponse vectorSearch(VectorSearchRequest request) {
        AiApiResponse<VectorSearchResponse> body = postWithRetry("/internal/search", request);
        return body != null ? body.getData() : null;
    }

    /**
     * 重排
     */
    public RerankResponse rerank(RerankRequest request) {
        AiApiResponse<RerankResponse> body = postWithRetry("/internal/rerank", request);
        return body != null ? body.getData() : null;
    }

    /**
     * 健康检查
     */
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

    /**
     * 带重试的 POST 请求
     * 网络超时（SocketTimeoutException、ResourceAccessException）时自动重试
     * 最多重试 maxRetryAttempts 次
     * 
     * 对应 EPIC-05 05.7: 超时与重试配置
     */
    @SuppressWarnings("unchecked")
    private <T> AiApiResponse<T> postWithRetry(String path, Object request) {
        String url = baseUrl + path;
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
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
                
            } catch (ResourceAccessException e) {
                // 网络异常（可能包含超时），触发重试
                lastException = e;
                log.warn("[EPIC-05] AI call attempt {} failed (will retry if available): {}, url={}",
                        attempt, e.getMessage(), url);
                
                if (attempt < maxRetryAttempts) {
                    try {
                        Thread.sleep(100L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted", ie);
                    }
                }
            } catch (Exception e) {
                // 检查是否是超时异常
                if (isTimeoutException(e)) {
                    lastException = e;
                    log.warn("[EPIC-05] AI call attempt {} timeout (will retry if available): {}, url={}",
                            attempt, e.getMessage(), url);
                    
                    if (attempt < maxRetryAttempts) {
                        try {
                            Thread.sleep(100L * attempt);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Retry interrupted", ie);
                        }
                    }
                } else {
                    // 其他异常（业务错误），不重试，直接抛出
                    log.warn("AI call failed (no retry): {}, url={}", e.getMessage(), url);
                    throw new RuntimeException(e);
                }
            }
        }
        
        // 所有重试都失败
        log.error("[EPIC-05] AI call failed after {} attempts: {}, url={}",
                maxRetryAttempts, lastException != null ? lastException.getMessage() : "unknown", url);
        if (lastException != null) {
            throw new RuntimeException("AI service unavailable after " + maxRetryAttempts + " retries", lastException);
        }
        return null;
    }

    /**
     * 判断异常是否为超时异常
     */
    private boolean isTimeoutException(Exception e) {
        String msg = e.getMessage();
        if (msg != null && (msg.contains("timeout") || msg.contains("Timeout"))) {
            return true;
        }
        // 递归检查嵌套异常
        if (e.getCause() != null && e.getCause() != e) {
            return isTimeoutException((Exception) e.getCause());
        }
        return false;
    }
}