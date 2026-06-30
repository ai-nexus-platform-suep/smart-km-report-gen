package com.powerreport.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.ai")
public class ReportAiProperties {

    /**
     * AI service endpoint for outline generation.
     */
    private String outlineUrl = "";

    /**
     * AI service endpoint for section content streaming.
     */
    private String sectionStreamUrl = "";

    /**
     * LLM API base URL configured from admin page.
     */
    private String apiUrl = "";

    /**
     * LLM API key configured from admin page.
     */
    private String apiKey = "";

    /**
     * LLM model name configured from admin page.
     */
    private String modelName = "";

    /**
     * AI HTTP timeout, in seconds.
     */
    private int timeoutSeconds = 60;

    /**
     * Outline temporary state TTL in Redis, in seconds.
     */
    private long outlineTempTtlSeconds = 1800;

    /**
     * Whether outline generation can fallback to local templates.
     */
    private boolean fallbackEnabled = true;
}

