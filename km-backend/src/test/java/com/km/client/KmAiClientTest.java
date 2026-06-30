package com.km.client;

import com.km.dto.ai.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * KmAiClient 单元测试。
 * 覆盖：embed、vectorSearch、rerank、healthCheck 的正常与异常路径。
 */
@ExtendWith(MockitoExtension.class)
class KmAiClientTest {

    @Mock
    private RestTemplate restTemplate;

    private KmAiClient client;

    private static final String BASE_URL = "http://localhost:8092";

    @BeforeEach
    void setUp() {
        client = new KmAiClient(restTemplate, BASE_URL);
    }

    // ====== embed 测试 ======

    @Test
    void shouldEmbed() {
        EmbedResponse embedResponse = new EmbedResponse();
        embedResponse.setVectors(Collections.singletonList(Arrays.asList(0.1f, 0.2f, 0.3f)));
        embedResponse.setDimension(3);

        AiApiResponse<EmbedResponse> aiResponse = new AiApiResponse<>();
        aiResponse.setCode(0);
        aiResponse.setMessage("ok");
        aiResponse.setData(embedResponse);

        when(restTemplate.exchange(
                eq(BASE_URL + "/internal/embed"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                ArgumentMatchers.<ParameterizedTypeReference<AiApiResponse<EmbedResponse>>>any()))
                .thenReturn(ResponseEntity.ok(aiResponse));

        EmbedRequest request = new EmbedRequest(Collections.singletonList("测试文本"), "default");
        EmbedResponse result = client.embed(request);

        assertNotNull(result);
        assertEquals(3, result.getDimension());
        assertEquals(1, result.getVectors().size());
        assertEquals(3, result.getVectors().get(0).size());
    }

    // ====== vectorSearch 测试 ======

    @Test
    void shouldVectorSearch() {
        VectorSearchResponse.VectorSearchHit hit = new VectorSearchResponse.VectorSearchHit();
        hit.setChunkId("chunk-001");
        hit.setDocumentId("doc-001");
        hit.setContent("相关内容");
        hit.setChapterPath("第1章");
        hit.setSimilarityScore(0.95f);

        VectorSearchResponse searchResponse = new VectorSearchResponse();
        searchResponse.setHits(Collections.singletonList(hit));

        AiApiResponse<VectorSearchResponse> aiResponse = new AiApiResponse<>();
        aiResponse.setCode(0);
        aiResponse.setMessage("ok");
        aiResponse.setData(searchResponse);

        when(restTemplate.exchange(
                eq(BASE_URL + "/internal/search"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                ArgumentMatchers.<ParameterizedTypeReference<AiApiResponse<VectorSearchResponse>>>any()))
                .thenReturn(ResponseEntity.ok(aiResponse));

        VectorSearchRequest request = new VectorSearchRequest("keyword", Collections.singletonList("kb-1"), 10, 0.6f);
        VectorSearchResponse result = client.vectorSearch(request);

        assertNotNull(result);
        assertEquals(1, result.getHits().size());
        assertEquals("chunk-001", result.getHits().get(0).getChunkId());
        assertEquals(0.95f, result.getHits().get(0).getSimilarityScore(), 0.001f);
    }

    // ====== rerank 测试 ======

    @Test
    void shouldRerank() {
        RerankResponse.RerankItem item = new RerankResponse.RerankItem();
        item.setIndex(0);
        item.setScore(0.92f);

        RerankResponse rerankResponse = new RerankResponse();
        rerankResponse.setItems(Collections.singletonList(item));

        AiApiResponse<RerankResponse> aiResponse = new AiApiResponse<>();
        aiResponse.setCode(0);
        aiResponse.setMessage("ok");
        aiResponse.setData(rerankResponse);

        when(restTemplate.exchange(
                eq(BASE_URL + "/internal/rerank"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                ArgumentMatchers.<ParameterizedTypeReference<AiApiResponse<RerankResponse>>>any()))
                .thenReturn(ResponseEntity.ok(aiResponse));

        RerankRequest request = new RerankRequest("query", Arrays.asList("passage1", "passage2"), 5, "default");
        RerankResponse result = client.rerank(request);

        assertNotNull(result);
        assertEquals(1, result.getItems().size());
        assertEquals(0, result.getItems().get(0).getIndex());
        assertEquals(0.92f, result.getItems().get(0).getScore(), 0.001f);
    }

    // ====== 异常路径测试 ======

    @Test
    void shouldReturnNullWhenResponseCodeNotZero() {
        AiApiResponse<EmbedResponse> aiResponse = new AiApiResponse<>();
        aiResponse.setCode(500);
        aiResponse.setMessage("Internal Error");
        aiResponse.setData(null);

        when(restTemplate.exchange(
                eq(BASE_URL + "/internal/embed"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                ArgumentMatchers.<ParameterizedTypeReference<AiApiResponse<EmbedResponse>>>any()))
                .thenReturn(ResponseEntity.ok(aiResponse));

        EmbedRequest request = new EmbedRequest(Collections.singletonList("文本"), "default");
        EmbedResponse result = client.embed(request);

        assertNull(result);
    }

    @Test
    void shouldReturnNullWhenResponseBodyIsNull() {
        when(restTemplate.exchange(
                eq(BASE_URL + "/internal/embed"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                ArgumentMatchers.<ParameterizedTypeReference<AiApiResponse<EmbedResponse>>>any()))
                .thenReturn(ResponseEntity.ok(null));

        EmbedRequest request = new EmbedRequest(Collections.singletonList("文本"), "default");
        EmbedResponse result = client.embed(request);

        assertNull(result);
    }

    @Test
    void shouldThrowExceptionOnNetworkError() {
        when(restTemplate.exchange(
                eq(BASE_URL + "/internal/embed"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                ArgumentMatchers.<ParameterizedTypeReference<AiApiResponse<EmbedResponse>>>any()))
                .thenThrow(new RestClientException("Connection refused"));

        EmbedRequest request = new EmbedRequest(Collections.singletonList("文本"), "default");

        assertThrows(RestClientException.class, () -> client.embed(request));
    }

    // ====== healthCheck 测试 ======

    @Test
    void shouldHealthCheckReturnTrue() {
        AiApiResponse<Void> aiResponse = new AiApiResponse<>();
        aiResponse.setCode(0);
        aiResponse.setMessage("ok");

        when(restTemplate.exchange(
                eq(BASE_URL + "/internal/health"),
                eq(HttpMethod.GET),
                isNull(),
                ArgumentMatchers.<ParameterizedTypeReference<AiApiResponse<Void>>>any()))
                .thenReturn(ResponseEntity.ok(aiResponse));

        assertTrue(client.healthCheck());
    }

    @Test
    void shouldHealthCheckReturnFalseWhenCodeNotZero() {
        AiApiResponse<Void> aiResponse = new AiApiResponse<>();
        aiResponse.setCode(500);
        aiResponse.setMessage("Service Unavailable");

        when(restTemplate.exchange(
                eq(BASE_URL + "/internal/health"),
                eq(HttpMethod.GET),
                isNull(),
                ArgumentMatchers.<ParameterizedTypeReference<AiApiResponse<Void>>>any()))
                .thenReturn(ResponseEntity.ok(aiResponse));

        assertFalse(client.healthCheck());
    }

    @Test
    void shouldHealthCheckReturnFalseOnException() {
        when(restTemplate.exchange(
                eq(BASE_URL + "/internal/health"),
                eq(HttpMethod.GET),
                isNull(),
                ArgumentMatchers.<ParameterizedTypeReference<AiApiResponse<Void>>>any()))
                .thenThrow(new RestClientException("Connection refused"));

        assertFalse(client.healthCheck());
    }

    // ====== 构造函数测试 ======

    @Test
    void shouldUseDefaultBaseUrl() {
        // 验证使用自定义 baseUrl 构造的 client 正常工作
        KmAiClient customClient = new KmAiClient(restTemplate, "http://custom-host:9090");

        AiApiResponse<Void> aiResponse = new AiApiResponse<>();
        aiResponse.setCode(0);

        when(restTemplate.exchange(
                eq("http://custom-host:9090/internal/health"),
                eq(HttpMethod.GET),
                isNull(),
                ArgumentMatchers.<ParameterizedTypeReference<AiApiResponse<Void>>>any()))
                .thenReturn(ResponseEntity.ok(aiResponse));

        assertTrue(customClient.healthCheck());
    }
}
