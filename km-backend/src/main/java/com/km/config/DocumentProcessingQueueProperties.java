package com.km.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "km.document-processing.queue")
public class DocumentProcessingQueueProperties {

    private boolean enabled = true;
    private String exchange = "km.document.processing";
    private String queue = "km.document.processing.parse";
    private String routingKey = "document.process";
    private String deadLetterExchange = "km.document.processing.dlx";
    private String deadLetterQueue = "km.document.processing.dlq";
    private String deadLetterRoutingKey = "document.process.dead";
    private String callbackUrl = "http://localhost:8091/internal/documents/status";
    private String parserBackend = "mineru";
}
