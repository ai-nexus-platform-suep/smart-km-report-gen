package com.km.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ModelConfigTestClient 单元测试。
 * 覆盖：embedding 测试、rerank 测试、URL 规范化、参数校验。
 */
@ExtendWith(MockitoExtension.class)
class ModelConfigTestClientTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ResponseEntity<String> responseEntity;

    private ModelConfigTestClient client;

    @BeforeEach
    void setUp() {
        client = new ModelConfigTestClient(restTemplate);
    }

    // ====== testEmbedding ======

    @Test
    void shouldCallEmbeddingApi() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(responseEntity);

        client.testEmbedding("https://api.example.com/v1", "sk-test-key", "text-embedding-model");

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<HttpEntity<Map>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);

        verify(restTemplate).postForEntity(urlCaptor.capture(), entityCaptor.capture(), eq(String.class));

        assertEquals("https://api.example.com/v1/embeddings", urlCaptor.getValue());

        HttpEntity<Map> entity = entityCaptor.getValue();
        assertNotNull(entity.getBody());
        assertEquals("text-embedding-model", entity.getBody().get("model"));
        assertEquals("float", entity.getBody().get("encoding_format"));
        assertNotNull(entity.getHeaders().get("Authorization"));
    }

    @Test
    void shouldIncludeBearerAuthInEmbeddingRequest() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(responseEntity);

        client.testEmbedding("https://api.example.com", "my-secret-key", "model-v1");

        ArgumentCaptor<HttpEntity<Map>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(anyString(), entityCaptor.capture(), eq(String.class));

        String authHeader = entityCaptor.getValue().getHeaders().getFirst("Authorization");
        assertEquals("Bearer my-secret-key", authHeader);
    }

    // ====== testRerank ======

    @Test
    void shouldCallRerankApi() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(responseEntity);

        client.testRerank("https://api.example.com/v1", "sk-rerank-key", "rerank-model");

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<HttpEntity<Map>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);

        verify(restTemplate).postForEntity(urlCaptor.capture(), entityCaptor.capture(), eq(String.class));

        assertEquals("https://api.example.com/v1/rerank", urlCaptor.getValue());

        HttpEntity<Map> entity = entityCaptor.getValue();
        assertEquals("rerank-model", entity.getBody().get("model"));
        assertEquals("配置连通性测试", entity.getBody().get("query"));
        assertNotNull(entity.getBody().get("documents"));
    }

    // ====== normalizeBaseUrl ======

    @Test
    void shouldStripTrailingSlash() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(responseEntity);

        client.testEmbedding("https://api.example.com/v1/", "key", "model");

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).postForEntity(urlCaptor.capture(), any(HttpEntity.class), eq(String.class));

        assertEquals("https://api.example.com/v1/embeddings", urlCaptor.getValue());
    }

    @Test
    void shouldThrowExceptionWhenUrlIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> client.testEmbedding(null, "key", "model"));
    }

    @Test
    void shouldThrowExceptionWhenUrlIsEmpty() {
        assertThrows(IllegalArgumentException.class,
                () -> client.testEmbedding("", "key", "model"));
    }

    @Test
    void shouldThrowExceptionWhenUrlIsBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> client.testEmbedding("   ", "key", "model"));
    }

    @Test
    void shouldHandleUrlWithoutTrailingSlash() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(responseEntity);

        client.testRerank("http://localhost:8080/api", "key", "model");

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).postForEntity(urlCaptor.capture(), any(HttpEntity.class), eq(String.class));

        assertEquals("http://localhost:8080/api/rerank", urlCaptor.getValue());
    }
}
