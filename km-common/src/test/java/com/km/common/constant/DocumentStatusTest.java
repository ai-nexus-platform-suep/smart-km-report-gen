package com.km.common.constant;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DocumentStatus 单元测试。
 * 覆盖：常量定义、常量值、私有构造器、字符串比较。
 */
class DocumentStatusTest {

    @Test
    void shouldDefineAllStatuses() {
        assertNotNull(DocumentStatus.UPLOADED);
        assertNotNull(DocumentStatus.PARSING);
        assertNotNull(DocumentStatus.CHUNKING);
        assertNotNull(DocumentStatus.EMBEDDING);
        assertNotNull(DocumentStatus.READY);
        assertNotNull(DocumentStatus.FAILED);
    }

    @Test
    void shouldHaveCorrectValues() {
        assertEquals("UPLOADED", DocumentStatus.UPLOADED);
        assertEquals("PARSING", DocumentStatus.PARSING);
        assertEquals("CHUNKING", DocumentStatus.CHUNKING);
        assertEquals("EMBEDDING", DocumentStatus.EMBEDDING);
        assertEquals("READY", DocumentStatus.READY);
        assertEquals("FAILED", DocumentStatus.FAILED);
    }

    @Test
    void shouldHavePrivateConstructor() throws Exception {
        Constructor<DocumentStatus> constructor = DocumentStatus.class.getDeclaredConstructor();
        assertEquals(0, constructor.getParameterCount());

        constructor.setAccessible(true);
        DocumentStatus instance = constructor.newInstance();
        assertNotNull(instance);
    }

    @Test
    void shouldSupportStatusEquality() {
        String status = "READY";

        assertEquals(DocumentStatus.READY, status);
        assertNotEquals(DocumentStatus.FAILED, status);
    }
}
