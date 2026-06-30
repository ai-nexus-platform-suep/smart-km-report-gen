package com.powerreport.content.service.serviceImpl;

import com.powerreport.config.ReportAiProperties;
import com.powerreport.content.dto.LlmConfigRequest;
import com.powerreport.content.dto.LlmConfigResponse;
import com.powerreport.content.service.LlmConfigService;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class LlmConfigServiceImpl implements LlmConfigService {

    private static final String MASKED_SECRET = "******";
    private static final String DEFAULT_MODEL_NAME = "deepseek-chat";

    private final ReportAiProperties aiProperties;

    @Override
    public synchronized LlmConfigResponse getConfig() {
        return toResponse();
    }

    @Override
    public synchronized LlmConfigResponse updateConfig(LlmConfigRequest request) {
        aiProperties.setApiUrl(request.getApiUrl());
        aiProperties.setModelName(request.getModelName());
        aiProperties.setTimeoutSeconds(request.getTimeoutSeconds());
        if (StringUtils.hasText(request.getApiKey()) && !Objects.equals(request.getApiKey(), MASKED_SECRET)) {
            aiProperties.setApiKey(request.getApiKey());
        }
        return toResponse();
    }

    private LlmConfigResponse toResponse() {
        LlmConfigResponse response = new LlmConfigResponse();
        response.setApiUrl(firstNonBlank(aiProperties.getApiUrl(), aiProperties.getSectionStreamUrl()));
        response.setApiKey(maskSecret(aiProperties.getApiKey()));
        response.setModelName(firstNonBlank(aiProperties.getModelName(), DEFAULT_MODEL_NAME));
        response.setTimeoutSeconds(aiProperties.getTimeoutSeconds());
        return response;
    }

    private String firstNonBlank(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred : fallback;
    }

    private String maskSecret(String secret) {
        if (!StringUtils.hasText(secret)) {
            return "";
        }
        if (secret.length() <= 6) {
            return MASKED_SECRET;
        }
        return secret.substring(0, 3) + MASKED_SECRET + secret.substring(secret.length() - 3);
    }
}
