package com.km.controller.config;

import com.km.dto.request.EmbeddingConfigRequest;
import com.km.dto.request.ParserConfigRequest;
import com.km.dto.request.RerankConfigRequest;
import com.km.service.ConfigService;
import com.km.vo.EmbeddingConfigVO;
import com.km.vo.ParserConfigVO;
import com.km.vo.RerankConfigVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ConfigControllerTest {

    @Mock
    private ConfigService configService;

    @InjectMocks
    private ConfigController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldGetEmbeddingConfig() throws Exception {
        EmbeddingConfigVO config = new EmbeddingConfigVO();
        config.setModelName("text-embedding-v2");
        config.setApiUrl("https://api.example.com/embed");
        config.setApiKey("sk-********");
        config.setDimension(1024);

        when(configService.getEmbeddingConfig()).thenReturn(config);

        mockMvc.perform(get("/api/admin/config/embedding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.modelName").value("text-embedding-v2"))
                .andExpect(jsonPath("$.data.dimension").value(1024));

        verify(configService).getEmbeddingConfig();
    }

    @Test
    void shouldUpdateEmbeddingConfig() throws Exception {
        EmbeddingConfigRequest request = new EmbeddingConfigRequest();
        request.setModelName("text-embedding-v2");
        request.setApiUrl("https://api.example.com/embed");
        request.setApiKey("sk-new-key");
        request.setDimension(1024);

        EmbeddingConfigVO config = new EmbeddingConfigVO();
        config.setModelName("text-embedding-v2");
        config.setApiUrl("https://api.example.com/embed");
        config.setApiKey("sk-********");
        config.setDimension(1024);

        when(configService.updateEmbeddingConfig(any(EmbeddingConfigRequest.class))).thenReturn(config);

        mockMvc.perform(put("/api/admin/config/embedding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.modelName").value("text-embedding-v2"));

        verify(configService).updateEmbeddingConfig(any(EmbeddingConfigRequest.class));
    }

    @Test
    void shouldGetRerankConfig() throws Exception {
        RerankConfigVO config = new RerankConfigVO();
        config.setModelName("bge-reranker-v2");
        config.setApiUrl("https://api.example.com/rerank");
        config.setApiKey("sk-********");
        config.setTopN(5);

        when(configService.getRerankConfig()).thenReturn(config);

        mockMvc.perform(get("/api/admin/config/rerank"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.modelName").value("bge-reranker-v2"))
                .andExpect(jsonPath("$.data.topN").value(5));

        verify(configService).getRerankConfig();
    }

    @Test
    void shouldUpdateRerankConfig() throws Exception {
        RerankConfigRequest request = new RerankConfigRequest();
        request.setModelName("bge-reranker-v2");
        request.setApiUrl("https://api.example.com/rerank");
        request.setApiKey("sk-new-key");
        request.setTopN(5);

        RerankConfigVO config = new RerankConfigVO();
        config.setModelName("bge-reranker-v2");
        config.setApiUrl("https://api.example.com/rerank");
        config.setApiKey("sk-********");
        config.setTopN(5);

        when(configService.updateRerankConfig(any(RerankConfigRequest.class))).thenReturn(config);

        mockMvc.perform(put("/api/admin/config/rerank")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.modelName").value("bge-reranker-v2"));

        verify(configService).updateRerankConfig(any(RerankConfigRequest.class));
    }

    @Test
    void shouldGetParserConfig() throws Exception {
        ParserConfigVO config = new ParserConfigVO();
        config.setBackend("tika");
        config.setMaxConcurrency(4);

        when(configService.getParserConfig()).thenReturn(config);

        mockMvc.perform(get("/api/admin/config/parser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.backend").value("tika"))
                .andExpect(jsonPath("$.data.maxConcurrency").value(4));

        verify(configService).getParserConfig();
    }

    @Test
    void shouldUpdateParserConfig() throws Exception {
        ParserConfigRequest request = new ParserConfigRequest();
        request.setBackend("tika");
        request.setMaxConcurrency(4);

        ParserConfigVO config = new ParserConfigVO();
        config.setBackend("tika");
        config.setMaxConcurrency(4);

        when(configService.updateParserConfig(any(ParserConfigRequest.class))).thenReturn(config);

        mockMvc.perform(put("/api/admin/config/parser")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.backend").value("tika"));

        verify(configService).updateParserConfig(any(ParserConfigRequest.class));
    }
}
