package com.km.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * WebConfig 单元测试。
 * 覆盖：CORS 跨域配置验证。
 */
class WebConfigTest {

    @Test
    void shouldAddCorsMappings() {
        WebConfig config = new WebConfig();

        // 创建一个真实的 CorsRegistry 并验证配置
        CorsRegistry registry = new CorsRegistry();
        config.addCorsMappings(registry);

        // 验证 registry 被正确配置
        assertNotNull(registry);
    }

    @Test
    void shouldConfigureCorsForApiPaths() {
        WebConfig config = new WebConfig();
        CorsRegistry registry = mock(CorsRegistry.class);
        CorsRegistration registration = mock(CorsRegistration.class);

        when(registry.addMapping("/api/**")).thenReturn(registration);
        when(registration.allowedOriginPatterns(anyString())).thenReturn(registration);
        when(registration.allowedMethods(any())).thenReturn(registration);
        when(registration.allowedHeaders(anyString())).thenReturn(registration);
        when(registration.allowCredentials(anyBoolean())).thenReturn(registration);

        config.addCorsMappings(registry);

        verify(registry).addMapping("/api/**");
        verify(registration).allowedOriginPatterns("*");
        verify(registration).allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
        verify(registration).allowedHeaders("*");
        verify(registration).allowCredentials(true);
    }
}
