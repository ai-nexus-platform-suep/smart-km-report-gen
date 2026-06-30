package com.km.service;

import com.km.entity.Chunk;
import com.km.entity.Document;
import com.km.vo.ChunkVO;
import com.km.vo.DocumentVO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DocumentConverter 单元测试。
 * 覆盖：Document/Chunk 实体转 VO、tags 解析、空值与异常处理。
 */
class DocumentConverterTest {

    // ====== Document → DocumentVO 测试 ======

    @Test
    void shouldConvertDocumentToVO() {
        LocalDateTime now = LocalDateTime.now();
        Document entity = new Document();
        entity.setId("doc-001");
        entity.setKbId("kb-001");
        entity.setFilename("测试文档.pdf");
        entity.setFileSize(1024L);
        entity.setMimeType("application/pdf");
        entity.setStatus("SUCCESS");
        entity.setErrorMsg(null);
        entity.setTagsJson(null);
        entity.setCreatedBy(1L);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        DocumentVO vo = DocumentConverter.toVO(entity);

        assertNotNull(vo);
        assertEquals("doc-001", vo.getId());
        assertEquals("kb-001", vo.getKbId());
        assertEquals("测试文档.pdf", vo.getFilename());
        assertEquals(1024L, vo.getFileSize());
        assertEquals("application/pdf", vo.getMimeType());
        assertEquals("SUCCESS", vo.getStatus());
        assertNull(vo.getErrorMsg());
        assertEquals(1L, vo.getCreatedBy());
        assertEquals(now, vo.getCreatedAt());
        assertEquals(now, vo.getUpdatedAt());
        assertTrue(vo.getTags().isEmpty());
    }

    @Test
    void shouldConvertDocumentWithTags() {
        Document entity = new Document();
        entity.setId("doc-002");
        entity.setTagsJson("{\"key1\":\"value1\"}");

        DocumentVO vo = DocumentConverter.toVO(entity);

        assertNotNull(vo);
        Map<String, String> tags = vo.getTags();
        assertNotNull(tags);
        assertEquals(1, tags.size());
        assertEquals("value1", tags.get("key1"));
    }

    @Test
    void shouldConvertDocumentWithEmptyTags() {
        Document entity = new Document();
        entity.setId("doc-003");
        entity.setTagsJson("");

        DocumentVO vo = DocumentConverter.toVO(entity);

        assertNotNull(vo);
        assertNotNull(vo.getTags());
        assertTrue(vo.getTags().isEmpty());
    }

    @Test
    void shouldConvertDocumentWithNullTags() {
        Document entity = new Document();
        entity.setId("doc-004");
        entity.setTagsJson(null);

        DocumentVO vo = DocumentConverter.toVO(entity);

        assertNotNull(vo);
        assertNotNull(vo.getTags());
        assertTrue(vo.getTags().isEmpty());
    }

    @Test
    void shouldConvertDocumentWithInvalidTagsJson() {
        Document entity = new Document();
        entity.setId("doc-005");
        entity.setTagsJson("not-json");

        DocumentVO vo = DocumentConverter.toVO(entity);

        assertNotNull(vo);
        assertNotNull(vo.getTags());
        assertTrue(vo.getTags().isEmpty());
    }

    @Test
    void shouldReturnNullForNullDocument() {
        assertNull(DocumentConverter.toVO(null));
    }

    // ====== Chunk → ChunkVO 测试 ======

    @Test
    void shouldConvertChunkToVO() {
        Chunk entity = new Chunk();
        entity.setId("chunk-001");
        entity.setDocId("doc-001");
        entity.setContent("Hello World");
        entity.setChapterPath("第1章 > 第1节");
        entity.setChunkIndex(0);
        entity.setChunkType("TEXT");

        ChunkVO vo = DocumentConverter.toChunkVO(entity);

        assertNotNull(vo);
        assertEquals("chunk-001", vo.getId());
        assertEquals("doc-001", vo.getDocId());
        assertEquals("Hello World", vo.getContent());
        assertEquals("第1章 > 第1节", vo.getChapterPath());
        assertEquals(0, vo.getChunkIndex());
        assertEquals("TEXT", vo.getChunkType());
        assertEquals(11, vo.getCharCount());
    }

    @Test
    void shouldCalculateCharCountFromContent() {
        Chunk entity = new Chunk();
        entity.setContent("Hello World");

        ChunkVO vo = DocumentConverter.toChunkVO(entity);

        assertEquals(11, vo.getCharCount());
    }

    @Test
    void shouldHandleNullContentCharCount() {
        Chunk entity = new Chunk();
        entity.setContent(null);

        ChunkVO vo = DocumentConverter.toChunkVO(entity);

        assertEquals(0, vo.getCharCount());
    }

    @Test
    void shouldConvertChunkWithAllFields() {
        Chunk entity = new Chunk();
        entity.setId("chunk-002");
        entity.setDocId("doc-002");
        entity.setContent("另一段内容");
        entity.setChapterPath("第2章");
        entity.setChunkIndex(5);
        entity.setChunkType("CODE");

        ChunkVO vo = DocumentConverter.toChunkVO(entity);

        assertEquals("chunk-002", vo.getId());
        assertEquals("第2章", vo.getChapterPath());
        assertEquals(5, vo.getChunkIndex());
        assertEquals("CODE", vo.getChunkType());
    }

    @Test
    void shouldReturnNullForNullChunk() {
        assertNull(DocumentConverter.toChunkVO(null));
    }

    // ====== 私有构造函数测试 ======

    @Test
    void shouldHavePrivateConstructor() throws Exception {
        Constructor<DocumentConverter> constructor = DocumentConverter.class.getDeclaredConstructor();
        assertFalse(constructor.isAccessible());
        constructor.setAccessible(true);
        DocumentConverter instance = constructor.newInstance();
        assertNotNull(instance);
    }
}
