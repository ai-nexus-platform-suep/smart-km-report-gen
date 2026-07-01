package com.km.service;

import com.km.config.DocumentProcessingQueueProperties;
import com.km.dto.document.DocumentProcessMessage;
import com.km.entity.Document;
import com.km.entity.KnowledgeBase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "km.document-processing.queue.enabled", havingValue = "true", matchIfMissing = true)
public class DocumentProcessQueuePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final DocumentProcessingQueueProperties properties;

    public void publish(Document document, KnowledgeBase knowledgeBase) {
        DocumentProcessMessage message = new DocumentProcessMessage(
                UUID.randomUUID().toString(),
                document.getId(),
                document.getKbId(),
                knowledgeBase.getName(),
                document.getFilePath(),
                document.getFilename(),
                document.getMimeType(),
                properties.getParserBackend(),
                knowledgeBase.getChunkStrategyJson(),
                1,
                properties.getCallbackUrl(),
                LocalDateTime.now());

        rabbitTemplate.convertAndSend(properties.getExchange(), properties.getRoutingKey(), message, amqpMessage -> {
            amqpMessage.getMessageProperties().setMessageId(message.getJobId());
            amqpMessage.getMessageProperties().setCorrelationId(document.getId());
            return amqpMessage;
        });
        log.info("Document processing job published, docId={}, jobId={}", document.getId(), message.getJobId());
    }
}
