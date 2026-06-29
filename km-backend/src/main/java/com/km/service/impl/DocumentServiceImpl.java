package com.km.service.impl;

import com.km.common.constant.DocumentStatus;
import com.km.common.dto.PageResult;
import com.km.common.exception.BusinessException;
import com.km.common.exception.ErrorCode;
import com.km.dto.response.DocumentBatchDeleteResponse;
import com.km.dto.response.DocumentDeleteResponse;
import com.km.dto.response.DocumentUploadResponse;
import com.km.entity.Document;
import com.km.entity.KnowledgeBase;
import com.km.repository.ChunkMapper;
import com.km.repository.DocumentMapper;
import com.km.repository.KnowledgeBaseMapper;
import com.km.service.DocumentConverter;
import com.km.service.DocumentService;
import com.km.storage.FileStorageService;
import com.km.vo.ChunkVO;
import com.km.vo.DocumentVO;
import com.km.common.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024L;

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(
            "pdf", "docx", "pptx", "xlsx", "md", "txt", "jpg", "png");

    private final DocumentMapper documentMapper;
    private final ChunkMapper chunkMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentUploadResponse uploadDocument(String kbId, MultipartFile file, String tagsJson, Long userId) {
        KnowledgeBase kb = knowledgeBaseMapper.getById(kbId);
        if (kb == null) {
            throw new BusinessException(ErrorCode.KM_KB_001);
        }
        validateFile(file);

        String docId = UUID.randomUUID().toString().replace("-", "");
        String objectName = kbId + "/" + docId + "/" + file.getOriginalFilename();
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

        DocumentVO vo = DocumentConverter.toVO(doc);
        KnowledgeBase updatedKb = knowledgeBaseMapper.getById(kbId);
        return new DocumentUploadResponse(vo, updatedKb.getDocCount());
    }

    @Override
    public PageResult<DocumentVO> listDocuments(String kbId, String status, int page, int pageSize) {
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

        fileStorageService.delete(doc.getFilePath());
        chunkMapper.deleteByDocId(docId);
        documentMapper.deleteById(docId);
        knowledgeBaseMapper.decrementDocCount(kbId, 1);

        return new DocumentDeleteResponse(docId, kb.getDocCount() - 1);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentBatchDeleteResponse batchDeleteDocuments(String kbId, List<String> ids) {
        KnowledgeBase kb = knowledgeBaseMapper.getById(kbId);
        if (kb == null) {
            throw new BusinessException(ErrorCode.KM_KB_001);
        }

        for (String id : ids) {
            Document doc = documentMapper.getById(id);
            if (doc != null && doc.getFilePath() != null) {
                fileStorageService.delete(doc.getFilePath());
            }
        }

        chunkMapper.deleteByDocIds(ids);
        documentMapper.deleteByIds(ids);
        knowledgeBaseMapper.decrementDocCount(kbId, ids.size());

        return new DocumentBatchDeleteResponse(ids, Math.max(0, kb.getDocCount() - ids.size()));
    }

    @Override
    public PageResult<ChunkVO> listChunks(String docId, int page, int pageSize) {
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
        documentMapper.updateById(doc);

        return DocumentConverter.toVO(doc);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(String docId, String status, String errorMsg) {
        documentMapper.updateStatus(docId, status, errorMsg);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "File is empty");
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
            throw new BusinessException(ErrorCode.KM_DOC_003);
        }
    }
}
