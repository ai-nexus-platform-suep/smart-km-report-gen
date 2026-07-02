package com.km.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DocumentProcessingQueueProperties 单元测试。
 * 覆盖：默认值、setter/getter。
 */
class DocumentProcessingQueuePropertiesTest {

    @Test
    void shouldHaveDefaultValues() {
        DocumentProcessingQueueProperties props = new DocumentProcessingQueueProperties();

        assertTrue(props.isEnabled());
        assertEquals("km.document.processing", props.getExchange());
        assertEquals("km.document.processing.parse", props.getQueue());
        assertEquals("document.process", props.getRoutingKey());
        assertEquals("km.document.processing.dlx", props.getDeadLetterExchange());
        assertEquals("km.document.processing.dlq", props.getDeadLetterQueue());
        assertEquals("document.process.dead", props.getDeadLetterRoutingKey());
        assertEquals("http://localhost:8091/internal/documents/status", props.getCallbackUrl());
        assertEquals("mineru", props.getParserBackend());
    }

    @Test
    void shouldAllowOverrideAllValues() {
        DocumentProcessingQueueProperties props = new DocumentProcessingQueueProperties();
        props.setEnabled(false);
        props.setExchange("custom.exchange");
        props.setQueue("custom.queue");
        props.setRoutingKey("custom.routing");
        props.setDeadLetterExchange("custom.dlx");
        props.setDeadLetterQueue("custom.dlq");
        props.setDeadLetterRoutingKey("custom.dead");
        props.setCallbackUrl("http://custom:8080/callback");
        props.setParserBackend("tika");

        assertFalse(props.isEnabled());
        assertEquals("custom.exchange", props.getExchange());
        assertEquals("custom.queue", props.getQueue());
        assertEquals("custom.routing", props.getRoutingKey());
        assertEquals("custom.dlx", props.getDeadLetterExchange());
        assertEquals("custom.dlq", props.getDeadLetterQueue());
        assertEquals("custom.dead", props.getDeadLetterRoutingKey());
        assertEquals("http://custom:8080/callback", props.getCallbackUrl());
        assertEquals("tika", props.getParserBackend());
    }
}
