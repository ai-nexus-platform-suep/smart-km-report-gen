package com.km.pipeline.producer;

import com.km.common.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Produces document processing tasks to RabbitMQ.
 * The Python km-ai-service consumer picks up and processes these tasks.
 */
@Component
public class DocumentTaskProducer {

    private static final Logger log = LoggerFactory.getLogger(DocumentTaskProducer.class);

    private final RabbitTemplate rabbitTemplate;

    @Value("${km.ai-service.callback-url:}")
    private String callbackUrl;

    public DocumentTaskProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Send a document processing task to the queue.
     * Python consumer will: parse -> chunk -> embed -> write to Milvus -> callback.
     */
    public void sendProcessTask(String documentId, String kbId, String minioPath,
                                 String mimeType, Map<String, Object> chunkStrategy) {
        Map<String, Object> message = new HashMap<>();
        message.put("documentId", documentId);
        message.put("kbId", kbId);
        message.put("minioPath", minioPath);
        message.put("mimeType", mimeType);
        message.put("chunkStrategy", chunkStrategy);
        message.put("callbackUrl", callbackUrl);

        rabbitTemplate.convertAndSend("km.document.process", message);
        log.info("Sent process task: documentId={}, kbId={}", documentId, kbId);
    }
}
