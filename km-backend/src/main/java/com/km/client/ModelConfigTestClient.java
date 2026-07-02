package com.km.client;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 使用当前系统配置直连模型 API，验证嵌入/重排连通性。
 */
@Component
@RequiredArgsConstructor
public class ModelConfigTestClient {

    private final RestTemplate restTemplate;

    public void testEmbedding(String apiUrl, String apiKey, String modelName) {
        String url = normalizeBaseUrl(apiUrl) + "/embeddings";
        Map<String, Object> body = new HashMap<>();
        body.put("model", modelName);
        body.put("input", Arrays.asList("配置连通性测试"));
        body.put("encoding_format", "float");
        postJson(url, apiKey, body);
    }

    public void testRerank(String apiUrl, String apiKey, String modelName) {
        String url = normalizeBaseUrl(apiUrl) + "/rerank";
        Map<String, Object> body = new HashMap<>();
        body.put("model", modelName);
        body.put("query", "配置连通性测试");
        body.put("documents", Arrays.asList("测试文档A", "测试文档B"));
        body.put("top_n", 2);
        postJson(url, apiKey, body);
    }

    private void postJson(String url, String apiKey, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
    }

    private String normalizeBaseUrl(String apiUrl) {
        if (apiUrl == null || apiUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("API 地址未配置");
        }
        String base = apiUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }
}
