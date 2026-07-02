package com.km.controller.internal;

import com.km.common.dto.ApiResponse;
import com.km.service.ConfigService;
import com.km.vo.EmbeddingConfigVO;
import com.km.vo.ParserConfigVO;
import com.km.vo.RerankConfigVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * InternalConfigController 单元测试。
 * 覆盖：embedding、rerank、parser 内部配置接口。
 */
@ExtendWith(MockitoExtension.class)
class InternalConfigControllerTest {

    @Mock
    private ConfigService configService;

    @InjectMocks
    private InternalConfigController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // ====== GET /internal/config/embedding ======

    @Test
    void shouldReturnEmbeddingConfig() throws Exception {
        EmbeddingConfigVO config = new EmbeddingConfigVO();
        config.setModelName("text-embedding-v3");
        config.setApiUrl("https://api.example.com/v1");
        config.setApiKey("sk-embedding-key");
        config.setDimension(1024);
        config.setUpdatedAt(LocalDateTime.now());

        when(configService.getEmbeddingConfigInternal()).thenReturn(config);

        mockMvc.perform(get("/internal/config/embedding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.modelName").value("text-embedding-v3"))
                .andExpect(jsonPath("$.data.apiUrl").value("https://api.example.com/v1"))
                .andExpect(jsonPath("$.data.apiKey").value("sk-embedding-key"))
                .andExpect(jsonPath("$.data.dimension").value(1024));

        verify(configService).getEmbeddingConfigInternal();
    }

    @Test
    void shouldReturnEmbeddingConfigWithSensitiveKey() throws Exception {
        // 内部接口应返回完整 API Key（不脱敏）
        EmbeddingConfigVO config = new EmbeddingConfigVO();
        config.setModelName("custom-embed");
        config.setApiUrl("http://localhost:8080/embeddings");
        config.setApiKey("sk-very-secret-full-key-value");
        config.setDimension(768);

        when(configService.getEmbeddingConfigInternal()).thenReturn(config);

        mockMvc.perform(get("/internal/config/embedding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.apiKey").value("sk-very-secret-full-key-value"));
    }

    // ====== GET /internal/config/rerank ======

    @Test
    void shouldReturnRerankConfig() throws Exception {
        RerankConfigVO config = new RerankConfigVO();
        config.setModelName("bge-reranker-v2");
        config.setApiUrl("https://api.example.com/v1");
        config.setApiKey("sk-rerank-key");
        config.setTopN(5);
        config.setUpdatedAt(LocalDateTime.now());

        when(configService.getRerankConfigInternal()).thenReturn(config);

        mockMvc.perform(get("/internal/config/rerank"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.modelName").value("bge-reranker-v2"))
                .andExpect(jsonPath("$.data.apiKey").value("sk-rerank-key"))
                .andExpect(jsonPath("$.data.topN").value(5));

        verify(configService).getRerankConfigInternal();
    }

    // ====== GET /internal/config/parser ======

    @Test
    void shouldReturnParserConfig() throws Exception {
        ParserConfigVO config = new ParserConfigVO();
        config.setBackend("mineru");
        config.setMaxConcurrency(3);
        config.setUpdatedAt(LocalDateTime.now());

        when(configService.getParserConfig()).thenReturn(config);

        mockMvc.perform(get("/internal/config/parser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.backend").value("mineru"))
                .andExpect(jsonPath("$.data.maxConcurrency").value(3));

        verify(configService).getParserConfig();
    }
}
