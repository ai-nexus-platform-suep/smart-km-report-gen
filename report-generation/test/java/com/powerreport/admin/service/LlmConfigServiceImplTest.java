package com.powerreport.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.powerreport.admin.dto.LlmConfigRequest;
import com.powerreport.admin.dto.LlmConfigResponse;
import com.powerreport.admin.service.serviceImpl.LlmConfigServiceImpl;
import com.powerreport.config.ReportAiProperties;
import org.junit.jupiter.api.Test;
 
class LlmConfigServiceImplTest {

    @Test
    void getConfigMasksSecretsAndUsesFallbacks() {
        ReportAiProperties properties = new ReportAiProperties();
        properties.setSectionStreamUrl("http://ai-service/sections");
        properties.setApiKey("abc123xyz");
        properties.setTimeoutSeconds(45);

        LlmConfigResponse response = new LlmConfigServiceImpl(properties).getConfig();

        assertThat(response.getApiUrl()).isEqualTo("http://ai-service/sections");
        assertThat(response.getApiKey()).isEqualTo("abc******xyz");
        assertThat(response.getModelName()).isEqualTo("deepseek-chat");
        assertThat(response.getTimeoutSeconds()).isEqualTo(45);
    }

    @Test
    void updateConfigKeepsExistingSecretWhenMaskedSecretIsSubmitted() {
        ReportAiProperties properties = new ReportAiProperties();
        properties.setApiKey("old-secret");
        LlmConfigServiceImpl service = new LlmConfigServiceImpl(properties);

        LlmConfigResponse response = service.updateConfig(request("http://llm", "******", "deepseek-reasoner", 30));

        assertThat(properties.getApiUrl()).isEqualTo("http://llm");
        assertThat(properties.getApiKey()).isEqualTo("old-secret");
        assertThat(properties.getModelName()).isEqualTo("deepseek-reasoner");
        assertThat(properties.getTimeoutSeconds()).isEqualTo(30);
        assertThat(response.getApiKey()).isEqualTo("old******ret");
    }

    @Test
    void updateConfigOverwritesSecretWhenPlainSecretIsSubmitted() {
        ReportAiProperties properties = new ReportAiProperties();
        properties.setApiKey("old-secret");
        LlmConfigServiceImpl service = new LlmConfigServiceImpl(properties);

        LlmConfigResponse response = service.updateConfig(request("http://llm", "new-secret-value", "deepseek-chat", 60));

        assertThat(properties.getApiKey()).isEqualTo("new-secret-value");
        assertThat(response.getApiKey()).isEqualTo("new******lue");
    }

    private LlmConfigRequest request(String apiUrl, String apiKey, String modelName, Integer timeoutSeconds) {
        LlmConfigRequest request = new LlmConfigRequest();
        request.setApiUrl(apiUrl);
        request.setApiKey(apiKey);
        request.setModelName(modelName);
        request.setTimeoutSeconds(timeoutSeconds);
        return request;
    }
}
