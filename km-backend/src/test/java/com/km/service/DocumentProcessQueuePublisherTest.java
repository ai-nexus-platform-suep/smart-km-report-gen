package com.km.service;

import com.km.config.DocumentProcessingQueueProperties;
import com.km.dto.document.DocumentProcessMessage;
import com.km.entity.Document;
import com.km.entity.KnowledgeBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DocumentProcessQueuePublisher 单元测试。
 * 覆盖：发布消息到 RabbitMQ 队列。
 */
@ExtendWith(MockitoExtension.class)
class DocumentProcessQueuePublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private DocumentProcessingQueueProperties properties;
    private DocumentProcessQueuePublisher publisher;

    private static final String EXCHANGE = "km.document.processing";
    private static final String ROUTING_KEY = "document.process";
    private static final String CALLBACK_URL = "http://localhost:8091/internal/documents/status";
    private static final String PARSER_BACKEND = "mineru";

    @BeforeEach
    void setUp() {
        properties = new DocumentProcessingQueueProperties();
        properties.setExchange(EXCHANGE);
        properties.setRoutingKey(ROUTING_KEY);
        properties.setCallbackUrl(CALLBACK_URL);
        properties.setParserBackend(PARSER_BACKEND);

        publisher = new DocumentProcessQueuePublisher(rabbitTemplate, properties);
    }

    @Test
    void shouldPublishMessageSuccessfully() {
        Document document = createDocument("doc-1", "kb-1", "report.pdf", "raw/kb-1/report.pdf--doc-1.pdf");
        KnowledgeBase kb = createKnowledgeBase("kb-1", "知识库A", "{\"strategy\":\"paragraph\"}");

        publisher.publish(document, kb);

        ArgumentCaptor<DocumentProcessMessage> messageCaptor =
                ArgumentCaptor.forClass(DocumentProcessMessage.class);
        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq(ROUTING_KEY),
                messageCaptor.capture(), any(MessagePostProcessor.class));

        DocumentProcessMessage msg = messageCaptor.getValue();
        assertNotNull(msg);
        assertEquals("doc-1", msg.getDocumentId());
        assertEquals("kb-1", msg.getKbId());
        assertEquals("知识库A", msg.getKbName());
        assertEquals("report.pdf", msg.getFilename());
        assertEquals("raw/kb-1/report.pdf--doc-1.pdf", msg.getRawObject());
        assertEquals("mineru", msg.getParserBackend());
        assertEquals("{\"strategy\":\"paragraph\"}", msg.getChunkStrategy());
        assertEquals(1, msg.getAttempt());
        assertEquals(CALLBACK_URL, msg.getCallbackUrl());
        assertNotNull(msg.getJobId());
        assertNotNull(msg.getCreatedAt());
    }

    @Test
    void shouldSetMessageIdAndCorrelationId() {
        Document document = createDocument("doc-2", "kb-2", "notes.txt", "raw/kb-2/notes.txt--doc-2.txt");
        KnowledgeBase kb = createKnowledgeBase("kb-2", "知识库B", null);

        publisher.publish(document, kb);

        ArgumentCaptor<DocumentProcessMessage> messageCaptor =
                ArgumentCaptor.forClass(DocumentProcessMessage.class);
        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq(ROUTING_KEY),
                messageCaptor.capture(), any(MessagePostProcessor.class));

        // 验证 jobId 是有效的 UUID
        String jobId = messageCaptor.getValue().getJobId();
        assertNotNull(jobId);
        assertFalse(jobId.isEmpty());
        // UUID 格式: 8-4-4-4-12
        assertEquals(36, jobId.length());
    }

    @Test
    void shouldPublishWithCorrectRouting() {
        Document document = createDocument("doc-3", "kb-3", "data.csv", "raw/kb-3/data.csv--doc-3.csv");
        KnowledgeBase kb = createKnowledgeBase("kb-3", "CSV库", "{}");

        publisher.publish(document, kb);

        verify(rabbitTemplate).convertAndSend(
                eq(EXCHANGE),
                eq(ROUTING_KEY),
                any(DocumentProcessMessage.class),
                any(MessagePostProcessor.class));
    }

    // ====== 辅助方法 ======

    private Document createDocument(String id, String kbId, String filename, String filePath) {
        Document doc = new Document();
        doc.setId(id);
        doc.setKbId(kbId);
        doc.setFilename(filename);
        doc.setFilePath(filePath);
        doc.setMimeType("application/pdf");
        doc.setStatus("UPLOADED");
        doc.setCreatedAt(LocalDateTime.now());
        return doc;
    }

    private KnowledgeBase createKnowledgeBase(String id, String name, String chunkStrategyJson) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(id);
        kb.setName(name);
        kb.setChunkStrategyJson(chunkStrategyJson);
        kb.setDocType("通用文档");
        kb.setCreatedAt(LocalDateTime.now());
        return kb;
    }
}
