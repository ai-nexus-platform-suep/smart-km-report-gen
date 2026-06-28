package com.powerreport.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.ai")
public class AiIntegrationProperties {

    /**
     * AI 全栈同学提供的大纲生成接口地址。
     */
    private String outlineUrl = "";

    /**
     * AI HTTP 调用超时时间，单位：秒。
     */
    private int timeoutSeconds = 60;

    /**
     * Redis 中大纲临时状态的过期时间，单位：秒。
     */
    private long outlineTempTtlSeconds = 1800;

    /**
     * AI 接口未配置或调用失败时，是否使用本地固定大纲方便联调。
     */
    private boolean fallbackEnabled = true;
}
