package com.myenglish.qachat.controller;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.myenglish.qachat.dto.resp.ModelConfigInternalVO;
import com.myenglish.qachat.mapper.MessageMapper;
import com.myenglish.qachat.mapper.ModelConfigMapper;
import com.myenglish.qachat.service.ModelConfigService;
import com.myenglish.qacommon.context.UserContextInterceptor;
import com.myenglish.qacommon.context.UserContextHeaders;
import com.myenglish.qacommon.context.UserContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = InternalModelConfigController.class,
        excludeAutoConfiguration = {
                DataSourceAutoConfiguration.class,
                MybatisPlusAutoConfiguration.class
        })
@Import(InternalModelConfigControllerTest.TestWebConfig.class)
@DisplayName("InternalModelConfigController 内部模型配置接口")
class InternalModelConfigControllerTest {

    @TestConfiguration
    static class TestWebConfig implements WebMvcConfigurer {
        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(new UserContextInterceptor())
                    .addPathPatterns("/internal/**")
                    .order(1);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ModelConfigService modelConfigService;

    @MockBean
    private MessageMapper messageMapper;

    @MockBean
    private ModelConfigMapper modelConfigMapper;

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    // ==================================================================
    // 正常流程
    // ==================================================================

    @Test
    @DisplayName("Python 通过 user-id Header 传入 userId，拦截器解析后 Controller 正确获取")
    void shouldReadUserIdFromHeader() throws Exception {
        Long userId = 42L;
        String scenario = "chat";

        ModelConfigInternalVO expected = ModelConfigInternalVO.builder()
                .provider("deepseek")
                .baseUrl("https://api.deepseek.com")
                .modelName("deepseek-chat")
                .apiKey("sk-test-key")
                .timeoutSeconds(60)
                .build();

        when(modelConfigService.getDefaultDecrypted(eq(userId), eq(scenario)))
                .thenReturn(expected);

        mockMvc.perform(get("/internal/model-configs/default")
                        .param("scenario", scenario)
                        .header(UserContextHeaders.USER_ID, String.valueOf(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provider").value("deepseek"))
                .andExpect(jsonPath("$.data.baseUrl").value("https://api.deepseek.com"))
                .andExpect(jsonPath("$.data.modelName").value("deepseek-chat"))
                .andExpect(jsonPath("$.data.apiKey").value("sk-test-key"))
                .andExpect(jsonPath("$.data.timeoutSeconds").value(60));

        verify(modelConfigService).getDefaultDecrypted(eq(userId), eq(scenario));
    }

    @Test
    @DisplayName("scenario 参数未传时默认使用 chat")
    void shouldDefaultScenarioToChat() throws Exception {
        Long userId = 1L;

        when(modelConfigService.getDefaultDecrypted(eq(userId), eq("chat")))
                .thenReturn(ModelConfigInternalVO.builder()
                        .provider("openai").baseUrl("https://api.openai.com")
                        .modelName("gpt-4").apiKey("sk-key").timeoutSeconds(30)
                        .build());

        mockMvc.perform(get("/internal/model-configs/default")
                        .header(UserContextHeaders.USER_ID, String.valueOf(userId)))
                .andExpect(status().isOk());

        verify(modelConfigService).getDefaultDecrypted(eq(userId), eq("chat"));
    }

    @Test
    @DisplayName("scenario=summary 应正确传递")
    void shouldPassSummaryScenario() throws Exception {
        Long userId = 5L;
        String scenario = "summary";

        when(modelConfigService.getDefaultDecrypted(eq(userId), eq(scenario)))
                .thenReturn(ModelConfigInternalVO.builder()
                        .provider("deepseek").baseUrl("https://api.deepseek.com")
                        .modelName("deepseek-chat").apiKey("sk-key").timeoutSeconds(60)
                        .build());

        mockMvc.perform(get("/internal/model-configs/default")
                        .param("scenario", scenario)
                        .header(UserContextHeaders.USER_ID, String.valueOf(userId)))
                .andExpect(status().isOk());

        verify(modelConfigService).getDefaultDecrypted(eq(userId), eq(scenario));
    }

    // ==================================================================
    // 边界 & 异常
    // ==================================================================

    @Nested
    @DisplayName("user-id Header 异常场景")
    class MissingUserIdHeader {

        @Test
        @DisplayName("缺少 user-id Header 时 userId 为 null，Service 抛异常应返回 500")
        void shouldFailWhenUserIdHeaderMissing() throws Exception {
            when(modelConfigService.getDefaultDecrypted(null, "chat"))
                    .thenThrow(new RuntimeException("未找到默认模型配置"));

            mockMvc.perform(get("/internal/model-configs/default"))
                    .andExpect(status().is5xxServerError());

            verify(modelConfigService).getDefaultDecrypted(null, "chat");
        }
    }

    @Nested
    @DisplayName("Service 层异常")
    class ServiceException {

        @Test
        @DisplayName("Service 抛出异常时 Controller 应向上传播返回 500")
        void shouldPropagateServiceException() throws Exception {
            Long userId = 99L;
            when(modelConfigService.getDefaultDecrypted(eq(userId), eq("chat")))
                    .thenThrow(new RuntimeException("未找到默认模型配置"));

            mockMvc.perform(get("/internal/model-configs/default")
                            .header(UserContextHeaders.USER_ID, String.valueOf(userId)))
                    .andExpect(status().is5xxServerError());
        }
    }

    // ==================================================================
    // UserContextInterceptor 集成验证
    // ==================================================================

    @Nested
    @DisplayName("与 UserContextInterceptor 集成")
    class UserContextIntegration {

        @Test
        @DisplayName("拦截器将 user-id Header 写入 UserContextHolder，afterCompletion 清除")
        void shouldSetAndClearUserContext() throws Exception {
            Long userId = 100L;

            when(modelConfigService.getDefaultDecrypted(eq(userId), eq("chat")))
                    .thenReturn(ModelConfigInternalVO.builder()
                            .provider("deepseek").baseUrl("https://test.com")
                            .modelName("model").apiKey("key1").timeoutSeconds(30)
                            .build());

            mockMvc.perform(get("/internal/model-configs/default")
                            .header(UserContextHeaders.USER_ID, String.valueOf(userId)))
                    .andExpect(status().isOk());

            verify(modelConfigService).getDefaultDecrypted(eq(userId), eq("chat"));
        }
    }

    // ==================================================================
    // 回归：不再使用 query param 传 userId
    // ==================================================================

    @Nested
    @DisplayName("回归验证")
    class Regression {

        @Test
        @DisplayName("query param 中的 userId 不影响 Controller 行为（Controller 只从 UserContextHolder 读取）")
        void shouldIgnoreQueryParamUserId() throws Exception {
            Long headerUserId = 10L;

            when(modelConfigService.getDefaultDecrypted(eq(headerUserId), eq("chat")))
                    .thenReturn(ModelConfigInternalVO.builder()
                            .provider("provider").baseUrl("url").modelName("model")
                            .apiKey("key").timeoutSeconds(30).build());

            mockMvc.perform(get("/internal/model-configs/default")
                            .param("userId", "999")
                            .header(UserContextHeaders.USER_ID, String.valueOf(headerUserId)))
                    .andExpect(status().isOk());

            verify(modelConfigService).getDefaultDecrypted(eq(headerUserId), eq("chat"));
        }
    }
}
