package com.km.service.impl;

import com.km.common.constant.DocumentStatus;
import com.km.common.dto.PageResult;
import com.km.common.exception.BusinessException;
import com.km.common.exception.ErrorCode;
import com.km.dto.response.DocumentBatchDeleteResponse;
import com.km.dto.response.DocumentDeleteResponse;
import com.km.dto.response.DocumentUploadResponse;
import com.km.entity.Chunk;
import com.km.entity.Document;
import com.km.entity.KnowledgeBase;
import com.km.repository.ChunkMapper;
import com.km.repository.DocumentMapper;
import com.km.repository.KnowledgeBaseMapper;
import com.km.service.DocumentProcessQueuePublisher;
import com.km.storage.FileStorageService;
import com.km.vo.ChunkVO;
import com.km.vo.DocumentVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DocumentServiceImpl 单元测试。
 * 覆盖：上传、列表查询、查询、删除、批量删除、切片列表、下载、重试、标签更新、状态更新、文件校验。
 */
@ExtendWith(MockitoExtension.class)
class DocumentServiceImplTest {

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private ChunkMapper chunkMapper;

    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private ObjectProvider<DocumentProcessQueuePublisher> publisherProvider;

    private DocumentServiceImpl documentService;

    private static final Long TEST_USER_ID = 1L;
    private static final String KB_ID = "kb-test-1";

    @BeforeEach
    void setUp() {
        documentService = new DocumentServiceImpl(documentMapper, chunkMapper,
                knowledgeBaseMapper, fileStorageService, publisherProvider);
    }

    // ====== 上传文档 ======

    @Test
    void shouldUploadDocumentSuccessfully() throws Exception {
        KnowledgeBase kb = createKnowledgeBase(KB_ID, 3);
        when(knowledgeBaseMapper.getById(KB_ID)).thenReturn(kb);

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "content".getBytes());

        DocumentUploadResponse response = documentService.uploadDocument(KB_ID, file, null, TEST_USER_ID);

        assertNotNull(response);
        assertNotNull(response.getDocument());
        assertEquals("test.pdf", response.getDocument().getFilename());
        assertEquals("UPLOADED", response.getDocument().getStatus());
        assertEquals(KB_ID, response.getDocument().getKbId());

        verify(fileStorageService).store(anyString(), any(InputStream.class), anyLong(), anyString());
        verify(documentMapper).insert(any(Document.class));
        verify(knowledgeBaseMapper).incrementDocCount(KB_ID);
    }

    @Test
    void shouldThrowExceptionWhenUploadToNonExistentKb() throws Exception {
        when(knowledgeBaseMapper.getById("nonexistent")).thenReturn(null);

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "content".getBytes());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentService.uploadDocument("nonexistent", file, null, TEST_USER_ID));
        assertEquals(ErrorCode.KM_KB_001.getCode(), ex.getCode());
        verify(fileStorageService, never()).store(anyString(), any(InputStream.class), anyLong(), anyString());
    }

    @Test
    void shouldRejectEmptyFile() {
        KnowledgeBase kb = createKnowledgeBase(KB_ID, 0);
        when(knowledgeBaseMapper.getById(KB_ID)).thenReturn(kb);

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentService.uploadDocument(KB_ID, file, null, TEST_USER_ID));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    void shouldRejectOversizedFile() {
        KnowledgeBase kb = createKnowledgeBase(KB_ID, 0);
        when(knowledgeBaseMapper.getById(KB_ID)).thenReturn(kb);

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(55L * 1024 * 1024); // 55MB > 50MB

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentService.uploadDocument(KB_ID, file, null, TEST_USER_ID));
        assertEquals(ErrorCode.KM_DOC_004.getCode(), ex.getCode());
    }

    @Test
    void shouldRejectUnsupportedExtension() {
        KnowledgeBase kb = createKnowledgeBase(KB_ID, 0);
        when(knowledgeBaseMapper.getById(KB_ID)).thenReturn(kb);

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1024L);
        when(file.getOriginalFilename()).thenReturn("test.exe");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentService.uploadDocument(KB_ID, file, null, TEST_USER_ID));
        assertEquals(ErrorCode.KM_DOC_003.getCode(), ex.getCode());
    }

    @Test
    void shouldRejectFileWithoutExtension() {
        KnowledgeBase kb = createKnowledgeBase(KB_ID, 0);
        when(knowledgeBaseMapper.getById(KB_ID)).thenReturn(kb);

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1024L);
        when(file.getOriginalFilename()).thenReturn("noext");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentService.uploadDocument(KB_ID, file, null, TEST_USER_ID));
        assertEquals(ErrorCode.KM_DOC_003.getCode(), ex.getCode());
    }

    @Test
    void shouldFailUploadWhenStorageThrows() throws Exception {
        KnowledgeBase kb = createKnowledgeBase(KB_ID, 3);
        when(knowledgeBaseMapper.getById(KB_ID)).thenReturn(kb);

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "content".getBytes());

        doThrow(new RuntimeException("Storage unavailable"))
                .when(fileStorageService).store(anyString(), any(InputStream.class), anyLong(), anyString());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentService.uploadDocument(KB_ID, file, null, TEST_USER_ID));
        assertEquals(ErrorCode.INTERNAL_ERROR.getCode(), ex.getCode());
        verify(documentMapper, never()).insert(any(Document.class));
        verify(knowledgeBaseMapper, never()).incrementDocCount(anyString());
    }

    // ====== 文档列表 ======

    @Test
    void shouldListDocuments() {
        KnowledgeBase kb = createKnowledgeBase(KB_ID, 2);
        when(knowledgeBaseMapper.getById(KB_ID)).thenReturn(kb);

        Document doc1 = createDocument("doc-1", KB_ID, "file1.pdf", "UPLOADED");
        Document doc2 = createDocument("doc-2", KB_ID, "file2.docx", "READY");
        when(documentMapper.listByKbId(KB_ID, null, 0, 10))
                .thenReturn(Arrays.asList(doc1, doc2));
        when(documentMapper.countByKbId(KB_ID, null)).thenReturn(2L);

        PageResult<DocumentVO> result = documentService.listDocuments(KB_ID, null, 1, 10);

        assertNotNull(result);
        assertEquals(2, result.getTotal());
        assertEquals(2, result.getList().size());
        assertEquals("file1.pdf", result.getList().get(0).getFilename());
        assertEquals("UPLOADED", result.getList().get(0).getStatus());
    }

    @Test
    void shouldThrowExceptionWhenListNonExistentKb() {
        when(knowledgeBaseMapper.getById("nonexistent")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentService.listDocuments("nonexistent", null, 1, 10));
        assertEquals(ErrorCode.KM_KB_001.getCode(), ex.getCode());
    }

    // ====== 查询文档 ======

    @Test
    void shouldGetDocument() {
        Document doc = createDocument("doc-1", KB_ID, "report.pdf", "READY");
        when(documentMapper.getById("doc-1")).thenReturn(doc);

        DocumentVO vo = documentService.getDocument("doc-1");

        assertNotNull(vo);
        assertEquals("doc-1", vo.getId());
        assertEquals("report.pdf", vo.getFilename());
        assertEquals("READY", vo.getStatus());
    }

    @Test
    void shouldThrowExceptionWhenGetNonExistentDoc() {
        when(documentMapper.getById("nonexistent")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentService.getDocument("nonexistent"));
        assertEquals(ErrorCode.KM_DOC_001.getCode(), ex.getCode());
    }

    // ====== 删除文档 ======

    @Test
    void shouldDeleteDocument() {
        KnowledgeBase kb = createKnowledgeBase(KB_ID, 5);
        Document doc = createDocument("doc-1", KB_ID, "file.pdf", "UPLOADED");
        when(knowledgeBaseMapper.getById(KB_ID)).thenReturn(kb);
        when(documentMapper.getById("doc-1")).thenReturn(doc);

        DocumentDeleteResponse response = documentService.deleteDocument(KB_ID, "doc-1");

        assertNotNull(response);
        assertEquals("doc-1", response.getDeletedDocumentId());
        verify(fileStorageService).delete(doc.getFilePath());
        verify(chunkMapper).deleteByDocId("doc-1");
        verify(documentMapper).deleteById("doc-1");
        verify(knowledgeBaseMapper).decrementDocCount(KB_ID, 1);
    }

    @Test
    void shouldThrowExceptionWhenDeleteNonExistentDoc() {
        KnowledgeBase kb = createKnowledgeBase(KB_ID, 2);
        when(knowledgeBaseMapper.getById(KB_ID)).thenReturn(kb);
        when(documentMapper.getById("nonexistent")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentService.deleteDocument(KB_ID, "nonexistent"));
        assertEquals(ErrorCode.KM_DOC_001.getCode(), ex.getCode());
        verify(fileStorageService, never()).delete(anyString());
        verify(documentMapper, never()).deleteById(anyString());
    }

    // ====== 批量删除文档 ======

    @Test
    void shouldBatchDeleteDocuments() {
        KnowledgeBase kb = createKnowledgeBase(KB_ID, 10);
        List<String> ids = Arrays.asList("doc-1", "doc-2");
        Document doc1 = createDocument("doc-1", KB_ID, "file1.pdf", "UPLOADED");
        Document doc2 = createDocument("doc-2", KB_ID, "file2.docx", "READY");
        when(knowledgeBaseMapper.getById(KB_ID)).thenReturn(kb);
        when(documentMapper.getById("doc-1")).thenReturn(doc1);
        when(documentMapper.getById("doc-2")).thenReturn(doc2);

        DocumentBatchDeleteResponse response = documentService.batchDeleteDocuments(KB_ID, ids);

        assertNotNull(response);
        assertEquals(ids, response.getDeletedIds());
        verify(fileStorageService).delete(doc1.getFilePath());
        verify(fileStorageService).delete(doc2.getFilePath());
        verify(chunkMapper).deleteByDocIds(ids);
        verify(documentMapper).deleteByIds(ids);
        verify(knowledgeBaseMapper).decrementDocCount(KB_ID, 2);
    }

    // ====== 切片列表 ======

    @Test
    void shouldListChunks() {
        Document doc = createDocument("doc-1", KB_ID, "report.pdf", "READY");
        when(documentMapper.getById("doc-1")).thenReturn(doc);

        Chunk chunk1 = createChunk("chunk-1", "doc-1", "内容段落一", 1);
        Chunk chunk2 = createChunk("chunk-2", "doc-1", "内容段落二", 2);
        when(chunkMapper.listByDocId("doc-1", 0, 20))
                .thenReturn(Arrays.asList(chunk1, chunk2));
        when(chunkMapper.countByDocId("doc-1")).thenReturn(2L);

        PageResult<ChunkVO> result = documentService.listChunks("doc-1", 1, 20);

        assertNotNull(result);
        assertEquals(2, result.getTotal());
        assertEquals(2, result.getList().size());
        assertEquals("chunk-1", result.getList().get(0).getId());
        assertEquals("内容段落一", result.getList().get(0).getContent());
    }

    @Test
    void shouldThrowExceptionWhenListChunksNonExistentDoc() {
        when(documentMapper.getById("nonexistent")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentService.listChunks("nonexistent", 1, 10));
        assertEquals(ErrorCode.KM_DOC_001.getCode(), ex.getCode());
    }

    // ====== 重试处理 ======

    @Test
    void shouldRetryFailedDocument() {
        Document doc = createDocument("doc-1", KB_ID, "report.pdf", DocumentStatus.FAILED);
        doc.setErrorMsg("解析超时");
        when(documentMapper.getById("doc-1")).thenReturn(doc);

        DocumentVO vo = documentService.retryProcess("doc-1");

        assertNotNull(vo);
        assertEquals(DocumentStatus.UPLOADED, vo.getStatus());
        assertNull(vo.getErrorMsg());
        verify(documentMapper).updateStatus("doc-1", DocumentStatus.UPLOADED, null);
    }

    @Test
    void shouldThrowExceptionWhenRetryNonFailedDoc() {
        Document doc = createDocument("doc-1", KB_ID, "report.pdf", DocumentStatus.READY);
        when(documentMapper.getById("doc-1")).thenReturn(doc);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentService.retryProcess("doc-1"));
        assertEquals(ErrorCode.KM_DOC_005.getCode(), ex.getCode());
        verify(documentMapper, never()).updateStatus(anyString(), anyString(), any());
    }

    // ====== 更新标签 ======

    @Test
    void shouldUpdateTags() {
        Document doc = createDocument("doc-1", KB_ID, "report.pdf", "READY");
        when(documentMapper.getById("doc-1")).thenReturn(doc);

        Map<String, String> tags = new HashMap<>();
        tags.put("category", "技术文档");
        tags.put("priority", "高");

        DocumentVO vo = documentService.updateTags("doc-1", tags);

        assertNotNull(vo);
        verify(documentMapper).updateTags(eq("doc-1"), anyString());
    }

    @Test
    void shouldClearTagsWhenEmptyMap() {
        Document doc = createDocument("doc-1", KB_ID, "report.pdf", "READY");
        doc.setTagsJson("{\"old\":\"value\"}");
        when(documentMapper.getById("doc-1")).thenReturn(doc);

        DocumentVO vo = documentService.updateTags("doc-1", new HashMap<String, String>());

        assertNotNull(vo);
        verify(documentMapper).updateTags("doc-1", null);
    }

    // ====== 更新状态 ======

    @Test
    void shouldUpdateStatus() {
        when(documentMapper.updateStatus("doc-1", DocumentStatus.PARSING, null)).thenReturn(1);

        documentService.updateStatus("doc-1", DocumentStatus.PARSING, null);

        verify(documentMapper).updateStatus("doc-1", DocumentStatus.PARSING, null);
    }

    @Test
    void shouldUpdateStatusWithErrorMessage() {
        when(documentMapper.updateStatus("doc-2", DocumentStatus.FAILED, "Process failed: 文件损坏无法解析")).thenReturn(1);

        documentService.updateStatus("doc-2", DocumentStatus.FAILED, "文件损坏无法解析");

        verify(documentMapper).updateStatus("doc-2", DocumentStatus.FAILED, "Process failed: 文件损坏无法解析");
    }

    // ====== 辅助方法 ======

    private KnowledgeBase createKnowledgeBase(String id, int docCount) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(id);
        kb.setName("测试知识库");
        kb.setDocType("通用文档");
        kb.setDocCount(docCount);
        kb.setCreatedAt(LocalDateTime.now().minusDays(1));
        kb.setUpdatedAt(LocalDateTime.now());
        return kb;
    }

    private Document createDocument(String id, String kbId, String filename, String status) {
        Document doc = new Document();
        doc.setId(id);
        doc.setKbId(kbId);
        doc.setFilename(filename);
        doc.setFilePath(kbId + "/" + id + "/" + filename);
        doc.setFileSize(10240L);
        doc.setMimeType("application/pdf");
        doc.setStatus(status);
        doc.setCreatedBy(TEST_USER_ID);
        doc.setCreatedAt(LocalDateTime.now().minusHours(1));
        doc.setUpdatedAt(LocalDateTime.now());
        return doc;
    }

    private Chunk createChunk(String id, String docId, String content, int index) {
        Chunk chunk = new Chunk();
        chunk.setId(id);
        chunk.setDocId(docId);
        chunk.setContent(content);
        chunk.setChapterPath("第1章");
        chunk.setChunkIndex(index);
        chunk.setChunkType("text");
        chunk.setCreatedAt(LocalDateTime.now());
        return chunk;
    }
}
