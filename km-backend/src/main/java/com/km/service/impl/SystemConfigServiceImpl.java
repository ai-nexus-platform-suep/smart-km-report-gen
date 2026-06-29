package com.km.service.impl;

import com.km.common.exception.BusinessException;
import com.km.common.exception.ErrorCode;
import com.km.entity.SystemConfig;
import com.km.repository.SystemConfigMapper;
import com.km.dto.response.SystemConfigVO;
import com.km.service.SystemConfigService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SystemConfigServiceImpl implements SystemConfigService {

    private final SystemConfigMapper systemConfigMapper;
    private final ObjectMapper objectMapper;

    private static final List<String> SENSITIVE_KEYS = Arrays.asList(
            "api_key", "apiKey", "apikey", "secret", "password"
    );

    @Override
    public List<SystemConfigVO> findAll() {
        return systemConfigMapper.findAll().stream()
                .map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public SystemConfigVO findByKey(String configKey) {
        SystemConfig config = systemConfigMapper.findByKey(configKey);
        if (config == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "配置项 " + configKey + " 不存在");
        }
        return toVO(config);
    }

    @Override
    @SuppressWarnings("unchecked")
    public SystemConfigVO update(String configKey, Map<String, Object> value) {
        try {
            String jsonValue = objectMapper.writeValueAsString(value);
            SystemConfig config = new SystemConfig();
            config.setConfigKey(configKey);
            config.setConfigValue(jsonValue);
            systemConfigMapper.upsert(config);
            return findByKey(configKey);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize config value", e);
        }
    }

    private SystemConfigVO toVO(SystemConfig config) {
        SystemConfigVO vo = new SystemConfigVO();
        vo.setConfigKey(config.getConfigKey());
        vo.setUpdatedAt(config.getUpdatedAt());
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> valueMap = objectMapper.readValue(config.getConfigValue(), Map.class);
            Map<String, Object> masked = valueMap.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> isSensitiveKey(e.getKey()) ? maskValue((String) e.getValue()) : e.getValue()
                    ));
            vo.setConfigValue(masked);
        } catch (JsonProcessingException e) {
            vo.setConfigValue(null);
        }
        return vo;
    }

    private boolean isSensitiveKey(String key) {
        return SENSITIVE_KEYS.stream().anyMatch(k -> key.toLowerCase().contains(k));
    }

    private String maskValue(String value) {
        if (value == null || value.length() < 8) { return "****"; }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }
}
