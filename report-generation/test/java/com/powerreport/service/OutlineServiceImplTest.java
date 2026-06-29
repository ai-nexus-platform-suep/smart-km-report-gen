package com.powerreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.powerreport.config.AiIntegrationProperties;
import com.powerreport.dto.OutlineConfirmRequest;
import com.powerreport.dto.OutlineGenerateRequest;
import com.powerreport.dto.OutlineGenerateResponse;
import com.powerreport.enums.ReportType;
import com.powerreport.mapper.ReportMapper;
import com.powerreport.mapper.ReportOutlineNodeMapper;
import com.powerreport.service.serviceImpl.OutlineServiceImpl;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class OutlineServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ReportMapper reportMapper;

    @Mock
    private ReportOutlineNodeMapper outlineNodeMapper;

    private AiIntegrationProperties aiProperties;
    private OutlineServiceImpl outlineService;

    @BeforeEach
    void setUp() {
        aiProperties = new AiIntegrationProperties();
        outlineService = new OutlineServiceImpl(
                aiProperties,
                new ObjectMapper().registerModule(new JavaTimeModule()),
                redisTemplate,
                reportMapper,
                outlineNodeMapper,
                new RestTemplateBuilder()
        );
    }

    @Test
    void buildSummerPeakOutlineReturnsNormalizedTemplate() {
        var outline = outlineService.buildOutline(ReportType.SUMMER_PEAK_CHECK);

        assertThat(outline).hasSize(6);
        assertThat(outline.get(0).getNumber()).isEqualTo("1");
        assertThat(outline.get(0).getTitle()).isEqualTo("检查概况");
        assertThat(outline.get(0).getLevel()).isEqualTo(1);
        assertThat(outline.get(0).getChildren()).extracting("number").containsExactly("1.1", "1.2", "1.3");
    }

    @Test
    void buildCoalInventoryOutlineReturnsAuditTemplate() {
        var outline = outlineService.buildOutline(ReportType.COAL_INVENTORY_AUDIT);

        assertThat(outline).hasSize(6);
        assertThat(outline.get(0).getTitle()).isEqualTo("审计概况");
        assertThat(outline.get(1).getTitle()).isEqualTo("煤炭库存管理情况");
    }

    @Test
    void generateOutlineFallsBackToLocalTemplateWhenAiUrlIsMissing() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        OutlineGenerateRequest request = sampleGenerateRequest();

        OutlineGenerateResponse response = outlineService.generateOutline(request);

        assertThat(response.getTempId()).isNotBlank();
        assertThat(response.getSource()).isEqualTo("LOCAL_TEMPLATE");
        assertThat(response.getExpireSeconds()).isEqualTo(1800L);
        assertThat(response.getOutline()).hasSize(6);
        verify(valueOperations).set(
                anyString(),
                anyString(),
                eq(Duration.ofSeconds(1800))
        );
    }

    @Test
    void generateOutlineThrowsWhenFallbackIsDisabledAndAiUrlIsMissing() {
        aiProperties.setFallbackEnabled(false);
        OutlineGenerateRequest request = sampleGenerateRequest();

        assertThatThrownBy(() -> outlineService.generateOutline(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI outline URL is not configured");
    }

    @Test
    void confirmOutlineRejectsEmptyOutlineWithoutTempId() {
        OutlineConfirmRequest request = new OutlineConfirmRequest();
        request.setReportType(ReportType.SUMMER_PEAK_CHECK);
        request.setSubject("2026 年迎峰度夏专项检查");
        request.setSpecialty("电气");
        request.setPowerPlant("示例电厂");
        request.setReportYear(2026);

        assertThatThrownBy(() -> outlineService.confirmOutline(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outline 为空时必须传 tempId");
    }

    private OutlineGenerateRequest sampleGenerateRequest() {
        OutlineGenerateRequest request = new OutlineGenerateRequest();
        request.setReportType(ReportType.SUMMER_PEAK_CHECK);
        request.setSubject("2026 年迎峰度夏专项检查");
        request.setName("2026 年迎峰度夏专项检查报告");
        request.setSpecialty("电气");
        request.setPowerPlant("示例电厂");
        request.setReportYear(2026);
        return request;
    }
}
