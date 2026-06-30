package com.km.service.impl;

import com.km.client.ModelConfigTestClient;
import com.km.common.constant.ConfigKeys;
import com.km.common.exception.BusinessException;
import com.km.common.exception.ErrorCode;
import com.km.common.util.JsonUtils;
import com.km.dto.request.EmbeddingConfigRequest;
import com.km.dto.request.ParserConfigRequest;
import com.km.dto.request.RerankConfigRequest;
import com.km.entity.SystemConfig;
import com.km.repository.SystemConfigMapper;
import com.km.service.ConfigService;
import com.km.util.ConfigMaskUtil;
import com.km.vo.ConfigTestResultVO;
import com.km.vo.EmbeddingConfigVO;
import com.km.vo.ParserConfigVO;
import com.km.vo.RerankConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ConfigServiceImpl implements ConfigService {

    private final SystemConfigMapper systemConfigMapper;
    private final ModelConfigTestClient modelConfigTestClient;

    @Override
    public EmbeddingConfigVO getEmbeddingConfig() {
        return toEmbeddingVO(loadConfig(ConfigKeys.EMBEDDING), true);
    }

    @Override
    public EmbeddingConfigVO getEmbeddingConfigInternal() {
        return toEmbeddingVO(loadConfig(ConfigKeys.EMBEDDING), false);
    }

    @Override
    public EmbeddingConfigVO updateEmbeddingConfig(EmbeddingConfigRequest request) {
        Map<String, Object> current = loadConfigMap(ConfigKeys.EMBEDDING);
        if (StringUtils.hasText(request.getModelName())) {
            current.put("modelName", request.getModelName());
        }
        if (StringUtils.hasText(request.getApiUrl())) {
            current.put("apiUrl", request.getApiUrl());
        }
        if (StringUtils.hasText(request.getApiKey()) && !ConfigMaskUtil.isMaskedKey(request.getApiKey())) {
            current.put("apiKey", request.getApiKey());
        }
        if (request.getDimension() != null) {
            current.put("dimension", request.getDimension());
        }
        saveConfig(ConfigKeys.EMBEDDING, current);
        return toEmbeddingVO(loadConfig(ConfigKeys.EMBEDDING), true);
    }

    @Override
    public RerankConfigVO getRerankConfig() {
        return toRerankVO(loadConfig(ConfigKeys.RERANK), true);
    }

    @Override
    public RerankConfigVO getRerankConfigInternal() {
        return toRerankVO(loadConfig(ConfigKeys.RERANK), false);
    }

    @Override
    public RerankConfigVO updateRerankConfig(RerankConfigRequest request) {
        Map<String, Object> current = loadConfigMap(ConfigKeys.RERANK);
        if (StringUtils.hasText(request.getModelName())) {
            current.put("modelName", request.getModelName());
        }
        if (StringUtils.hasText(request.getApiUrl())) {
            current.put("apiUrl", request.getApiUrl());
        }
        if (StringUtils.hasText(request.getApiKey()) && !ConfigMaskUtil.isMaskedKey(request.getApiKey())) {
            current.put("apiKey", request.getApiKey());
        }
        if (request.getTopN() != null) {
            current.put("topN", request.getTopN());
        }
        saveConfig(ConfigKeys.RERANK, current);
        return toRerankVO(loadConfig(ConfigKeys.RERANK), true);
    }

    @Override
    public ParserConfigVO getParserConfig() {
        return toParserVO(loadConfig(ConfigKeys.PARSER));
    }

    @Override
    public ParserConfigVO updateParserConfig(ParserConfigRequest request) {
        Map<String, Object> current = loadConfigMap(ConfigKeys.PARSER);
        if (StringUtils.hasText(request.getBackend())) {
            current.put("backend", request.getBackend());
        }
        if (request.getMaxConcurrency() != null) {
            current.put("maxConcurrency", request.getMaxConcurrency());
        }
        saveConfig(ConfigKeys.PARSER, current);
        return toParserVO(loadConfig(ConfigKeys.PARSER));
    }

    @Override
    public ConfigTestResultVO testEmbeddingConfig() {
        Map<String, Object> config = loadConfigMap(ConfigKeys.EMBEDDING);
        return runConnectivityTest(
                (String) config.get("apiUrl"),
                (String) config.getOrDefault("apiKey", ""),
                (String) config.get("modelName"),
                true);
    }

    @Override
    public ConfigTestResultVO testRerankConfig() {
        Map<String, Object> config = loadConfigMap(ConfigKeys.RERANK);
        return runConnectivityTest(
                (String) config.get("apiUrl"),
                (String) config.getOrDefault("apiKey", ""),
                (String) config.get("modelName"),
                false);
    }

    private ConfigTestResultVO runConnectivityTest(String apiUrl, String apiKey, String modelName, boolean embedding) {
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException(ErrorCode.KM_CFG_002, "API Key 未配置，请先保存有效密钥");
        }
        if (!StringUtils.hasText(modelName)) {
            throw new BusinessException(ErrorCode.KM_CFG_002, "模型名称未配置");
        }
        long start = System.currentTimeMillis();
        try {
            if (embedding) {
                modelConfigTestClient.testEmbedding(apiUrl, apiKey, modelName);
            } else {
                modelConfigTestClient.testRerank(apiUrl, apiKey, modelName);
            }
            ConfigTestResultVO result = new ConfigTestResultVO();
            result.setSuccess(true);
            result.setMessage("连通性测试成功");
            result.setLatencyMs(System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.KM_CFG_002, "连通性测试失败: " + e.getMessage());
        }
    }

    private SystemConfig loadConfig(String key) {
        SystemConfig config = systemConfigMapper.getByKey(key);
        if (config == null) {
            throw new BusinessException(ErrorCode.KM_CFG_001);
        }
        return config;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadConfigMap(String key) {
        SystemConfig config = loadConfig(key);
        return JsonUtils.fromJson(config.getConfigValue(), Map.class);
    }

    private void saveConfig(String key, Map<String, Object> value) {
        SystemConfig config = new SystemConfig();
        config.setConfigKey(key);
        config.setConfigValue(JsonUtils.toJson(value));
        systemConfigMapper.upsert(config);
    }

    @SuppressWarnings("unchecked")
    private EmbeddingConfigVO toEmbeddingVO(SystemConfig config, boolean maskKey) {
        Map<String, Object> map = JsonUtils.fromJson(config.getConfigValue(), Map.class);
        EmbeddingConfigVO vo = new EmbeddingConfigVO();
        vo.setModelName((String) map.get("modelName"));
        vo.setApiUrl((String) map.get("apiUrl"));
        String apiKey = (String) map.getOrDefault("apiKey", "");
        vo.setApiKey(maskKey ? ConfigMaskUtil.maskApiKey(apiKey) : apiKey);
        Object dimension = map.get("dimension");
        vo.setDimension(dimension instanceof Number ? ((Number) dimension).intValue() : null);
        vo.setUpdatedAt(config.getUpdatedAt());
        return vo;
    }

    @SuppressWarnings("unchecked")
    private RerankConfigVO toRerankVO(SystemConfig config, boolean maskKey) {
        Map<String, Object> map = JsonUtils.fromJson(config.getConfigValue(), Map.class);
        RerankConfigVO vo = new RerankConfigVO();
        vo.setModelName((String) map.get("modelName"));
        vo.setApiUrl((String) map.get("apiUrl"));
        String apiKey = (String) map.getOrDefault("apiKey", "");
        vo.setApiKey(maskKey ? ConfigMaskUtil.maskApiKey(apiKey) : apiKey);
        Object topN = map.get("topN");
        vo.setTopN(topN instanceof Number ? ((Number) topN).intValue() : null);
        vo.setUpdatedAt(config.getUpdatedAt());
        return vo;
    }

    @SuppressWarnings("unchecked")
    private ParserConfigVO toParserVO(SystemConfig config) {
        Map<String, Object> map = JsonUtils.fromJson(config.getConfigValue(), Map.class);
        ParserConfigVO vo = new ParserConfigVO();
        vo.setBackend((String) map.get("backend"));
        Object maxConcurrency = map.get("maxConcurrency");
        vo.setMaxConcurrency(maxConcurrency instanceof Number ? ((Number) maxConcurrency).intValue() : null);
        vo.setUpdatedAt(config.getUpdatedAt());
        return vo;
    }
}
