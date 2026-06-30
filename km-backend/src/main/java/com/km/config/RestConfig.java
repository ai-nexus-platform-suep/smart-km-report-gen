package com.km.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate 配置，用于调用 km-ai-service 的 AI 能力 API。
 * 
 * 对应 EPIC-05 05.7 超时与重试配置
 */
@Configuration
public class RestConfig {

    /**
     * 配置 RestTemplate，包含超时设置
     * - 连接超时：5秒
     * - 读取超时：10秒（P95 < 2s 目标）
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);   // 5秒连接超时
        factory.setReadTimeout(10000);      // 10秒读取超时
        return new RestTemplate(factory);
    }
}