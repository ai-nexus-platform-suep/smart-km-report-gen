package com.km.service.impl;

import com.km.common.constant.DocumentStatus;
import com.km.common.dto.PageResult;
import com.km.common.exception.BusinessException;
import com.km.common.exception.ErrorCode;
import com.km.common.util.JsonUtils;
import com.km.dto.response.*;
import com.km.entity.Chunk;
import com.km.entity.Document;
import com.km.entity.KnowledgeBase;
import com.km.pipeline.producer.DocumentTaskProducer;
import com.km.repository.ChunkMapper;
import com.km.repository.DocumentMapper;
import com.km.repository.KnowledgeBaseMapper;
import com.km.service.DocumentService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DocumentServiceImpl implements DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentServiceImpl.class);

    private final DocumentMapper docMapper;
    private final ChunkMapper chunkMapper;
    private final KnowledgeBaseMapper kbMapper;
    private final DocumentTaskProducer taskProducer;
    private final MinioClient minioClient;

    @Value("${km.minio.bucket}")
    private String minioBucket;

    public DocumentServiceImpl(DocumentMapper docMapper, ChunkMapper chunkMapper,
                                KnowledgeBaseMapper kbMapper, DocumentTaskProducer taskProducer,
                                MinioClient minioClient) {
        this.docMapper = docMapper;
        this.chunkMapper = chunkMapper;
        this.kbMapper = kbMapper;
        this.taskProducer = taskProducer;
        this.minioClient = minioClient;
    }

    @Override
    @Transactional
    public DocumentUploadResultVO upload(String kbId, MultipartFile file, String tags, Long createdBy) {
        KnowledgeBase kb = kbMapper.getById(kbId);
        if (kb == null) throw new BusinessException(ErrorCode.KM_KB_001);

        String docId = UUID.randomUUID().toString();
        String objectPath = kbId + "/" + docId + "_" + file.getOriginalFilename();

        try {
            // Upload to MinIO
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioBucket)
                    .object(objectPath)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());

            // Save document record
            Document doc = new Document();
            doc.setId(docId);
            doc.setKbId(kbId);
            doc.setFilename(file.getOriginalFilename());
            doc.setFilePath(objectPath);
            doc.setFileSize(file.getSize());
            doc.setMimeType(file.getContentType());
            doc.setStatus(DocumentStatus.UPLOADED);
            doc.setTagsJson(tags);
            doc.setCreatedBy(createdBy);
            doc.setCreatedAt(LocalDateTime.now());
            doc.setUpdatedAt(LocalDateTime.now());
            docMapper.insert(doc);

            // Update KB doc count
            kbMapper.updateDocCount(kbId, 1);

            // Send to processing queue
            @SuppressWarnings("unchecked")
            Map<String, Object> chunkStrategy = new java.util.HashMap<>();
            try {
                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                chunkStrategy = om.readValue(kb.getChunkStrategyJson(), java.util.Map.class);
            } catch (Exception e) {
                // fallback to empty
            }
            taskProducer.sendProcessTask(docId, kbId, objectPath, file.getContentType(), chunkStrategy);

            log.info("Document uploaded: docId={}, kbId={}, size={}", docId, kbId, file.getSize());

            DocumentUploadResultVO result = new DocumentUploadResultVO();
            result.setDocument(toVO(doc));
            result.setKbDocCount(kb.getDocCount() + 1);
            return result;

        } catch (Exception e) {
            log.error("Upload failed", e);
            throw new RuntimeException("File upload failed", e);
        }
    }

    @Override
    public DocumentVO getById(String id) {
        Document doc = docMapper.getById(id);
        if (doc == null) throw new BusinessException(ErrorCode.KM_DOC_001);
        return toVO(doc);
    }

    @Override
    public PageResult<DocumentVO> listByKbId(String kbId, String status, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<Document> list = docMapper.listByKbId(kbId, status, offset, pageSize);
        long total = docMapper.countByKbId(kbId, status);
        List<DocumentVO> voList = list.stream().map(this::toVO).collect(Collectors.toList());
        return new PageResult<>(voList, total, page, pageSize);
    }

    @Override
    @Transactional
    public DocumentDeleteResultVO delete(String kbId, String docId) {
        Document doc = docMapper.getById(docId);
        if (doc == null) throw new BusinessException(ErrorCode.KM_DOC_001);

        // Delete from MinIO
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioBucket)
                    .object(doc.getFilePath())
                    .build());
        } catch (Exception e) {
            log.warn("Failed to delete MinIO object: {}", doc.getFilePath());
        }

        docMapper.deleteById(docId);
        kbMapper.updateDocCount(kbId, -1);

        DocumentDeleteResultVO result = new DocumentDeleteResultVO();
        result.setDeletedDocumentId(docId);
        KnowledgeBase kb = kbMapper.getById(kbId);
        result.setKbDocCount(kb != null ? kb.getDocCount() : 0);
        return result;
    }

    @Override
    @Transactional
    public DocumentBatchDeleteResultVO batchDelete(String kbId, List<String> ids) {
        docMapper.batchDeleteByIds(kbId, ids);
        kbMapper.updateDocCount(kbId, -ids.size());
        DocumentBatchDeleteResultVO result = new DocumentBatchDeleteResultVO();
        result.setDeletedIds(ids);
        KnowledgeBase kb = kbMapper.getById(kbId);
        result.setKbDocCount(kb != null ? kb.getDocCount() : 0);
        return result;
    }

    @Override
    @Transactional
    public DocumentVO updateTags(String id, Map<String, String> tags) {
        Document doc = docMapper.getById(id);
        if (doc == null) throw new BusinessException(ErrorCode.KM_DOC_001);
        doc.setTagsJson(JsonUtils.toJson(tags));
        doc.setUpdatedAt(LocalDateTime.now());
        docMapper.updateStatus(id, doc.getStatus(), doc.getErrorMsg());
        // Note: for tags only we need a dedicated update method
        return toVO(doc);
    }

    @Override
    @Transactional
    public DocumentVO retryProcess(String id) {
        Document doc = docMapper.getById(id);
        if (doc == null) throw new BusinessException(ErrorCode.KM_DOC_001);
        if (!DocumentStatus.FAILED.equals(doc.getStatus())) {
            throw new BusinessException(ErrorCode.KM_DOC_002, "Document is not in FAILED status");
        }
        doc.setStatus(DocumentStatus.UPLOADED);
        doc.setErrorMsg(null);
        doc.setUpdatedAt(LocalDateTime.now());
        docMapper.updateStatus(id, DocumentStatus.UPLOADED, null);
        KnowledgeBase kb = kbMapper.getById(doc.getKbId());
        if (kb != null) {
            Map<String, Object> chunkStrategy = new HashMap<>();
            try {
                chunkStrategy = JsonUtils.toJson(kb.getChunkStrategyJson());
            } catch (Exception e) { /* ignore */ }
            taskProducer.sendProcessTask(id, doc.getKbId(), doc.getFilePath(), doc.getMimeType(), chunkStrategy);
        }
        return toVO(doc);
    }

    @Override
    public PageResult<?> listChunks(String docId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<Chunk> chunks = chunkMapper.listByDocId(docId, offset, pageSize);
        long total = chunkMapper.countByDocId(docId);
        List<ChunkVO> voList = chunks.stream().map(this::toChunkVO).collect(Collectors.toList());
        return new PageResult<>(voList, total, page, pageSize);
    }

    private DocumentVO toVO(Document doc) {
        DocumentVO vo = new DocumentVO();
        vo.setId(doc.getId());
        vo.setKbId(doc.getKbId());
        vo.setFilename(doc.getFilename());
        vo.setFileSize(doc.getFileSize());
        vo.setMimeType(doc.getMimeType());
        vo.setStatus(doc.getStatus());
        vo.setErrorMsg(doc.getErrorMsg());
        vo.setCreatedBy(doc.getCreatedBy());
        vo.setCreatedAt(doc.getCreatedAt());
        vo.setUpdatedAt(doc.getUpdatedAt());
        return vo;
    }

    private ChunkVO toChunkVO(Chunk chunk) {
        ChunkVO vo = new ChunkVO();
        vo.setId(chunk.getId());
        vo.setDocId(chunk.getDocId());
        vo.setContent(chunk.getContent());
        vo.setChapterPath(chunk.getChapterPath());
        vo.setChunkIndex(chunk.getChunkIndex());
        vo.setChunkType(chunk.getChunkType());
        vo.setCharCount(chunk.getCharCount());
        return vo;
    }
}
