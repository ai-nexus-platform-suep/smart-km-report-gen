package com.myenglish.qachat.controller;

import com.myenglish.qachat.dto.resp.ModelConfigInternalVO;
import com.myenglish.qachat.mapper.MessageMapper;
import com.myenglish.qachat.mapper.ModelConfigMapper;
import com.myenglish.qachat.service.ModelConfigService;
import com.myenglish.qacommon.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        properties = {
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration",
                "spring.main.allow-bean-definition-overriding=true"
        }
)
@AutoConfigureMockMvc
@DisplayName("InternalModelConfigController 内部模型配置接口")
class InternalModelConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MessageMapper messageMapper;

    @MockBean
    private ModelConfigMapper modelConfigMapper;

    @MockBean
    private ModelConfigService modelConfigService;

    @BeforeEach
    void setUp() {
        reset(modelConfigService);
    }

    // ======================== Query 参数传递 userId ========================

    @Nested
    @DisplayName("Query 参数传递 userId（Python 直连场景）")
    class QueryParamUserId {

        @Test
        @DisplayName("通过 query 参数 userId=1 正确传递到 service 层")
        void shouldPassUserIdFromQueryParam() throws Exception {
            when(modelConfigService.getDefaultDecrypted(1L, "chat"))
                    .thenReturn(ModelConfigInternalVO.builder()
                            .provider("openai")
                            .baseUrl("https://api.openai.com")
                            .modelName("gpt-4")
                            .apiKey("sk-test-key")
                            .timeoutSeconds(30)
                            .build());

            mockMvc.perform(get("/internal/model-configs/default")
                            .param("userId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.apiKey").value("sk-test-key"));
        }

        @Test
        @DisplayName("不同 userId 应传递到 service 层")
        void shouldPassDifferentUserId() throws Exception {
            when(modelConfigService.getDefaultDecrypted(99L, "chat"))
                    .thenReturn(ModelConfigInternalVO.builder()
                            .provider("test").build());

            mockMvc.perform(get("/internal/model-configs/default")
                            .param("userId", "99"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("query 参数 userId=999 时应传递 999 到 service")
        void shouldUseQueryParamUserId() throws Exception {
            when(modelConfigService.getDefaultDecrypted(999L, "chat"))
                    .thenReturn(ModelConfigInternalVO.builder()
                            .provider("openai").build());

            mockMvc.perform(get("/internal/model-configs/default")
                            .param("userId", "999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    // ======================== 缺少必需参数 ========================

    @Nested
    @DisplayName("缺少必需参数 userId")
    class MissingUserIdParam {

        @Test
        @DisplayName("缺少 userId 参数时 Spring 应返回 400（Bad Request）")
        void shouldReturn400WhenUserIdMissing() throws Exception {
            mockMvc.perform(get("/internal/model-configs/default"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ======================== 回归场景 ========================

    @Nested
    @DisplayName("回归测试")
    class Regression {

        @Test
        @DisplayName("不传 scenario 参数时应默认为 chat")
        void shouldDefaultScenarioToChat() throws Exception {
            when(modelConfigService.getDefaultDecrypted(anyLong(), eq("chat")))
                    .thenReturn(ModelConfigInternalVO.builder()
                            .provider("openai")
                            .modelName("gpt-3.5-turbo")
                            .build());

            mockMvc.perform(get("/internal/model-configs/default")
                            .param("userId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("传入 scenario=summary 时应传递正确值")
        void shouldPassSummaryScenario() throws Exception {
            when(modelConfigService.getDefaultDecrypted(1L, "summary"))
                    .thenReturn(ModelConfigInternalVO.builder()
                            .provider("openai")
                            .modelName("gpt-4")
                            .build());

            mockMvc.perform(get("/internal/model-configs/default")
                            .param("userId", "1")
                            .param("scenario", "summary"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    // ======================== 服务异常 ========================

    @Nested
    @DisplayName("服务层异常传播")
    class ServiceException {

        @Test
        @DisplayName("服务层抛出 BusinessException 时应正确传播")
        void shouldPropagateServiceException() throws Exception {
            when(modelConfigService.getDefaultDecrypted(anyLong(), any()))
                    .thenThrow(new BusinessException(500, "模型配置查询失败"));

            mockMvc.perform(get("/internal/model-configs/default")
                            .param("userId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("模型配置查询失败"));
        }
    }
}
