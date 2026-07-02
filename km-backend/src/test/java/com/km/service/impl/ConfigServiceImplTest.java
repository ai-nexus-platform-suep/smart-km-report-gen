package com.km.service.impl;

import com.km.client.ModelConfigTestClient;
import com.km.common.constant.ConfigKeys;
import com.km.common.exception.BusinessException;
import com.km.common.exception.ErrorCode;
import com.km.dto.request.EmbeddingConfigRequest;
import com.km.dto.request.ParserConfigRequest;
import com.km.dto.request.RerankConfigRequest;
import com.km.entity.SystemConfig;
import com.km.repository.SystemConfigMapper;
import com.km.vo.EmbeddingConfigVO;
import com.km.vo.ParserConfigVO;
import com.km.vo.RerankConfigVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ConfigServiceImpl 单元测试。
 * 覆盖：配置查询、配置更新、掩码处理、配置不存在异常。
 */
@ExtendWith(MockitoExtension.class)
class ConfigServiceImplTest {

    @Mock
    private SystemConfigMapper systemConfigMapper;

    @Mock
    private ModelConfigTestClient modelConfigTestClient;

    private ConfigServiceImpl configService;

    private static final String EMBEDDING_JSON =
            "{\"modelName\":\"text-embedding-ada-002\",\"apiUrl\":\"https://api.openai.com/v1\",\"apiKey\":\"sk-proj-abc123456789\",\"dimension\":1536}";

    private static final String RERANK_JSON =
            "{\"modelName\":\"bge-reranker-v2-m3\",\"apiUrl\":\"https://api.siliconflow.cn/v1\",\"apiKey\":\"sk-proj-def987654321\",\"topN\":5}";

    private static final String PARSER_JSON =
            "{\"backend\":\"mineru\",\"maxConcurrency\":4}";

    @BeforeEach
    void setUp() {
        configService = new ConfigServiceImpl(systemConfigMapper, modelConfigTestClient);
    }

    // ====== Embedding 查询 ======

    @Test
    void shouldReturnEmbeddingConfigWhenExists() {
        SystemConfig config = createEmbeddingConfig();
        when(systemConfigMapper.getByKey(ConfigKeys.EMBEDDING)).thenReturn(config);

        EmbeddingConfigVO vo = configService.getEmbeddingConfig();

        assertNotNull(vo);
        assertEquals("text-embedding-ada-002", vo.getModelName());
        assertEquals("https://api.openai.com/v1", vo.getApiUrl());
        assertNotNull(vo.getApiKey());
        assertTrue(vo.getApiKey().contains("****"), "API Key should be masked");
        assertEquals(Integer.valueOf(1536), vo.getDimension());
        assertEquals(config.getUpdatedAt(), vo.getUpdatedAt());
    }

    @Test
    void shouldThrowExceptionWhenEmbeddingConfigNotFound() {
        when(systemConfigMapper.getByKey(ConfigKeys.EMBEDDING)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> configService.getEmbeddingConfig());
        assertEquals(ErrorCode.KM_CFG_001.getCode(), ex.getCode());
    }

    // ====== Rerank 查询 ======

    @Test
    void shouldReturnRerankConfigWhenExists() {
        SystemConfig config = createRerankConfig();
        when(systemConfigMapper.getByKey(ConfigKeys.RERANK)).thenReturn(config);

        RerankConfigVO vo = configService.getRerankConfig();

        assertNotNull(vo);
        assertEquals("bge-reranker-v2-m3", vo.getModelName());
        assertEquals("https://api.siliconflow.cn/v1", vo.getApiUrl());
        assertNotNull(vo.getApiKey());
        assertTrue(vo.getApiKey().contains("****"), "API Key should be masked");
        assertEquals(Integer.valueOf(5), vo.getTopN());
    }

    @Test
    void shouldThrowExceptionWhenRerankConfigNotFound() {
        when(systemConfigMapper.getByKey(ConfigKeys.RERANK)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> configService.getRerankConfig());
        assertEquals(ErrorCode.KM_CFG_001.getCode(), ex.getCode());
    }

    // ====== Parser 查询 ======

    @Test
    void shouldReturnParserConfigWhenExists() {
        SystemConfig config = createParserConfig();
        when(systemConfigMapper.getByKey(ConfigKeys.PARSER)).thenReturn(config);

        ParserConfigVO vo = configService.getParserConfig();

        assertNotNull(vo);
        assertEquals("mineru", vo.getBackend());
        assertEquals(Integer.valueOf(4), vo.getMaxConcurrency());
    }

    @Test
    void shouldThrowExceptionWhenParserConfigNotFound() {
        when(systemConfigMapper.getByKey(ConfigKeys.PARSER)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> configService.getParserConfig());
        assertEquals(ErrorCode.KM_CFG_001.getCode(), ex.getCode());
    }

    // ====== Embedding 更新 ======

    @Test
    void shouldUpdateEmbeddingConfigPartially() {
        SystemConfig original = createEmbeddingConfig();
        SystemConfig updated = createEmbeddingConfig();
        // simulate upsert persisted: only modelName changed
        updated.setConfigValue(
                "{\"modelName\":\"gpt-4\",\"apiUrl\":\"https://api.openai.com/v1\",\"apiKey\":\"sk-proj-abc123456789\",\"dimension\":1536}");
        when(systemConfigMapper.getByKey(ConfigKeys.EMBEDDING)).thenReturn(original, updated);

        EmbeddingConfigRequest request = new EmbeddingConfigRequest();
        request.setModelName("gpt-4");

        configService.updateEmbeddingConfig(request);

        ArgumentCaptor<SystemConfig> captor = ArgumentCaptor.forClass(SystemConfig.class);
        verify(systemConfigMapper).upsert(captor.capture());
        SystemConfig saved = captor.getValue();
        assertNotNull(saved.getConfigValue());
        // check only modelName was changed; other fields preserved
        assertTrue(saved.getConfigValue().contains("\"modelName\":\"gpt-4\""));
        assertTrue(saved.getConfigValue().contains("\"apiUrl\":\"https://api.openai.com/v1\""));
        assertTrue(saved.getConfigValue().contains("\"apiKey\":\"sk-proj-abc123456789\""));
        assertTrue(saved.getConfigValue().contains("\"dimension\":1536"));
        verify(systemConfigMapper, times(2)).getByKey(ConfigKeys.EMBEDDING);
    }

    @Test
    void shouldSkipApiKeyUpdateWhenMasked() {
        SystemConfig original = createEmbeddingConfig();
        SystemConfig updated = createEmbeddingConfig();
        // same as original — apiKey should be preserved
        updated.setConfigValue(EMBEDDING_JSON);
        when(systemConfigMapper.getByKey(ConfigKeys.EMBEDDING)).thenReturn(original, updated);

        EmbeddingConfigRequest request = new EmbeddingConfigRequest();
        request.setApiKey("sk-****abcd"); // masked value, should be skipped

        EmbeddingConfigVO vo = configService.updateEmbeddingConfig(request);

        ArgumentCaptor<SystemConfig> captor = ArgumentCaptor.forClass(SystemConfig.class);
        verify(systemConfigMapper).upsert(captor.capture());
        SystemConfig saved = captor.getValue();
        // original apiKey preserved, not replaced with masked value
        assertTrue(saved.getConfigValue().contains("\"apiKey\":\"sk-proj-abc123456789\""));
        assertFalse(saved.getConfigValue().contains("sk-****abcd"));
    }

    @Test
    void shouldUpdateApiKeyWhenNotMasked() {
        SystemConfig original = createEmbeddingConfig();
        SystemConfig updated = createEmbeddingConfig();
        updated.setConfigValue(
                "{\"modelName\":\"text-embedding-ada-002\",\"apiUrl\":\"https://api.openai.com/v1\",\"apiKey\":\"sk-new-clear-key\",\"dimension\":1536}");
        when(systemConfigMapper.getByKey(ConfigKeys.EMBEDDING)).thenReturn(original, updated);

        EmbeddingConfigRequest request = new EmbeddingConfigRequest();
        request.setApiKey("sk-new-clear-key");

        configService.updateEmbeddingConfig(request);

        ArgumentCaptor<SystemConfig> captor = ArgumentCaptor.forClass(SystemConfig.class);
        verify(systemConfigMapper).upsert(captor.capture());
        SystemConfig saved = captor.getValue();
        assertTrue(saved.getConfigValue().contains("\"apiKey\":\"sk-new-clear-key\""));
    }

    // ====== Rerank 更新 ======

    @Test
    void shouldUpdateRerankConfig() {
        SystemConfig original = createRerankConfig();
        SystemConfig updated = createRerankConfig();
        updated.setConfigValue(
                "{\"modelName\":\"gpt-reranker\",\"apiUrl\":\"https://api.openai.com/v1\",\"apiKey\":\"sk-proj-def987654321\",\"topN\":10}");
        when(systemConfigMapper.getByKey(ConfigKeys.RERANK)).thenReturn(original, updated);

        RerankConfigRequest request = new RerankConfigRequest();
        request.setModelName("gpt-reranker");
        request.setApiUrl("https://api.openai.com/v1");
        request.setTopN(10);

        RerankConfigVO vo = configService.updateRerankConfig(request);

        ArgumentCaptor<SystemConfig> captor = ArgumentCaptor.forClass(SystemConfig.class);
        verify(systemConfigMapper).upsert(captor.capture());
        SystemConfig saved = captor.getValue();
        assertTrue(saved.getConfigValue().contains("\"modelName\":\"gpt-reranker\""));
        assertTrue(saved.getConfigValue().contains("\"apiUrl\":\"https://api.openai.com/v1\""));
        assertTrue(saved.getConfigValue().contains("\"topN\":10"));
    }

    // ====== Parser 更新 ======

    @Test
    void shouldUpdateParserConfig() {
        SystemConfig original = createParserConfig();
        SystemConfig updated = createParserConfig();
        updated.setConfigValue("{\"backend\":\"docling\",\"maxConcurrency\":8}");
        when(systemConfigMapper.getByKey(ConfigKeys.PARSER)).thenReturn(original, updated);

        ParserConfigRequest request = new ParserConfigRequest();
        request.setBackend("docling");
        request.setMaxConcurrency(8);

        ParserConfigVO vo = configService.updateParserConfig(request);

        ArgumentCaptor<SystemConfig> captor = ArgumentCaptor.forClass(SystemConfig.class);
        verify(systemConfigMapper).upsert(captor.capture());
        SystemConfig saved = captor.getValue();
        assertTrue(saved.getConfigValue().contains("\"backend\":\"docling\""));
        assertTrue(saved.getConfigValue().contains("\"maxConcurrency\":8"));
    }

    // ====== API Key 掩码 ======

    @Test
    void shouldMaskApiKeyInGetResponse() {
        SystemConfig config = createEmbeddingConfig();
        when(systemConfigMapper.getByKey(ConfigKeys.EMBEDDING)).thenReturn(config);

        EmbeddingConfigVO vo = configService.getEmbeddingConfig();
        // apiKey starts with "sk-" prefix
        assertTrue(vo.getApiKey().startsWith("sk-"), "Should preserve key prefix");
        // contains mask token
        assertTrue(vo.getApiKey().contains("****"), "Should mask middle of key");
        // ends with last 4 characters of original key
        assertTrue(vo.getApiKey().endsWith("6789"), "Should preserve key suffix");
        // masked key should not equal original
        assertNotEquals("sk-proj-abc123456789", vo.getApiKey());
    }

    // ====== 辅助方法 ======

    private SystemConfig createEmbeddingConfig() {
        return createSystemConfig(ConfigKeys.EMBEDDING, EMBEDDING_JSON);
    }

    private SystemConfig createRerankConfig() {
        return createSystemConfig(ConfigKeys.RERANK, RERANK_JSON);
    }

    private SystemConfig createParserConfig() {
        return createSystemConfig(ConfigKeys.PARSER, PARSER_JSON);
    }

    private SystemConfig createSystemConfig(String key, String jsonValue) {
        SystemConfig config = new SystemConfig();
        config.setConfigKey(key);
        config.setConfigValue(jsonValue);
        config.setUpdatedAt(LocalDateTime.now());
        return config;
    }
}
