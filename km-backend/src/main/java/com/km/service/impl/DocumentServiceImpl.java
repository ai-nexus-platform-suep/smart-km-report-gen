package com.km.service.impl;

import com.km.common.constant.DocumentStatus;
import com.km.common.dto.PageResult;
import com.km.common.exception.BusinessException;
import com.km.common.exception.ErrorCode;
import com.km.dto.request.ReplaceDocumentChunksRequest;
import com.km.dto.response.DocumentBatchDeleteResponse;
import com.km.dto.response.DocumentDeleteResponse;
import com.km.dto.response.DocumentUploadResponse;
import com.km.dto.response.ReplaceDocumentChunksResponse;
import com.km.entity.Chunk;
import com.km.entity.Document;
import com.km.entity.KnowledgeBase;
import com.km.repository.ChunkMapper;
import com.km.repository.DocumentMapper;
import com.km.repository.KnowledgeBaseMapper;
import com.km.service.DocumentConverter;
import com.km.service.DocumentProcessQueuePublisher;
import com.km.service.DocumentService;
import com.km.storage.FileStorageService;
import com.km.vo.ChunkVO;
import com.km.vo.DocumentVO;
import com.km.common.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024L;

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(
            "pdf", "docx", "pptx", "xlsx", "md", "txt", "jpg", "png");

    private static final List<String> ALLOWED_MIME_TYPES = Arrays.asList(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/markdown",
            "text/plain",
            "image/jpeg",
            "image/png");

    private static final Pattern UNSAFE_OBJECT_KEY_CHARS = Pattern.compile("[^\\p{L}\\p{N}._-]+");

    private final DocumentMapper documentMapper;
    private final ChunkMapper chunkMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final FileStorageService fileStorageService;
    private final ObjectProvider<DocumentProcessQueuePublisher> documentProcessQueuePublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentUploadResponse uploadDocument(String kbId, MultipartFile file, String tagsJson, Long userId) {
        KnowledgeBase kb = knowledgeBaseMapper.getById(kbId);
        if (kb == null) {
            throw new BusinessException(ErrorCode.KM_KB_001);
        }
        validateFile(file);

        String docId = UUID.randomUUID().toString().replace("-", "");
        String objectName = buildRawObjectName(kbId, kb.getName(), docId, file.getOriginalFilename());
        try {
            fileStorageService.store(objectName, file.getInputStream(), file.getSize(), file.getContentType());
        } catch (Exception e) {
            log.error("File storage upload failed, kbId={}, filename={}", kbId, file.getOriginalFilename(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "File upload to storage failed");
        }

        Document doc = new Document();
        doc.setId(docId);
        doc.setKbId(kbId);
        doc.setFilename(file.getOriginalFilename());
        doc.setFilePath(objectName);
        doc.setFileSize(file.getSize());
        doc.setMimeType(file.getContentType());
        doc.setStatus(DocumentStatus.UPLOADED);
        doc.setTagsJson(tagsJson);
        doc.setCreatedBy(userId);
        documentMapper.insert(doc);

        knowledgeBaseMapper.incrementDocCount(kbId);
        publishProcessJobAfterCommit(doc, kb);

        DocumentVO vo = DocumentConverter.toVO(doc);
        KnowledgeBase updatedKb = knowledgeBaseMapper.getById(kbId);
        return new DocumentUploadResponse(vo, updatedKb.getDocCount());
    }

    @Override
    public PageResult<DocumentVO> listDocuments(String kbId, String status, int page, int pageSize) {
        validatePage(page, pageSize);
        validateStatusFilter(status);
        KnowledgeBase kb = knowledgeBaseMapper.getById(kbId);
        if (kb == null) {
            throw new BusinessException(ErrorCode.KM_KB_001);
        }

        int offset = (page - 1) * pageSize;
        List<Document> documents = documentMapper.listByKbId(kbId, status, offset, pageSize);
        long total = documentMapper.countByKbId(kbId, status);

        List<DocumentVO> list = documents.stream()
                .map(DocumentConverter::toVO)
                .collect(Collectors.toList());

        return new PageResult<>(list, total, page, pageSize);
    }

    @Override
    public DocumentVO getDocument(String docId) {
        Document doc = documentMapper.getById(docId);
        if (doc == null) {
            throw new BusinessException(ErrorCode.KM_DOC_001);
        }
        return DocumentConverter.toVO(doc);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentDeleteResponse deleteDocument(String kbId, String docId) {
        KnowledgeBase kb = knowledgeBaseMapper.getById(kbId);
        if (kb == null) {
            throw new BusinessException(ErrorCode.KM_KB_001);
        }

        Document doc = documentMapper.getById(docId);
        if (doc == null) {
            throw new BusinessException(ErrorCode.KM_DOC_001);
        }
        if (!kbId.equals(doc.getKbId())) {
            throw new BusinessException(ErrorCode.KM_DOC_001);
        }

        fileStorageService.delete(doc.getFilePath());
        chunkMapper.deleteByDocId(docId);
        documentMapper.deleteById(docId);
        knowledgeBaseMapper.decrementDocCount(kbId, 1);

        return new DocumentDeleteResponse(docId, Math.max(0, kb.getDocCount() - 1));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentBatchDeleteResponse batchDeleteDocuments(String kbId, List<String> ids) {
        List<String> uniqueIds = normalizeIds(ids);
        KnowledgeBase kb = knowledgeBaseMapper.getById(kbId);
        if (kb == null) {
            throw new BusinessException(ErrorCode.KM_KB_001);
        }

        List<Document> documents = new ArrayList<>(uniqueIds.size());
        for (String id : uniqueIds) {
            Document doc = documentMapper.getById(id);
            if (doc == null || !kbId.equals(doc.getKbId())) {
                throw new BusinessException(ErrorCode.KM_DOC_001);
            }
            documents.add(doc);
        }

        for (Document doc : documents) {
            if (doc.getFilePath() != null) {
                fileStorageService.delete(doc.getFilePath());
            }
        }

        chunkMapper.deleteByDocIds(uniqueIds);
        documentMapper.deleteByIds(uniqueIds);
        knowledgeBaseMapper.decrementDocCount(kbId, uniqueIds.size());

        return new DocumentBatchDeleteResponse(uniqueIds, Math.max(0, kb.getDocCount() - uniqueIds.size()));
    }

    @Override
    public PageResult<ChunkVO> listChunks(String docId, int page, int pageSize) {
        validatePage(page, pageSize);
        Document doc = documentMapper.getById(docId);
        if (doc == null) {
            throw new BusinessException(ErrorCode.KM_DOC_001);
        }

        int offset = (page - 1) * pageSize;
        List<ChunkVO> list = chunkMapper.listByDocId(docId, offset, pageSize)
                .stream()
                .map(DocumentConverter::toChunkVO)
                .collect(Collectors.toList());
        long total = chunkMapper.countByDocId(docId);

        return new PageResult<>(list, total, page, pageSize);
    }

    @Override
    public void downloadDocument(String docId, HttpServletResponse response) {
        Document doc = documentMapper.getById(docId);
        if (doc == null) {
            throw new BusinessException(ErrorCode.KM_DOC_001);
        }

        try (InputStream is = fileStorageService.retrieve(doc.getFilePath())) {
            response.setContentType(doc.getMimeType() != null ? doc.getMimeType() : "application/octet-stream");
            response.setHeader("Content-Disposition",
                    "attachment;filename=" + URLEncoder.encode(doc.getFilename(), "UTF-8"));
            response.setContentLengthLong(doc.getFileSize() != null ? doc.getFileSize() : 0);

            OutputStream os = response.getOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            os.flush();
        } catch (Exception e) {
            log.error("Document download failed, docId={}", docId, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "File download failed");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentVO retryProcess(String docId) {
        Document doc = documentMapper.getById(docId);
        if (doc == null) {
            throw new BusinessException(ErrorCode.KM_DOC_001);
        }
        if (!DocumentStatus.FAILED.equals(doc.getStatus())) {
            throw new BusinessException(ErrorCode.KM_DOC_005);
        }

        documentMapper.updateStatus(docId, DocumentStatus.UPLOADED, null);
        KnowledgeBase kb = knowledgeBaseMapper.getById(doc.getKbId());
        if (kb != null) {
            publishProcessJobAfterCommit(doc, kb);
        }

        doc.setStatus(DocumentStatus.UPLOADED);
        doc.setErrorMsg(null);
        return DocumentConverter.toVO(doc);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentVO updateTags(String docId, Map<String, String> tags) {
        Document doc = documentMapper.getById(docId);
        if (doc == null) {
            throw new BusinessException(ErrorCode.KM_DOC_001);
        }

        String tagsJson = null;
        if (tags != null && !tags.isEmpty()) {
            try {
                tagsJson = JsonUtils.getMapper().writeValueAsString(tags);
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Tags serialization failed");
            }
        }

        doc.setTagsJson(tagsJson);
        documentMapper.updateTags(docId, tagsJson);

        return DocumentConverter.toVO(doc);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(String docId, String status, String errorMsg) {
        if (!ALLOWED_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported document status: " + status);
        }
        // 03.6: FAILED 时规范化错误信息
        String formattedMsg = errorMsg;
        if (DocumentStatus.FAILED.equals(status) && errorMsg != null && !errorMsg.startsWith("Process failed: ")) {
            formattedMsg = "Process failed: " + errorMsg;
        }
        if (formattedMsg != null && formattedMsg.length() > 1000) {
            formattedMsg = formattedMsg.substring(0, 1000);
        }
        int updated = documentMapper.updateStatus(docId, status, formattedMsg);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.KM_DOC_001);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReplaceDocumentChunksResponse replaceChunks(String docId, ReplaceDocumentChunksRequest request) {
        Document doc = documentMapper.getById(docId);
        if (doc == null) {
            throw new BusinessException(ErrorCode.KM_DOC_001);
        }
        if (request.getKbId() != null && !request.getKbId().trim().isEmpty() && !request.getKbId().equals(doc.getKbId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "documentId does not belong to kbId");
        }

        List<ReplaceDocumentChunksRequest.ChunkItem> items = request.getChunks();
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "chunks cannot be empty");
        }

        List<Chunk> chunks = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            ReplaceDocumentChunksRequest.ChunkItem item = items.get(i);
            validateReplaceChunkItem(item, i);
            Chunk chunk = new Chunk();
            chunk.setId(item.getId().trim());
            chunk.setDocId(docId);
            chunk.setContent(item.getContent().trim());
            chunk.setChapterPath(item.getChapterPath() == null ? null : item.getChapterPath().trim());
            chunk.setChunkIndex(item.getChunkIndex());
            chunk.setChunkType(item.getChunkType().trim());
            chunk.setVectorId(item.getVectorId().trim());
            chunks.add(chunk);
        }

        chunkMapper.deleteByDocId(docId);
        chunkMapper.insertBatch(chunks);
        return new ReplaceDocumentChunksResponse(docId, chunks.size());
    }

    private static final List<String> ALLOWED_STATUSES = Arrays.asList(
            DocumentStatus.UPLOADED,
            DocumentStatus.PARSING,
            DocumentStatus.CHUNKING,
            DocumentStatus.EMBEDDING,
            DocumentStatus.READY,
            DocumentStatus.FAILED);

    private void validateReplaceChunkItem(ReplaceDocumentChunksRequest.ChunkItem item, int expectedIndex) {
        if (item == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "chunk cannot be null");
        }
        if (item.getChunkIndex() == null || item.getChunkIndex() != expectedIndex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "chunkIndex must be zero-based and contiguous");
        }
        if (item.getId() == null || item.getId().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "chunk id cannot be blank");
        }
        if (item.getContent() == null || item.getContent().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "chunk content cannot be blank");
        }
        if (item.getChunkType() == null || item.getChunkType().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "chunkType cannot be blank");
        }
        if (item.getVectorId() == null || item.getVectorId().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "vectorId cannot be blank");
        }
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "page must be greater than or equal to 1");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "pageSize must be between 1 and 100");
        }
    }

    private void validateStatusFilter(String status) {
        if (status != null && !status.trim().isEmpty() && !ALLOWED_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported document status: " + status);
        }
    }

    private List<String> normalizeIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "ids cannot be empty");
        }
        return ids.stream().distinct().collect(Collectors.toList());
    }

    private void publishProcessJobAfterCommit(Document doc, KnowledgeBase kb) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishProcessJob(doc, kb);
                }
            });
            return;
        }
        publishProcessJob(doc, kb);
    }

    private void publishProcessJob(Document doc, KnowledgeBase kb) {
        DocumentProcessQueuePublisher publisher = documentProcessQueuePublisher.getIfAvailable();
        if (publisher == null) {
            log.info("Document processing queue publisher unavailable, docId={}", doc.getId());
            return;
        }
        try {
            publisher.publish(doc, kb);
        } catch (Exception e) {
            log.warn("Document processing job publish failed, docId={}", doc.getId(), e);
        }
    }

    private String buildRawObjectName(String kbId, String kbName, String docId, String originalFilename) {
        String kbSegment = safeObjectKeySegment(kbName, "kb") + "--" + shortIdentifier(kbId);
        String safeFilename = safeFilename(originalFilename);
        String filenameStem = safeFilename;
        String extension = "";
        int dotIndex = safeFilename.lastIndexOf('.');
        if (dotIndex > 0) {
            filenameStem = safeFilename.substring(0, dotIndex);
            extension = safeFilename.substring(dotIndex).toLowerCase();
        }
        return "raw/kb-" + kbSegment + "/" + filenameStem + "--doc-" + docId + extension;
    }

    private String safeFilename(String originalFilename) {
        String filename = originalFilename == null ? "document" : originalFilename;
        filename = filename.replace('\\', '/');
        int slashIndex = filename.lastIndexOf('/');
        if (slashIndex >= 0) {
            filename = filename.substring(slashIndex + 1);
        }
        String safe = safeObjectKeySegment(filename, "document");
        return safe.length() > 120 ? safe.substring(0, 120) : safe;
    }

    private String safeObjectKeySegment(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        String safe = UNSAFE_OBJECT_KEY_CHARS.matcher(value.trim()).replaceAll("-");
        safe = safe.replaceAll("^[._-]+|[._-]+$", "");
        return safe.isEmpty() ? fallback : safe;
    }

    private String shortIdentifier(String id) {
        String safe = safeObjectKeySegment(id, "unknown");
        return safe.length() > 8 ? safe.substring(0, 8) : safe;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Upload file cannot be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.KM_DOC_004);
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new BusinessException(ErrorCode.KM_DOC_003);
        }

        String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ErrorCode.KM_DOC_003, "File extension not supported: " + extension);
        }

        // 03.7: MIME 类型校验（作为补充，不替代扩展名检查）
        // 当前仅 warn 日志，不拦截上传。原因：浏览器/curl 上传时 MIME 常为 */* 或缺失，
        // 实际校验仍以扩展名白名单为准。如需严格拦截，需补全 MIME 映射并改为 throw。
        String mimeType = file.getContentType();
        if (mimeType != null && !mimeType.isEmpty() && !ALLOWED_MIME_TYPES.contains(mimeType)) {
            log.warn("Unexpected MIME type '{}' for extension '{}', file={}", mimeType, extension, originalFilename);
        }
    }
}
