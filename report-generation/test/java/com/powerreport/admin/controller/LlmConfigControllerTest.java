package com.powerreport.admin.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
 
import com.powerreport.admin.dto.LlmConfigRequest;
import com.powerreport.admin.dto.LlmConfigResponse;
import com.powerreport.admin.service.LlmConfigService;
import com.powerreport.common.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class LlmConfigControllerTest {

    @Mock
    private LlmConfigService llmConfigService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new LlmConfigController(llmConfigService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getConfigReturnsMaskedConfig() throws Exception {
        when(llmConfigService.getConfig()).thenReturn(response());

        mockMvc.perform(get("/api/admin/config/llm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.apiUrl").value("https://llm.example.test/v1"))
                .andExpect(jsonPath("$.data.apiKey").value("abc******xyz"));
    }

    @Test
    void updateConfigValidatesRequiredFields() throws Exception {
        mockMvc.perform(put("/api/admin/config/llm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "apiUrl": "",
                                  "modelName": "",
                                  "timeoutSeconds": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("apiUrl")));
    }

    @Test
    void updateConfigDelegatesToService() throws Exception {
        when(llmConfigService.updateConfig(any(LlmConfigRequest.class))).thenReturn(response());

        mockMvc.perform(put("/api/admin/config/llm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "apiUrl": "https://llm.example.test/v1",
                                  "apiKey": "abc123xyz",
                                  "modelName": "deepseek-chat",
                                  "timeoutSeconds": 30
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("LLM config updated"))
                .andExpect(jsonPath("$.data.modelName").value("deepseek-chat"));
    }

    private LlmConfigResponse response() {
        LlmConfigResponse response = new LlmConfigResponse();
        response.setApiUrl("https://llm.example.test/v1");
        response.setApiKey("abc******xyz");
        response.setModelName("deepseek-chat");
        response.setTimeoutSeconds(30);
        return response;
    }
}
